package com.zhutao.medrms.change.service;

import com.zhutao.medrms.change.domain.entity.ChangeAttachment;
import com.zhutao.medrms.change.mapper.ChangeAttachmentMapper;
import com.zhutao.medrms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * v1.43 P1-6 修复：变更单附件上传/下载/删除
 * 之前只在前端 localStorage 暂存，刷新即丢失。
 * 现在使用 chg_schema.t_change_attachment 表 + 本地文件系统存储（生产可替换为 MinIO/S3）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeAttachmentService {

    private final ChangeAttachmentMapper attachmentMapper;

    @Value("${med-rms.attachment.storage-path:/tmp/med-rms-attachments}")
    private String storageRoot;

    /**
     * 上传附件到变更单
     */
    @Transactional
    public ChangeAttachment upload(Long changeId, MultipartFile file, Long uploaderId, String uploaderName) {
        if (changeId == null) {
            throw BusinessException.param("changeId 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.param("文件不能为空");
        }
        if (file.getSize() > 50L * 1024 * 1024) {
            throw BusinessException.param("文件大小不能超过 50MB");
        }

        // 物理存储
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String ext = "";
        int dotIdx = original.lastIndexOf('.');
        if (dotIdx > 0) ext = original.substring(dotIdx);
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target;
        try {
            Path dir = Paths.get(storageRoot, String.valueOf(changeId));
            Files.createDirectories(dir);
            target = dir.resolve(stored);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("附件写入失败: changeId={}, name={}", changeId, original, e);
            throw BusinessException.sys("附件存储失败: " + e.getMessage());
        }

        // 写库
        ChangeAttachment att = new ChangeAttachment();
        att.setChangeId(changeId);
        att.setFileName(stored);
        att.setOriginalName(original);
        att.setContentType(file.getContentType());
        att.setFileSize(file.getSize());
        att.setStoragePath(target.toString());
        att.setUploadedBy(uploaderId);
        att.setUploadedByName(uploaderName);
        att.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(att);
        log.info("附件上传成功: changeId={}, id={}, name={}", changeId, att.getId(), original);
        return att;
    }

    public List<ChangeAttachment> list(Long changeId) {
        return attachmentMapper.selectByChangeId(changeId);
    }

    public ChangeAttachment getById(Long id) {
        ChangeAttachment att = attachmentMapper.selectById(id);
        if (att == null) {
            throw new BusinessException("SY0301", "附件不存在: id=" + id);
        }
        return att;
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        ChangeAttachment att = getById(id);
        // 物理删除（允许失败但记录）
        try {
            File f = new File(att.getStoragePath());
            if (f.exists() && !f.delete()) {
                log.warn("物理文件删除失败: {}", att.getStoragePath());
            }
        } catch (Exception e) {
            log.warn("物理文件删除异常: {}", e.getMessage());
        }
        attachmentMapper.deleteById(id);
        log.info("附件删除: id={}, operatorId={}", id, operatorId);
    }
}
