package com.zhutao.medrms.traceability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.traceability.domain.entity.TraceLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TraceLinkMapper extends BaseMapper<TraceLink> {

    @Select("SELECT * FROM trace_schema.t_trace_link WHERE source_id = #{sourceId} AND is_deleted = false")
    List<TraceLink> selectBySourceId(@Param("sourceId") Long sourceId);

    @Select("SELECT * FROM trace_schema.t_trace_link WHERE target_id = #{targetId} AND is_deleted = false")
    List<TraceLink> selectByTargetId(@Param("targetId") Long targetId);

    @Select("SELECT * FROM trace_schema.t_trace_link WHERE project_id = #{projectId} AND link_type = #{linkType} AND is_deleted = false ORDER BY id ASC")
    List<TraceLink> selectByProjectAndType(@Param("projectId") Long projectId, @Param("linkType") String linkType);

    @Select("SELECT * FROM trace_schema.t_trace_link WHERE project_id = #{projectId} AND is_deleted = false ORDER BY id ASC")
    List<TraceLink> selectByProject(@Param("projectId") Long projectId);

    @Select("SELECT COUNT(*) FROM trace_schema.t_trace_link WHERE source_id = #{sourceId} AND target_id = #{targetId} AND link_type = #{linkType} AND is_deleted = false")
    long countExists(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId, @Param("linkType") String linkType);

    // v1.55 修复：按 (source, target) 对查询所有 linkType 的 TraceLink
    @Select("SELECT * FROM trace_schema.t_trace_link WHERE source_id = #{sourceId} AND target_id = #{targetId} AND is_deleted = false ORDER BY id ASC")
    List<TraceLink> selectByPair(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
}
