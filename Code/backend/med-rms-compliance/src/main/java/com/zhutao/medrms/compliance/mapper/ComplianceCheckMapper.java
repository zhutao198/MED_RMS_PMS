package com.zhutao.medrms.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.compliance.domain.entity.ComplianceCheck;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ComplianceCheckMapper extends BaseMapper<ComplianceCheck> {

    @Select("SELECT * FROM compliance_schema.t_compliance_check WHERE requirement_id = #{requirementId} AND is_deleted = false ORDER BY checked_at DESC")
    List<ComplianceCheck> selectByRequirementId(@Param("requirementId") Long requirementId);

    @Select("SELECT cc.* FROM compliance_schema.t_compliance_check cc " +
            "INNER JOIN req_schema.t_requirement r ON cc.requirement_id = r.id " +
            "WHERE r.project_id = #{projectId} AND cc.is_deleted = false " +
            "ORDER BY cc.checked_at DESC")
    List<ComplianceCheck> selectByProjectId(@Param("projectId") Long projectId);
}