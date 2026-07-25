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

    // R225.2 CONTRACT-001：按 projectId + status + changeType 分页查询变更
    // ChangeRequest 实体无 projectId 字段，通过 requirement_id 关联 req_schema.t_requirement 过滤
    // status/changeType 为 null 时不过滤（COALESCE 处理）；jdbcType 显式声明避免 PostgreSQL "无法确定参数类型" 错误
    @Select("SELECT cr.* FROM chg_schema.t_change_request cr " +
            "INNER JOIN req_schema.t_requirement r ON cr.requirement_id = r.id " +
            "WHERE cr.is_deleted = false AND r.is_deleted = false " +
            "AND r.project_id = #{projectId,jdbcType=BIGINT} " +
            "AND (#{status,jdbcType=VARCHAR} IS NULL OR UPPER(cr.status) = UPPER(#{status,jdbcType=VARCHAR})) " +
            "AND (#{changeType,jdbcType=VARCHAR} IS NULL OR cr.change_type = #{changeType,jdbcType=VARCHAR}) " +
            "ORDER BY cr.created_at DESC LIMIT #{size,jdbcType=INTEGER} OFFSET #{offset,jdbcType=INTEGER}")
    List<ChangeRequest> selectByProjectAndConditions(@Param("projectId") Long projectId,
                                                      @Param("status") String status,
                                                      @Param("changeType") String changeType,
                                                      @Param("size") int size,
                                                      @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM chg_schema.t_change_request cr " +
            "INNER JOIN req_schema.t_requirement r ON cr.requirement_id = r.id " +
            "WHERE cr.is_deleted = false AND r.is_deleted = false " +
            "AND r.project_id = #{projectId,jdbcType=BIGINT} " +
            "AND (#{status,jdbcType=VARCHAR} IS NULL OR UPPER(cr.status) = UPPER(#{status,jdbcType=VARCHAR})) " +
            "AND (#{changeType,jdbcType=VARCHAR} IS NULL OR cr.change_type = #{changeType,jdbcType=VARCHAR})")
    long countByProjectAndConditions(@Param("projectId") Long projectId,
                                      @Param("status") String status,
                                      @Param("changeType") String changeType);
}