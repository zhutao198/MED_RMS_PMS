package com.zhutao.medrms.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RequirementMapper extends BaseMapper<Requirement> {

    @Select("SELECT * FROM req_schema.t_requirement WHERE requirement_no = #{requirementNo} AND is_deleted = false")
    Requirement selectByRequirementNo(@Param("requirementNo") String requirementNo);

    @Select("SELECT * FROM req_schema.t_requirement WHERE project_id = #{projectId} AND requirement_type = #{type} AND is_deleted = false")
    List<Requirement> selectByProjectAndType(@Param("projectId") Long projectId, @Param("type") String type);

    @Select("SELECT COUNT(*) FROM req_schema.t_requirement WHERE project_id = #{projectId} AND is_deleted = false")
    long countByProject(@Param("projectId") Long projectId);

    /**
     * FR-0.10: 批量标记需求为 suspect
     */
    @Update("UPDATE req_schema.t_requirement SET is_suspect = true WHERE id IN (${ids})")
    int markSuspectBatch(@Param("ids") String ids);

    @Update(value = "UPDATE req_schema.t_requirement SET title = #{title,jdbcType=VARCHAR}, description = #{description,jdbcType=VARCHAR}, priority = #{priority,jdbcType=VARCHAR}, risk_level = #{riskLevel,jdbcType=VARCHAR}, safety_class = #{safetyClass,jdbcType=VARCHAR}, status = #{status,jdbcType=VARCHAR}, requirement_category = #{requirementCategory,jdbcType=VARCHAR}, source = #{source,jdbcType=VARCHAR}, source_no = #{sourceNo,jdbcType=VARCHAR}, updated_at = NOW() WHERE id = #{id,jdbcType=BIGINT}")
    int updateFields(@Param("id") Long id,
                     @Param("title") String title,
                     @Param("description") String description,
                     @Param("priority") String priority,
                     @Param("riskLevel") String riskLevel,
                     @Param("safetyClass") String safetyClass,
                     @Param("status") String status,
                     @Param("requirementCategory") String requirementCategory,
                     @Param("source") String source,
                     @Param("sourceNo") String sourceNo);
}