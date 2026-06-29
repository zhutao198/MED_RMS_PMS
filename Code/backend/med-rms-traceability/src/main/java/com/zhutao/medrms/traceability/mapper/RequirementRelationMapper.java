package com.zhutao.medrms.traceability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.traceability.domain.entity.RequirementRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RequirementRelationMapper extends BaseMapper<RequirementRelation> {

    @Select("SELECT * FROM trace_schema.t_requirement_relation WHERE source_req_id = #{sourceReqId}")
    List<RequirementRelation> selectBySourceReqId(@Param("sourceReqId") Long sourceReqId);

    @Select("SELECT * FROM trace_schema.t_requirement_relation WHERE target_req_id = #{targetReqId}")
    List<RequirementRelation> selectByTargetReqId(@Param("targetReqId") Long targetReqId);

    @Select("SELECT * FROM trace_schema.t_requirement_relation WHERE relation_type = 'HORIZONTAL' AND horizontal_type = #{type}")
    List<RequirementRelation> selectByHorizontalType(@Param("type") String type);
}