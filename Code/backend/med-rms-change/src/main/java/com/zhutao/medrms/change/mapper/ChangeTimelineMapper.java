package com.zhutao.medrms.change.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.change.domain.entity.ChangeTimelineEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChangeTimelineMapper extends BaseMapper<ChangeTimelineEntry> {

    @Select("SELECT * FROM chg_schema.t_change_timeline WHERE change_id = #{changeId} ORDER BY created_at ASC, id ASC")
    List<ChangeTimelineEntry> selectByChangeId(Long changeId);
}
