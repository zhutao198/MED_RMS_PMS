package com.zhutao.medrms.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.admin.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM sys_schema.t_user WHERE username = #{username} AND is_deleted = false")
    User selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM sys_schema.t_user WHERE email = #{email} AND is_deleted = false")
    User selectByEmail(@Param("email") String email);

    // v1.42 BUG #51 修复：@TableLogic 字段在 updateById 中被自动忽略
    // 必须用显式 SQL 才能真正软删除
    @Update("UPDATE sys_schema.t_user SET is_deleted = true WHERE id = #{id} AND is_deleted = false")
    int softDeleteById(@Param("id") Long id);
}