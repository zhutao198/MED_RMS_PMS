package com.zhutao.medrms.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.compliance.domain.entity.Baseline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BaselineMapper extends BaseMapper<Baseline> {

    @Select("SELECT * FROM compliance_schema.t_baseline WHERE project_id = #{projectId} ORDER BY created_at DESC")
    List<Baseline> selectByProject(@Param("projectId") Long projectId);

    @Select("SELECT * FROM compliance_schema.t_baseline WHERE baseline_no = #{baselineNo}")
    Baseline selectByBaselineNo(@Param("baselineNo") String baselineNo);
}
