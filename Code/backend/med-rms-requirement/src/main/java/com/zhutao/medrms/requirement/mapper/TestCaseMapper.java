package com.zhutao.medrms.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.requirement.domain.entity.TestCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TestCaseMapper extends BaseMapper<TestCase> {

    /**
     * FR-0.10: 标记一组需求关联的测试用例为 suspect
     * v1.45 BUG #97 修复：原用 ${ids} 直接拼接，MyBatis 传 List 会渲染为 "[1, 2, 3]"，
     *         PG 期望 IN (1, 2, 3)。改用 MyBatis <foreach> 安全拼接。
     */
    @Update("<script>UPDATE req_schema.t_test_case SET is_suspect = true WHERE requirement_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int markSuspectByRequirementIds(@Param("ids") List<Long> requirementIds);
}