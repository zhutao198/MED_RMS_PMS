package com.zhutao.medrms.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.requirement.domain.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    @Select("SELECT COUNT(*) FROM req_schema.t_review WHERE requirement_id = #{requirementId}")
    long countByRequirement(@Param("requirementId") Long requirementId);

    @Select("SELECT COUNT(*) FROM req_schema.t_review WHERE requirement_id = #{requirementId} AND decision = 'APPROVED'")
    long countApprovedByRequirement(@Param("requirementId") Long requirementId);

    /** v1.47 BUG #128 配套：拉取某需求所有评审记录 */
    @Select("SELECT * FROM req_schema.t_review WHERE requirement_id = #{requirementId} ORDER BY round DESC, id ASC")
    List<Review> selectByRequirementId(@Param("requirementId") Long requirementId);

    /** v1.47 BUG #128 配套：拉取本轮（最新 round）的所有评审 */
    @Select("SELECT * FROM req_schema.t_review WHERE requirement_id = #{requirementId} AND round = (SELECT MAX(round) FROM req_schema.t_review WHERE requirement_id = #{requirementId})")
    List<Review> selectLatestRoundByRequirement(@Param("requirementId") Long requirementId);
}