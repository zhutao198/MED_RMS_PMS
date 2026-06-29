package com.zhutao.medrms.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.risk.domain.entity.RiskAssessment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RiskAssessmentMapper extends BaseMapper<RiskAssessment> {

    @Select("SELECT * FROM risk_schema.t_risk_assessment WHERE requirement_id = #{requirementId} AND is_deleted = false")
    List<RiskAssessment> selectByRequirementId(@Param("requirementId") Long requirementId);

    @Select("SELECT * FROM risk_schema.t_risk_assessment ra " +
            "INNER JOIN req_schema.t_requirement r ON ra.requirement_id = r.id " +
            "WHERE r.project_id = #{projectId} AND ra.is_deleted = false")
    List<RiskAssessment> selectByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM risk_schema.t_risk_assessment ra " +
            "INNER JOIN req_schema.t_requirement r ON ra.requirement_id = r.id " +
            "WHERE r.project_id = #{projectId} AND ra.risk_level = #{riskLevel} AND ra.is_deleted = false")
    Long countByRiskLevel(@Param("projectId") Long projectId, @Param("riskLevel") String riskLevel);
}