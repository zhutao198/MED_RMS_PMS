package com.zhutao.medrms.change.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.change.domain.entity.ChangeRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChangeRequestMapper extends BaseMapper<ChangeRequest> {

    // R223.2 DATA-001：自定义 @Select 绕过 MyBatis-Plus 全局逻辑删除，需手动过滤 is_deleted
    @Select("SELECT * FROM chg_schema.t_change_request WHERE requirement_id = #{requirementId} AND is_deleted = false ORDER BY created_at DESC")
    List<ChangeRequest> selectByRequirementId(@Param("requirementId") Long requirementId);

    @Select("SELECT * FROM chg_schema.t_change_request WHERE status = #{status} AND is_deleted = false ORDER BY created_at DESC")
    List<ChangeRequest> selectByStatus(@Param("status") String status);

    @Select("SELECT * FROM chg_schema.t_change_request WHERE requester_id = #{userId} AND is_deleted = false ORDER BY created_at DESC")
    List<ChangeRequest> selectByRequester(@Param("userId") Long userId);
}