package com.zhutao.medrms.project.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("proj_schema.t_project_member")
public class ProjectMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String projectNo;

    private Long userId;

    private String username;

    private String realName;

    // 项目成员角色（前端 ProjectMembersAdd.vue 下拉值，原样入库，无后端枚举校验）：
    // PROJECT_MANAGER(项目经理)/REQUIREMENT_ENGINEER(需求工程师)/DEVELOPER(开发工程师)/
    // TESTER(测试工程师)/QA(质量工程师)。其中 TESTER、QA 用于 PRD §7.7.1 验证衔接通知（差异 #3b）。
    private String role;

    private String department;

    private LocalDate joinedAt;

    private String status; // ACTIVE/INACTIVE

    private Boolean isDeleted = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}