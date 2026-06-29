package com.zhutao.medrms.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.admin.domain.entity.Permission;
import com.zhutao.medrms.admin.domain.entity.RolePermission;
import com.zhutao.medrms.admin.mapper.PermissionMapper;
import com.zhutao.medrms.admin.mapper.RoleMapper;
import com.zhutao.medrms.admin.mapper.RolePermissionMapper;
import com.zhutao.medrms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 权限查询服务
 * 依据 Detailed/04-权限设计/权限流程设计.md 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public List<String> getUserRoleCodes(Long userId) {
        return roleMapper.selectRoleCodesByUserId(userId);
    }

    public Set<String> getUserPermCodes(Long userId) {
        List<String> roleCodes = getUserRoleCodes(userId);
        if (roleCodes.isEmpty()) {
            return Set.of();
        }
        if (roleCodes.contains("ADMIN")) {
            return Set.of("*");
        }
        return new HashSet<>(permissionMapper.selectPermCodesByRoleCodes(new HashSet<>(roleCodes)));
    }

    public boolean hasPermission(Long userId, String permCode) {
        if (userId == null || permCode == null) {
            return false;
        }
        Set<String> perms = getUserPermCodes(userId);
        if (perms.contains("*")) {
            return true;
        }
        if (perms.contains(permCode)) {
            return true;
        }
        // 通配符：模块:*
        int idx = permCode.indexOf(':');
        if (idx > 0) {
            String modulePrefix = permCode.substring(0, idx) + ":*";
            if (perms.contains(modulePrefix)) {
                return true;
            }
        }
        // 通配符：模块:资源:*
        int secondIdx = permCode.indexOf(':', idx + 1);
        if (secondIdx > 0) {
            String midPrefix = permCode.substring(0, secondIdx) + ":*";
            if (perms.contains(midPrefix)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAdmin(Long userId) {
        return getUserRoleCodes(userId).contains("ADMIN");
    }

    // ---------- v1.46 P1-后端-2：角色权限矩阵管理 ----------

    /** 全量权限码列表（仅 ACTIVE，按 id 升序），供前端授权界面渲染选项 */
    public List<Permission> listAllPermissions() {
        return permissionMapper.selectList(
            new LambdaQueryWrapper<Permission>()
                .eq(Permission::getStatus, "ACTIVE")
                .orderByAsc(Permission::getId)
        );
    }

    /** 单角色已授予的权限码集合 */
    public List<String> getRolePermCodes(Long roleId) {
        if (roleId == null) {
            throw BusinessException.param("角色 ID 不能为空");
        }
        return rolePermissionMapper.selectPermCodesByRoleId(roleId);
    }

    /**
     * 全量替换角色的授权集合（覆盖更新语义）：
     * 1. 校验 permCodes 全部存在于 t_permission；
     * 2. 删除该角色现有的所有 t_role_permission 行；
     * 3. 按 permCodes 重新插入。
     * 设计原因：前端复选框 UX 一次性提交完整勾选状态，比 grant/revoke 增量更稳。
     */
    @Transactional
    public void replaceRolePermissions(Long roleId, List<String> permCodes) {
        if (roleId == null) {
            throw BusinessException.param("角色 ID 不能为空");
        }
        if (permCodes == null) {
            permCodes = List.of();
        }
        Set<String> codeSet = new HashSet<>(permCodes);

        // 校验：所有 permCode 必须存在
        List<Permission> all = listAllPermissions();
        Map<String, Long> codeToId = all.stream()
            .collect(Collectors.toMap(Permission::getPermCode, Permission::getId));
        for (String code : codeSet) {
            if (!codeToId.containsKey(code)) {
                throw BusinessException.param("未知权限码：" + code);
            }
        }

        // 删除现有授权
        rolePermissionMapper.delete(
            new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId)
        );

        // 插入新授权
        for (String code : codeSet) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermId(codeToId.get(code));
            rolePermissionMapper.insert(rp);
        }
        log.info("角色权限已替换: roleId={}, 授权 {} 项", roleId, codeSet.size());
    }
}
