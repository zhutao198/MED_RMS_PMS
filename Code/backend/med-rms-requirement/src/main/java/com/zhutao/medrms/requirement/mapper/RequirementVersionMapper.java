package com.zhutao.medrms.requirement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.requirement.domain.entity.RequirementVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RequirementVersionMapper extends BaseMapper<RequirementVersion> {

    @Select("SELECT * FROM req_schema.t_requirement_version WHERE requirement_id = #{requirementId} ORDER BY version_no DESC")
    List<RequirementVersion> selectByRequirementId(@Param("requirementId") Long requirementId);

    @Select("SELECT * FROM req_schema.t_requirement_version WHERE requirement_id = #{requirementId} ORDER BY version_no DESC LIMIT 1")
    RequirementVersion selectLatestByRequirementId(@Param("requirementId") Long requirementId);
}
