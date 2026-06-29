package com.zhutao.medrms.traceability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.traceability.domain.entity.TraceGapIgnored;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TraceGapIgnoredMapper extends BaseMapper<TraceGapIgnored> {

    @Select("SELECT * FROM trace_schema.t_trace_gap_ignored WHERE project_id = #{projectId} ORDER BY ignored_at DESC")
    List<TraceGapIgnored> selectByProject(@Param("projectId") Long projectId);

    @Select("SELECT * FROM trace_schema.t_trace_gap_ignored WHERE project_id = #{projectId} AND gap_type = #{gapType} AND requirement_id = #{requirementId} LIMIT 1")
    TraceGapIgnored findUnique(@Param("projectId") Long projectId, @Param("gapType") String gapType, @Param("requirementId") Long requirementId);
}
