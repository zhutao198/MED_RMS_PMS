package com.zhutao.medrms.notification.controller;

import com.zhutao.medrms.common.annotation.AuditLog;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.notification.domain.entity.Notification;
import com.zhutao.medrms.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "通知", description = "通知查询与管理接口")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "获取我的未读通知")
    @GetMapping("/unread")
    public Result<List<Notification>> getUnread() {
        // P0-1 修复：userId 由前端传入改为从登录态取，杜绝横向越权
        Long userId = requireCurrentUserId();
        return Result.success(notificationService.getUnreadByUser(userId));
    }

    @Operation(summary = "获取未读数量")
    @GetMapping("/unread/count")
    public Result<Map<String, Integer>> getUnreadCount() {
        Long userId = requireCurrentUserId();
        int count = notificationService.getUnreadCount(userId);
        return Result.success(Map.of("count", count));
    }

    // v1.43 BUG #58 修复：增加"全部通知"端点（支持 status/type 过滤）
    @Operation(summary = "获取我的全部通知（支持状态/类型过滤）")
    @GetMapping("/all")
    public Result<List<Notification>> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        Long userId = requireCurrentUserId();
        return Result.success(notificationService.getAllByUser(userId, status, type));
    }

    @AuditLog(eventType = "MODIFY", entityType = "NOTIFICATION", operation = "标记已读")
    @Operation(summary = "标记已读")
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id, requireCurrentUserId());
        return Result.success(null);
    }

    @AuditLog(eventType = "MODIFY", entityType = "NOTIFICATION", operation = "全部标记已读")
    @Operation(summary = "全部标记已读")
    @PutMapping("/read/all")
    public Result<Void> markAllAsRead() {
        notificationService.markAllAsRead(requireCurrentUserId());
        return Result.success(null);
    }

    // v1.43 BUG #63 修复：删除单条通知 + 清空所有
    @AuditLog(eventType = "DELETE", entityType = "NOTIFICATION", operation = "删除通知", entityIdSpel = "#id")
    @Operation(summary = "删除通知")
    @DeleteMapping("/{id}")
    public Result<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id, requireCurrentUserId());
        return Result.success();
    }

    @AuditLog(eventType = "DELETE", entityType = "NOTIFICATION", operation = "清空所有通知")
    @Operation(summary = "清空我的所有通知")
    @DeleteMapping("/all")
    public Result<Void> deleteAll() {
        notificationService.deleteByUser(requireCurrentUserId());
        return Result.success();
    }

    /** P0-1 辅助：取当前登录用户 ID，未登录直接 403 */
    private Long requireCurrentUserId() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) {
            throw new BusinessException("SY0301", "用户未登录");
        }
        return uid;
    }
}