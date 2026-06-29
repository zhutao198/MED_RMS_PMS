package com.zhutao.medrms.compliance.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.compliance.domain.entity.StatisticsSnapshot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Mapper
public interface StatisticsSnapshotMapper extends BaseMapper<StatisticsSnapshot> {

    default List<StatisticsSnapshot> selectByProjectAndType(Long projectId, String metricType) {
        LambdaQueryWrapper<StatisticsSnapshot> w = new LambdaQueryWrapper<>();
        w.eq(StatisticsSnapshot::getProjectId, projectId)
         .eq(StatisticsSnapshot::getMetricType, metricType)
         .orderByDesc(StatisticsSnapshot::getCalculatedAt);
        return selectList(w);
    }

    default List<StatisticsSnapshot> selectListSafe(Long projectId, String metricType) {
        try {
            return selectByProjectAndType(projectId, metricType);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Delete("DELETE FROM report_schema.statistics_snapshot WHERE project_id = #{projectId} AND metric_type = #{metricType}")
    int deleteByProjectAndType(Long projectId, String metricType);

    @Insert("""
        INSERT INTO report_schema.statistics_snapshot
        (project_id, metric_type, metric_key, metric_value, dimension_json, calculated_at)
        VALUES
        (#{projectId}, #{metricType}, #{metricKey}, #{metricValue}, #{dimensionJson}::jsonb, #{calculatedAt})
        """)
    int insertRaw(@Param("projectId") Long projectId,
                  @Param("metricType") String metricType,
                  @Param("metricKey") String metricKey,
                  @Param("metricValue") BigDecimal metricValue,
                  @Param("dimensionJson") String dimensionJson,
                  @Param("calculatedAt") LocalDateTime calculatedAt);
}
