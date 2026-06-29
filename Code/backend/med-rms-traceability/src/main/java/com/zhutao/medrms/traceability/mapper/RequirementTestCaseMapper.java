package com.zhutao.medrms.traceability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.traceability.domain.entity.RequirementTestCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RequirementTestCaseMapper extends BaseMapper<RequirementTestCase> {

    @Select("SELECT * FROM trace_schema.t_requirement_test_case WHERE requirement_id = #{requirementId}")
    List<RequirementTestCase> selectByRequirementId(@Param("requirementId") Long requirementId);

    @Select("SELECT * FROM trace_schema.t_requirement_test_case WHERE test_case_id = #{testCaseId}")
    List<RequirementTestCase> selectByTestCaseId(@Param("testCaseId") Long testCaseId);
}