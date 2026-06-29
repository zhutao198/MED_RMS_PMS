package com.zhutao.medrms.common.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.common.outbox.OutboxMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxMapper extends BaseMapper<OutboxMessage> {
}
