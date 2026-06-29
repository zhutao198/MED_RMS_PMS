package com.zhutao.medrms.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.compliance.domain.entity.RegulatoryMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RegulatoryMappingMapper extends BaseMapper<RegulatoryMapping> {

    @Select("SELECT * FROM compliance_schema.t_regulatory_mapping WHERE project_id = #{projectId} AND is_deleted = false")
    List<RegulatoryMapping> selectByProjectId(Long projectId);

    @Select("SELECT * FROM compliance_schema.t_regulatory_mapping WHERE regulation_type = #{regulationType} AND is_deleted = false")
    List<RegulatoryMapping> selectByRegulationType(String regulationType);
}