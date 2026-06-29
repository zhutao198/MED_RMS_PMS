package com.zhutao.medrms.change.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.change.domain.entity.ChangeApproval;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChangeApprovalMapper extends BaseMapper<ChangeApproval> {

    @Select("SELECT * FROM chg_schema.t_change_approval WHERE change_id = #{changeId} ORDER BY created_at ASC, id ASC")
    List<ChangeApproval> selectByChangeId(Long changeId);
}
