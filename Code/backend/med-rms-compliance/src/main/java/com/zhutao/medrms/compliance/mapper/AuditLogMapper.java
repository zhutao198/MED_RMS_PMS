package com.zhutao.medrms.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.compliance.domain.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Select("SELECT * FROM compliance_schema.t_audit_log WHERE entity_type = #{entityType} AND entity_id = #{entityId} ORDER BY created_at DESC")
    List<AuditLog> selectByEntity(@Param("entityType") String entityType, @Param("entityId") Long entityId);

    @Select("SELECT * FROM compliance_schema.t_audit_log WHERE operator_id = #{operatorId} ORDER BY created_at DESC")
    List<AuditLog> selectByOperator(@Param("operatorId") Long operatorId);

    @Select("SELECT * FROM compliance_schema.t_audit_log WHERE created_at BETWEEN #{startTime} AND #{endTime} ORDER BY created_at DESC")
    List<AuditLog> selectByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM compliance_schema.t_audit_log WHERE event_type = #{eventType} ORDER BY created_at DESC")
    List<AuditLog> selectByEventType(@Param("eventType") String eventType);
}