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

    // R225.2 CONTRACT-006：批量删除（带 is_deleted=false 过滤避免误删软删除记录）
    @Update("<script>UPDATE req_schema.t_test_case SET is_deleted = true WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " AND is_deleted = false</script>")
    int deleteBatchIds(@Param("ids") List<Long> ids);

    // R225.2 CONTRACT-006：批量更新状态
    @Update("<script>UPDATE req_schema.t_test_case SET status = #{status} WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " AND is_deleted = false</script>")
    int updateStatusBatch(@Param("ids") List<Long> ids, @Param("status") String status);

    // 变更影响评估：按需求 ID 查询关联测试用例（PRD 7.4.2 测试用例维度）
    @org.apache.ibatis.annotations.Select(
            "SELECT * FROM req_schema.t_test_case WHERE requirement_id = #{requirementId} AND is_deleted = false")
    List<TestCase> selectByRequirementId(@Param("requirementId") Long requirementId);
}