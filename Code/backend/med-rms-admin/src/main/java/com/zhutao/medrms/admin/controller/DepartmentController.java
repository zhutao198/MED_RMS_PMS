package com.zhutao.medrms.admin.controller;

import com.zhutao.medrms.admin.domain.entity.Department;
import com.zhutao.medrms.admin.domain.dto.OrgNode;
import com.zhutao.medrms.admin.service.DepartmentService;
import com.zhutao.medrms.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R99 部门管理 Controller
 * <p>6 个端点：树查询 + 子部门列表 + CRUD + 排序
 */
@Tag(name = "组织架构", description = "R99 部门 CRUD + 树形管理")
@RestController
@RequestMapping("/system/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * 获取完整部门树（前端 Organization.vue 树形展示）。
     */
    @Operation(summary = "获取完整部门树")
    @GetMapping("/tree")
    public Result<List<OrgNode>> getTree() {
        List<Department> all = departmentService.listAll();
        // 内存中构建父子关系
        Map<Long, List<Department>> childrenMap = new HashMap<>();
        for (Department d : all) {
            childrenMap.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d);
        }
        // 构造树形 OrgNode（顶层 parentId=0）
        return Result.success(buildOrgNodes(0L, childrenMap, null));
    }

    /**
     * 按 parent_id 查直接子部门（用于 el-tree 懒加载场景）。
     */
    @Operation(summary = "按 parent_id 查直接子部门")
    @GetMapping
    public Result<List<Department>> listByParentId(@RequestParam(required = false) Long parentId) {
        return Result.success(departmentService.listByParentId(parentId));
    }

    @Operation(summary = "创建部门")
    @PostMapping
    public Result<Department> create(@RequestBody Department department) {
        return Result.success(departmentService.createDepartment(department));
    }

    @Operation(summary = "更新部门")
    @PutMapping("/{id}")
    public Result<Department> update(@PathVariable Long id, @RequestBody Department updates) {
        return Result.success(departmentService.updateDepartment(id, updates));
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return Result.success();
    }

    @Operation(summary = "更新排序")
    @PostMapping("/{id}/sort")
    public Result<Void> updateSort(@PathVariable Long id, @RequestParam Integer sortOrder) {
        departmentService.updateSortOrder(id, sortOrder);
        return Result.success();
    }

    // ==================== 私有辅助 ====================

    /**
     * 递归构建 OrgNode 树。
     *
     * @param parentId    当前父节点 ID（0 = 根）
     * @param childrenMap 部门 ID → 子部门列表
     * @param userCountMap 部门 ID → 用户数（可选，未来扩展）
     */
    private List<OrgNode> buildOrgNodes(Long parentId,
                                        Map<Long, List<Department>> childrenMap,
                                        Map<Long, Integer> userCountMap) {
        List<Department> children = childrenMap.getOrDefault(parentId, new ArrayList<>());
        List<OrgNode> result = new ArrayList<>();
        for (Department d : children) {
            OrgNode node = new OrgNode();
            node.setId(d.getId());
            node.setLabel(d.getName());
            node.setUserCount(userCountMap == null ? 0 : userCountMap.getOrDefault(d.getId(), 0));
            node.setChildren(buildOrgNodes(d.getId(), childrenMap, userCountMap));
            result.add(node);
        }
        return result;
    }
}