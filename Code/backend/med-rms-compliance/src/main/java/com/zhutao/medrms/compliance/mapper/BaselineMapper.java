package com.zhutao.medrms.compliance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.compliance.domain.entity.Baseline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BaselineMapper extends BaseMapper<Baseline> {

    // R223.3 DATA-004：自定义 @Select 绕过 MyBatis-Plus 全局逻辑删除，需手动过滤 is_deleted
    @Select("SELECT * FROM compliance_schema.t_baseline WHERE project_id = #{projectId} AND is_deleted = false ORDER BY created_at DESC")
    List<Baseline> selectByProject(@Param("projectId") Long projectId);

    @Select("SELECT * FROM compliance_schema.t_baseline WHERE baseline_no = #{baselineNo} AND is_deleted = false")
    Baseline selectByBaselineNo(@Param("baselineNo") String baselineNo);
}
