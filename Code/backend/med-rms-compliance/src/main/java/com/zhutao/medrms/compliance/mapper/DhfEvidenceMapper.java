package com.zhutao.medrms.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.compliance.domain.entity.DhfEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DhfEvidenceMapper extends BaseMapper<DhfEvidence> {

    @Select("SELECT * FROM compliance_schema.t_dhf_evidence WHERE project_id = #{projectId} AND is_deleted = false ORDER BY uploaded_at DESC")
    List<DhfEvidence> selectByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT * FROM compliance_schema.t_dhf_evidence WHERE project_id = #{projectId} AND evidence_type = #{evidenceType} AND is_deleted = false")
    List<DhfEvidence> selectByType(@Param("projectId") Long projectId, @Param("evidenceType") String evidenceType);
}