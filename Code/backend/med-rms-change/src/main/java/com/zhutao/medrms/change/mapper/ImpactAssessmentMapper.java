package com.zhutao.medrms.change.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.change.domain.entity.ImpactAssessment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ImpactAssessmentMapper extends BaseMapper<ImpactAssessment> {

    @Select("SELECT * FROM chg_schema.t_impact_assessment WHERE change_request_id = #{changeId} AND is_deleted = false")
    List<ImpactAssessment> selectByChangeId(@Param("changeId") Long changeId);

    @Select("SELECT * FROM chg_schema.t_impact_assessment WHERE change_request_id IN (SELECT id FROM chg_schema.t_change_request WHERE requirement_id = #{requirementId}) AND is_deleted = false")
    List<ImpactAssessment> selectByRequirementId(@Param("requirementId") Long requirementId);

    @Select("SELECT COUNT(*) FROM chg_schema.t_impact_assessment WHERE change_request_id = #{changeId} AND is_deleted = false")
    long countByChangeId(@Param("changeId") Long changeId);

    // R274：批量按 changeIds 查影响评估（避免 N+1）
    @Select("<script>" +
            "SELECT * FROM chg_schema.t_impact_assessment " +
            "WHERE is_deleted = false " +
            "<choose>" +
            "<when test='changeIds != null and changeIds.size() > 0'>" +
            "AND change_request_id IN " +
            "<foreach collection='changeIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</when>" +
            "<otherwise>AND 1=0</otherwise>" +
            "</choose>" +
            "</script>")
    List<ImpactAssessment> selectByChangeIds(@Param("changeIds") List<Long> changeIds);
}