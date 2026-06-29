package com.zhutao.medrms.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.compliance.domain.entity.ReportTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReportTemplateMapper extends BaseMapper<ReportTemplate> {

    @Select("SELECT * FROM report_schema.report_template WHERE is_active = TRUE ORDER BY id")
    List<ReportTemplate> selectActive();

    @Select("SELECT * FROM report_schema.report_template WHERE type = #{type} AND is_active = TRUE ORDER BY id LIMIT 1")
    ReportTemplate selectByType(String type);
}
