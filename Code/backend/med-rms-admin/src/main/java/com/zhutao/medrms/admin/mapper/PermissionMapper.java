package com.zhutao.medrms.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.admin.domain.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    @Select("""
        <script>
        SELECT DISTINCT p.perm_code FROM sys_schema.t_permission p
        JOIN sys_schema.t_role_permission rp ON rp.perm_id = p.id
        JOIN sys_schema.t_role r ON r.id = rp.role_id
        WHERE r.role_code IN
        <foreach collection="roleCodes" item="rc" open="(" separator="," close=")">
            #{rc}
        </foreach>
          AND r.status = 'ACTIVE' AND p.status = 'ACTIVE'
        </script>
        """)
    List<String> selectPermCodesByRoleCodes(@Param("roleCodes") Set<String> roleCodes);
}