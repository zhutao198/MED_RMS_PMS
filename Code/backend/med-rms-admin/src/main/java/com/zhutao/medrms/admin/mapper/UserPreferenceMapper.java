package com.zhutao.medrms.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.admin.domain.entity.UserPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {

    @Select("SELECT * FROM sys_schema.t_user_preference WHERE user_id = #{userId} AND pref_key = #{prefKey}")
    UserPreference selectByUserAndKey(@Param("userId") Long userId, @Param("prefKey") String prefKey);

    @Select("SELECT * FROM sys_schema.t_user_preference WHERE user_id = #{userId}")
    List<UserPreference> selectByUserId(@Param("userId") Long userId);
}
