package com.zhutao.medrms.change.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.change.domain.entity.ChangeAttachment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChangeAttachmentMapper extends BaseMapper<ChangeAttachment> {

    @Select("SELECT * FROM chg_schema.t_change_attachment WHERE change_id = #{changeId} ORDER BY created_at DESC")
    List<ChangeAttachment> selectByChangeId(@Param("changeId") Long changeId);

    @Delete("DELETE FROM chg_schema.t_change_attachment WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
