package com.zhutao.medrms.change.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.change.domain.entity.ChangeAttachment;
import com.zhutao.medrms.change.mapper.ChangeAttachmentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ChangeAttachmentService 单元测试（W12-D3）
 * 变更附件管理
 */
@ExtendWith(MockitoExtension.class)
class ChangeAttachmentServiceTest {

    @Mock private ChangeAttachmentMapper mapper;

    @InjectMocks private ChangeAttachmentService service;

    @Test
    @DisplayName("list-按 changeId 过滤")
    void list() {
        when(mapper.selectByChangeId(1L)).thenReturn(List.of(new ChangeAttachment()));
        assertEquals(1, service.list(1L).size());
    }

    @Test
    @DisplayName("getById-不存在抛错")
    void getById_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(Exception.class, () -> service.getById(99L));
    }

    @Test
    @DisplayName("delete-存在则物理删除（v1.43 P1-6 修复后走 deleteById）")
    void delete() {
        ChangeAttachment att = new ChangeAttachment();
        att.setId(1L);
        att.setStoragePath("/tmp/nonexistent-" + System.nanoTime() + ".bin");
        when(mapper.selectById(1L)).thenReturn(att);

        service.delete(1L, 100L);

        // Service 在 v1.43 P1-6 修复后改为物理删除 + deleteById（不再 updateById）
        verify(mapper).deleteById(1L);
        verify(mapper, never()).updateById(any(ChangeAttachment.class));
    }
}
