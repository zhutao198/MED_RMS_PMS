package com.zhutao.medrms.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.notification.domain.entity.EmailQueue;
import com.zhutao.medrms.notification.mapper.EmailQueueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailQueueService {

    private final EmailQueueMapper emailQueueMapper;

    @Transactional
    public EmailQueue queueEmail(String toAddress, String subject, String body, LocalDateTime scheduledAt) {
        EmailQueue email = new EmailQueue();
        email.setToAddress(toAddress);
        email.setSubject(subject);
        email.setBody(body);
        email.setStatus("PENDING");
        email.setScheduledAt(scheduledAt != null ? scheduledAt : LocalDateTime.now());
        emailQueueMapper.insert(email);
        log.info("邮件已加入队列: to={}, subject={}", toAddress, subject);
        return email;
    }

    @Transactional
    public EmailQueue queueEmailCc(String toAddress, String ccAddress, String subject, String body) {
        EmailQueue email = new EmailQueue();
        email.setToAddress(toAddress);
        email.setCcAddress(ccAddress);
        email.setSubject(subject);
        email.setBody(body);
        email.setStatus("PENDING");
        emailQueueMapper.insert(email);
        return email;
    }

    public List<EmailQueue> getPendingEmails() {
        LambdaQueryWrapper<EmailQueue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmailQueue::getStatus, "PENDING")
               .le(EmailQueue::getScheduledAt, LocalDateTime.now())
               .lt(EmailQueue::getRetryCount, 3)
               .eq(EmailQueue::getIsDeleted, false)
               .orderByAsc(EmailQueue::getScheduledAt);
        return emailQueueMapper.selectList(wrapper);
    }

    @Transactional
    public void markAsSent(Long id) {
        EmailQueue email = emailQueueMapper.selectById(id);
        if (email != null) {
            email.setStatus("SENT");
            email.setSentAt(LocalDateTime.now());
            emailQueueMapper.updateById(email);
        }
    }

    @Transactional
    public void markAsFailed(Long id, String errorMessage) {
        EmailQueue email = emailQueueMapper.selectById(id);
        if (email != null) {
            email.setStatus("FAILED");
            email.setErrorMessage(errorMessage);
            email.setRetryCount(email.getRetryCount() + 1);
            emailQueueMapper.updateById(email);
        }
    }
}