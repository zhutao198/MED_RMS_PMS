package com.zhutao.medrms.change.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chg_schema.t_change_attachment")
public class ChangeAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long changeId;

    private String fileName;

    private String originalName;

    private String contentType;

    private Long fileSize;

    private String storagePath;

    private Long uploadedBy;

    private String uploadedByName;

    private LocalDateTime createdAt;
}
