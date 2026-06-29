package com.zhutao.medrms.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.compliance.domain.entity.ProblemReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProblemReportMapper extends BaseMapper<ProblemReport> {

    // R88-2 修复：原 WHERE project_id = #{projectId} 当 projectId=null 时 = NULL 永假 → 漏掉游离数据
    // 改为 (project_id = #{projectId} OR project_id IS NULL) → 兼容游离数据
    @Select("SELECT * FROM compliance_schema.t_problem_report WHERE (project_id = #{projectId} OR project_id IS NULL) AND is_deleted = false")
    IPage<ProblemReport> selectByProjectId(Page<ProblemReport> page, Long projectId);

    @Select("SELECT * FROM compliance_schema.t_problem_report WHERE severity = #{severity} AND is_deleted = false")
    IPage<ProblemReport> selectBySeverity(Page<ProblemReport> page, String severity);

    @Select("SELECT * FROM compliance_schema.t_problem_report WHERE status = #{status} AND is_deleted = false")
    IPage<ProblemReport> selectByStatus(Page<ProblemReport> page, String status);

    @Select("SELECT * FROM compliance_schema.t_problem_report WHERE is_deleted = false ORDER BY created_at DESC")
    IPage<ProblemReport> selectAll(Page<ProblemReport> page);
}