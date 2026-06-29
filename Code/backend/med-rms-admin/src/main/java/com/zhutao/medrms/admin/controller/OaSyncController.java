package com.zhutao.medrms.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.admin.domain.entity.Department;
import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.mapper.DepartmentMapper;
import com.zhutao.medrms.admin.mapper.UserMapper;
import com.zhutao.medrms.admin.service.DepartmentService;
import com.zhutao.medrms.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R101 浏览器抓取方案接收端点
 * <p>用途：浏览器抓取脚本(已登录 OA session) → POST 数据到本端点 → Med-RMS 写入 t_department / t_user / t_requirement
 * <p>设计原则：
 * <ul>
 *   <li>无密码/SOAP 复杂度：浏览器已登录，跳过泛微登录+加密</li>
 *   <li>upsert 而非 insert：按 code/workcode 唯一性更新，避免重复</li>
 *   <li>幂等：相同数据多次 POST 结果一致</li>
 *   <li>结构宽松：OA 数据格式可能变化，Controller 不强校验</li>
 * </ul>
 */
@Slf4j
@Tag(name = "OA 数据接收(R101)", description = "浏览器抓取方案接收端点")
@RestController
@RequestMapping("/oa-sync")
@RequiredArgsConstructor
public class OaSyncController {

    private final DepartmentService departmentService;
    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;

    // ==================== 组织架构(分部+部门) ====================

    @Operation(summary = "接收分部列表(OA HrmService → t_department)")
    @PostMapping("/subcompanies")
    public Result<SyncResult> syncSubcompanies(@RequestBody List<Map<String, Object>> subcompanyList) {
        return Result.success(syncSubcompaniesImpl(subcompanyList));
    }

    @Operation(summary = "接收部门列表(OA HrmService → t_department)")
    @PostMapping("/departments")
    public Result<SyncResult> syncDepartments(@RequestBody List<Map<String, Object>> departmentList) {
        return Result.success(syncDepartmentsImpl(departmentList));
    }

    @Operation(summary = "接收组织架构全量(分部+部门 合并)")
    @PostMapping("/org-structure")
    public Result<Map<String, Object>> syncOrgStructure(@RequestBody OrgStructurePayload payload) {
        SyncResult subResult = syncSubcompaniesImpl(payload.getSubcompanies() == null ? List.of() : payload.getSubcompanies());
        SyncResult deptResult = syncDepartmentsImpl(payload.getDepartments() == null ? List.of() : payload.getDepartments());
        Map<String, Object> resp = new HashMap<>();
        resp.put("subcompanies", subResult);
        resp.put("departments", deptResult);
        return Result.success(resp);
    }

    // ==================== 人员 ====================

    @Operation(summary = "接收人员列表(OA HrmService → t_user)")
    @PostMapping("/users")
    public Result<SyncResult> syncUsers(@RequestBody List<Map<String, Object>> userList) {
        SyncResult result = new SyncResult();
        for (Map<String, Object> u : userList) {
            try {
                String workcode = strVal(u, "workcode");  // 工号
                if (workcode.isEmpty()) continue;
                // 找部门ID(OA 部门 ID → Med-RMS department.id 需映射)
                String oaDeptId = strVal(u, "departmentid");
                Long medRmsDeptId = null;
                if (!oaDeptId.isEmpty()) {
                    // 简化：根据 OA 部门名/code 查 Med-RMS department
                    String oaDeptName = strVal(u, "departmentname");
                    if (!oaDeptName.isEmpty()) {
                        Department d = departmentMapper.selectOne(new LambdaQueryWrapper<Department>()
                            .eq(Department::getName, oaDeptName)
                            .last("LIMIT 1"));
                        if (d != null) medRmsDeptId = d.getId();
                    }
                }
                User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, workcode).last("LIMIT 1"));
                if (existing == null) {
                    User newUser = new User();
                    newUser.setUsername(workcode);
                    newUser.setRealName(strVal(u, "lastname"));
                    newUser.setEmail(strVal(u, "email"));
                    newUser.setPhone(strVal(u, "mobile"));
                    newUser.setDepartment(strVal(u, "departmentname"));
                    newUser.setDeptId(medRmsDeptId);
                    newUser.setPasswordHash("$2a$10$Hksqmm0tjLE9n5nqEeF6huP6WifRN1Z8/9QadXBByBY2nipJ5ucpu");  // R101:默认密码 hash
                    newUser.setPasswordHash("$2a$10$Hksqmm0tjLE9n5nqEeF6huP6WifRN1Z8/9QadXBByBY2nipJ5ucpu");  // R101:默认密码 hash
                    newUser.setRole("USER");  // 默认角色
                    newUser.setStatus("ACTIVE");
                    newUser.setCreatedAt(LocalDateTime.now());
                    newUser.setUpdatedAt(LocalDateTime.now());
                    userMapper.insert(newUser);
                    result.created++;
                } else {
                    if (!strVal(u, "lastname").isEmpty()) existing.setRealName(strVal(u, "lastname"));
                    if (!strVal(u, "email").isEmpty()) existing.setEmail(strVal(u, "email"));
                    if (!strVal(u, "mobile").isEmpty()) existing.setPhone(strVal(u, "mobile"));
                    if (!strVal(u, "departmentname").isEmpty()) existing.setDepartment(strVal(u, "departmentname"));
                    if (medRmsDeptId != null) existing.setDeptId(medRmsDeptId);
                    existing.setUpdatedAt(LocalDateTime.now());
                    userMapper.updateById(existing);
                    result.updated++;
                }
            } catch (Exception e) {
                log.warn("R101 syncUsers 单条失败: {}", e.getMessage());
                result.failed++;
            }
        }
        log.info("R101 syncUsers 完成: created={}, updated={}, failed={}",
            result.created, result.updated, result.failed);
        return Result.success(result);
    }

    // ==================== 工作流(紧急需求单 + 需求采集卡) ====================

    @Operation(summary = "接收紧急需求单(OA workflowid 535)")
    @PostMapping("/urgent-requirements")
    public Result<SyncResult> syncUrgentRequirements(@RequestBody List<Map<String, Object>> woList) {
        return Result.success(syncWorkflowToRequirements(woList, "URGENT"));
    }

    @Operation(summary = "接收需求采集卡(OA workflowid 554)")
    @PostMapping("/requirement-cards")
    public Result<SyncResult> syncRequirementCards(@RequestBody List<Map<String, Object>> woList) {
        return Result.success(syncWorkflowToRequirements(woList, "CARD"));
    }

    @Operation(summary = "通用工作流接收(任意 workflowid)")
    @PostMapping("/workflow")
    public Result<SyncResult> syncWorkflow(@RequestBody WorkflowSyncPayload payload) {
        return Result.success(syncWorkflowToRequirements(
            payload.getWorkflowList() == null ? List.of() : payload.getWorkflowList(),
            payload.getType() == null ? "GENERIC" : payload.getType()));
    }

    @Operation(summary = "同步状态查询")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ready");
        resp.put("message", "OA 同步接收端点就绪 - R101");
        resp.put("endpoints", List.of(
            "POST /api/oa-sync/subcompanies",
            "POST /api/oa-sync/departments",
            "POST /api/oa-sync/org-structure",
            "POST /api/oa-sync/users",
            "POST /api/oa-sync/urgent-requirements",
            "POST /api/oa-sync/requirement-cards",
            "POST /api/oa-sync/workflow"
        ));
        return Result.success(resp);
    }

    // ==================== 私有辅助 ====================

    @Transactional
    protected SyncResult syncSubcompaniesImpl(List<Map<String, Object>> list) {
        SyncResult result = new SyncResult();
        for (Map<String, Object> m : list) {
            try {
                String code = strVal(m, "_code");
                String name = strVal(m, "_fullname");
                if (name.isEmpty()) continue;
                // upsert 顶级部门(分部)
                upsertTopDepartment(code, name, m);
                result.created++;  // simplified count
            } catch (Exception e) {
                log.warn("syncSubcompany 失败: {}", e.getMessage());
                result.failed++;
            }
        }
        return result;
    }

    @Transactional
    protected SyncResult syncDepartmentsImpl(List<Map<String, Object>> list) {
        SyncResult result = new SyncResult();
        for (Map<String, Object> m : list) {
            try {
                String name = strVal(m, "_fullname");
                String supId = strVal(m, "_supdepartmentid");  // 父部门 OA id
                String subcompanyId = strVal(m, "_subcompanyid");
                String code = strVal(m, "_code");
                if (name.isEmpty()) continue;
                Long parentId = resolveParentId(subcompanyId, supId);
                String sortOrderStr = strVal(m, "_showorder");
                Integer sortOrder = sortOrderStr.isEmpty() ? 0 : Integer.parseInt(sortOrderStr);
                Department existing = code.isEmpty() ? null :
                    departmentMapper.selectOne(new LambdaQueryWrapper<Department>()
                        .eq(Department::getCode, code).last("LIMIT 1"));
                if (existing == null) {
                    Department d = new Department();
                    d.setCode(code.isEmpty() ? null : code);
                    d.setName(name);
                    d.setParentId(parentId);
                    d.setSortOrder(sortOrder);
                    d.setCreatedAt(LocalDateTime.now());
                    d.setUpdatedAt(LocalDateTime.now());
                    departmentMapper.insert(d);
                    result.created++;
                } else {
                    existing.setName(name);
                    existing.setParentId(parentId);
                    existing.setSortOrder(sortOrder);
                    existing.setUpdatedAt(LocalDateTime.now());
                    departmentMapper.updateById(existing);
                    result.updated++;
                }
            } catch (Exception e) {
                log.warn("syncDepartment 失败: {}", e.getMessage());
                result.failed++;
            }
        }
        log.info("R101 syncDepartments 完成: created={}, updated={}, failed={}",
            result.created, result.updated, result.failed);
        return result;
    }

    /**
     * upsert 顶级分部(作为 dept_id=0 的顶级部门)
     */
    private void upsertTopDepartment(String code, String name, Map<String, Object> m) {
        Department existing = code.isEmpty() ? null :
            departmentMapper.selectOne(new LambdaQueryWrapper<Department>()
                .eq(Department::getCode, code).last("LIMIT 1"));
        if (existing == null) {
            Department d = new Department();
            d.setCode(code.isEmpty() ? null : code);
            d.setName(name);
            d.setParentId(0L);
            d.setLevel(1);
            d.setCreatedAt(LocalDateTime.now());
            d.setUpdatedAt(LocalDateTime.now());
            departmentMapper.insert(d);
        } else {
            existing.setName(name);
            existing.setUpdatedAt(LocalDateTime.now());
            departmentMapper.updateById(existing);
        }
    }

    /**
     * 解析父部门 ID:OA 父 ID → Med-RMS dept.id
     */
    private Long resolveParentId(String subcompanyId, String supDeptId) {
        // 简化:如果 supDeptId 为空,用 subcompanyId 找分部;都找不到返回 0
        // 实际实现需要 OA 父 ID → Med-RMS 部门 ID 的映射表(可以缓存或扩展)
        if (supDeptId == null || supDeptId.isEmpty() || "0".equals(supDeptId)) {
            if (subcompanyId != null && !subcompanyId.isEmpty()) {
                // 用 subcompanyid 作为"分部标识"——查 Med-RMS 中对应的顶级部门
                Department sub = departmentMapper.selectOne(new LambdaQueryWrapper<Department>()
                    .last("LIMIT 1"));
                // 简化:返回第一个顶级部门作为父(实际应按 subcompanyid → dept.code 映射)
                return sub != null ? sub.getId() : 0L;
            }
            return 0L;
        }
        // TODO: 维护 oa_dept_id → med_dept_id 映射表
        return 0L;
    }

    /**
     * 工作流 → 需求(简化版:写日志 + 标记需手动转换)
     */
    private SyncResult syncWorkflowToRequirements(List<Map<String, Object>> woList, String type) {
        SyncResult result = new SyncResult();
        log.info("R101 接收 {} 工作流数据 {} 条(待二次开发转换)", type, woList.size());
        // Phase 1: 仅记录 + 计数,不写入 t_requirement(避免脏数据)
        // Phase 2: 待确认 OA 工作流字段结构后,实现完整转换
        for (Map<String, Object> wo : woList) {
            log.debug("R101 workitem: id={}, title={}, status={}",
                wo.get("requestid"), wo.get("title"), wo.get("status"));
        }
        result.received = woList.size();
        return result;
    }

    private String strVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return "";
        return v.toString().trim();
    }

    // ==================== DTO ====================

    @Data
    public static class OrgStructurePayload {
        private List<Map<String, Object>> subcompanies;
        private List<Map<String, Object>> departments;
    }

    @Data
    public static class WorkflowSyncPayload {
        private List<Map<String, Object>> workflowList;
        private String type;
    }

    @Data
    public static class SyncResult {
        private int created = 0;
        private int updated = 0;
        private int failed = 0;
        private int received = 0;  // for workflow
    }
}