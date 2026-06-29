package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("report_schema.t_report")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reportType;
    private String title;
    private Long projectId;
    private String filePath;
    private Long generatedBy;
    private LocalDateTime generatedAt;
    @TableField("is_deleted")
    private Boolean deleted = false;
    private LocalDateTime createdAt;
}