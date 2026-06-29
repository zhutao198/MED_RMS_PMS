package com.zhutao.medrms.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R99 组织架构-部门实体
 * 支持任意层级树形结构，通过 parent_id + path（物化路径）实现高效子树查询。
 * <p>字段说明：
 * <ul>
 *   <li>parent_id = 0：顶级根节点（Med-RMS 整体组织）</li>
 *   <li>path：物化路径，如 /1/3/7，便于按 ancestor 查询整个子树</li>
 *   <li>level：层级深度（1=顶级，2=二级，...），冗余便于前端展示</li>
 *   <li>sort_order：同级排序，asc</li>
 * </ul>
 */
@Data
@TableName("sys_schema.t_department")
public class Department {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父部门ID，0=顶级 */
    private Long parentId;

    /** 部门名称 */
    private String name;

    /** 部门编码（跨层级唯一，如 RND / QA / RA / PMO / QM） */
    private String code;

    /** 同级排序（asc） */
    private Integer sortOrder;

    /** 层级深度（1=顶级，2=二级 ...） */
    private Integer level;

    /** 物化路径 /a/b/c，便于按 ancestor 查询 */
    private String path;

    /** 部门负责人 user_id（不强制 FK，由应用层校验存在性） */
    private Long leaderId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean isDeleted;
}