package com.zhutao.medrms.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.compliance.domain.entity.Iec62304ChecklistItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface Iec62304ChecklistMapper extends BaseMapper<Iec62304ChecklistItem> {

    /** 查询项目下全部条款（按章节、条款排序） */
    @Select("SELECT * FROM compliance_schema.t_iec62304_checklist " +
            "WHERE project_id = #{projectId} " +
            "ORDER BY section_order ASC, clause_order ASC, id ASC")
    List<Iec62304ChecklistItem> selectByProjectId(@Param("projectId") Long projectId);

    /** 统计各状态数量 */
    @Select("SELECT compliance_status AS status, COUNT(*) AS cnt " +
            "FROM compliance_schema.t_iec62304_checklist " +
            "WHERE project_id = #{projectId} " +
            "GROUP BY compliance_status")
    List<Map<String, Object>> countByStatus(@Param("projectId") Long projectId);

    /** 按 clauseNo 查找（用于初始化时 upsert 防重复） */
    @Select("SELECT * FROM compliance_schema.t_iec62304_checklist " +
            "WHERE project_id = #{projectId} AND clause_no = #{clauseNo} LIMIT 1")
    Iec62304ChecklistItem findByProjectAndClause(@Param("projectId") Long projectId,
                                                 @Param("clauseNo") String clauseNo);
}
