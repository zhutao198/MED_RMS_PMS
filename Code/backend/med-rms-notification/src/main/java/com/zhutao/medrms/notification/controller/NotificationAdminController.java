package com.zhutao.medrms.notification.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.notification.domain.entity.EmailQueue;
import com.zhutao.medrms.notification.domain.entity.ImQueue;
import com.zhutao.medrms.notification.domain.entity.NotificationChannel;
import com.zhutao.medrms.notification.domain.entity.NotificationSettings;
import com.zhutao.medrms.notification.mapper.ImQueueMapper;
import com.zhutao.medrms.notification.mapper.NotificationChannelMapper;
import com.zhutao.medrms.notification.service.EmailQueueService;
import com.zhutao.medrms.notification.service.NotificationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationAdminController {

    private final EmailQueueService emailQueueService;
    private final NotificationSettingsService settingsService;
    private final NotificationChannelMapper channelMapper;
    private final ImQueueMapper imQueueMapper;

    @PostMapping("/email/queue")
    public Result<EmailQueue> queueEmail(@RequestParam String toAddress,
                                        @RequestParam String subject,
                                        @RequestParam String body,
                                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime scheduledAt) {
        return Result.success(emailQueueService.queueEmail(toAddress, subject, body, scheduledAt));
    }

    @PostMapping("/email/queue-cc")
    public Result<EmailQueue> queueEmailCc(@RequestParam String toAddress,
                                           @RequestParam String ccAddress,
                                           @RequestParam String subject,
                                           @RequestParam String body) {
        return Result.success(emailQueueService.queueEmailCc(toAddress, ccAddress, subject, body));
    }

    @GetMapping("/email/pending")
    public Result<List<EmailQueue>> getPendingEmails() {
        return Result.success(emailQueueService.getPendingEmails());
    }

    @PostMapping("/email/{id}/sent")
    public Result<Void> markAsSent(@PathVariable Long id) {
        emailQueueService.markAsSent(id);
        return Result.success();
    }

    @PostMapping("/email/{id}/failed")
    public Result<Void> markAsFailed(@PathVariable Long id, @RequestParam String errorMessage) {
        emailQueueService.markAsFailed(id, errorMessage);
        return Result.success();
    }

    @GetMapping("/settings/{userId}")
    public Result<NotificationSettings> getSettings(@PathVariable Long userId) {
        return Result.success(settingsService.getByUser(userId));
    }

    @PostMapping("/settings/{userId}")
    public Result<NotificationSettings> saveSettings(@PathVariable Long userId, @RequestBody NotificationSettings settings) {
        return Result.success(settingsService.createOrUpdate(userId, settings));
    }

    // ==================== v1.46 P1-后端-4：通知渠道管理 ====================

    @GetMapping("/channels")
    public Result<List<NotificationChannel>> listChannels() {
        return Result.success(channelMapper.selectList(
            new LambdaQueryWrapper<NotificationChannel>()
                .eq(NotificationChannel::getIsDeleted, false)
                .orderByAsc(NotificationChannel::getId)
        ));
    }

    @PostMapping("/channels")
    public Result<NotificationChannel> createChannel(@RequestBody NotificationChannel channel) {
        channel.setId(null);
        channelMapper.insert(channel);
        return Result.success(channel);
    }

    @PutMapping("/channels/{id}")
    public Result<NotificationChannel> updateChannel(@PathVariable Long id, @RequestBody NotificationChannel channel) {
        channel.setId(id);
        channelMapper.updateById(channel);
        return Result.success(channel);
    }

    @DeleteMapping("/channels/{id}")
    public Result<Void> deleteChannel(@PathVariable Long id) {
        channelMapper.softDeleteById(id);
        return Result.success();
    }

    @GetMapping("/im/pending")
    public Result<List<ImQueue>> getPendingIm() {
        return Result.success(imQueueMapper.selectList(
            new LambdaQueryWrapper<ImQueue>()
                .eq(ImQueue::getStatus, "PENDING")
                .lt(ImQueue::getRetryCount, 3)
                .eq(ImQueue::getIsDeleted, false)
                .orderByAsc(ImQueue::getCreatedAt)
        ));
    }
}