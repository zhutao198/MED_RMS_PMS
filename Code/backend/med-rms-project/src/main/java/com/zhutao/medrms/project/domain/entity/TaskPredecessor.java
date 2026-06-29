package com.zhutao.medrms.project.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("prj_schema.t_task_predecessor")
public class TaskPredecessor {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long predecessorId;

    private LocalDateTime createdAt;
}
