package com.zhutao.medrms.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.requirement.domain.entity.RequirementAncestor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RequirementAncestorMapper extends BaseMapper<RequirementAncestor> {

    @Select("SELECT * FROM req_schema.t_requirement_ancestor WHERE descendant_id = #{descendantId}")
    List<RequirementAncestor> selectByDescendant(@Param("descendantId") Long descendantId);

    @Select("SELECT * FROM req_schema.t_requirement_ancestor WHERE ancestor_id = #{ancestorId} AND depth > 0")
    List<RequirementAncestor> selectDescendants(@Param("ancestorId") Long ancestorId);

    @Select("SELECT * FROM req_schema.t_requirement_ancestor WHERE ancestor_id = #{ancestorId} AND descendant_id = #{descendantId}")
    RequirementAncestor selectByPair(@Param("ancestorId") Long ancestorId, @Param("descendantId") Long descendantId);

    /**
     * 查找所有下游需求ID（不含自身），用于 suspect 自动标记（FR-0.10）
     */
    @Select("SELECT DISTINCT descendant_id FROM req_schema.t_requirement_ancestor WHERE ancestor_id = #{ancestorId} AND depth > 0")
    List<Long> selectDescendantIds(@Param("ancestorId") Long ancestorId);
}