package com.zhutao.medrms.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.notification.domain.entity.NotificationChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationChannelMapper extends BaseMapper<NotificationChannel> {

    // v1.46 P1-后端-4：MyBatis-Plus 3.5.9 bug：updateById 不把 is_deleted 写入 SET，
    // 仅追加到 WHERE（视为逻辑删除字段）。用显式 SQL 绕过。
    @Update("UPDATE not_schema.t_notification_channel SET is_deleted = true, updated_at = NOW() WHERE id = #{id}")
    int softDeleteById(Long id);
}
