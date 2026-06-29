package com.zhutao.medrms.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.admin.domain.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("""
        SELECT r.role_code FROM sys_schema.t_role r
        JOIN sys_schema.t_user_role ur ON ur.role_id = r.id
        JOIN sys_schema.t_user u ON u.id = ur.user_id
        WHERE u.id = #{userId} AND u.is_deleted = false AND r.status = 'ACTIVE'
        """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}