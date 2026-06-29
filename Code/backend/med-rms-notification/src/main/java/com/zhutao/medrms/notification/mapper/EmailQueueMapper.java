package com.zhutao.medrms.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.notification.domain.entity.EmailQueue;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailQueueMapper extends BaseMapper<EmailQueue> {
}