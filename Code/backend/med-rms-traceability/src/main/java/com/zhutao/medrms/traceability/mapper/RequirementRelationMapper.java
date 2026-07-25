package com.zhutao.medrms.traceability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.traceability.domain.entity.RequirementRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RequirementRelationMapper extends BaseMapper<RequirementRelation> {

    // 逻辑删除修复：自定义 @Select 绕过 MyBatis-Plus 全局逻辑删除，需手动过滤 is_deleted = false
    @Select("SELECT * FROM trace_schema.t_requirement_relation WHERE source_req_id = #{sourceReqId} AND is_deleted = false")
    List<RequirementRelation> selectBySourceReqId(@Param("sourceReqId") Long sourceReqId);

    @Select("SELECT * FROM trace_schema.t_requirement_relation WHERE target_req_id = #{targetReqId} AND is_deleted = false")
    List<RequirementRelation> selectByTargetReqId(@Param("targetReqId") Long targetReqId);

    @Select("SELECT * FROM trace_schema.t_requirement_relation WHERE relation_type = 'HORIZONTAL' AND horizontal_type = #{type} AND is_deleted = false")
    List<RequirementRelation> selectByHorizontalType(@Param("type") String type);
}