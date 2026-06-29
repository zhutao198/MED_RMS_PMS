package com.zhutao.medrms.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.project.domain.entity.ComplianceTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ComplianceTemplateMapper extends BaseMapper<ComplianceTemplate> {

    @Select("SELECT * FROM proj_schema.t_compliance_template WHERE is_active = TRUE ORDER BY id ASC")
    List<ComplianceTemplate> selectAllActive();

    @Select("SELECT * FROM proj_schema.t_compliance_template WHERE code = #{code} LIMIT 1")
    ComplianceTemplate findByCode(String code);
}
