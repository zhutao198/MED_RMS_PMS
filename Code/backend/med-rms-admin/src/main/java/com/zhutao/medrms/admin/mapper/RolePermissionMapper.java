package com.zhutao.medrms.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.admin.domain.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色权限关联 Mapper（v1.46 P1-后端-2）
 * 配合 SystemController 的 /system/roles/{id}/permissions 管理接口。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    /** 查询角色已授予的权限码列表（仅 ACTIVE 状态权限） */
    @Select("""
        SELECT p.perm_code FROM sys_schema.t_permission p
        JOIN sys_schema.t_role_permission rp ON rp.perm_id = p.id
        WHERE rp.role_id = #{roleId} AND p.status = 'ACTIVE'
        ORDER BY p.id
        """)
    List<String> selectPermCodesByRoleId(@Param("roleId") Long roleId);
}
