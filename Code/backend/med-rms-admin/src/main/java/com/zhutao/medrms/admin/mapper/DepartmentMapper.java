package com.zhutao.medrms.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.admin.domain.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * R99 部门 Mapper
 * <p>继承 BaseMapper&lt;Department&gt; 自动获得 CRUD。
 * <p>自定义 selectChildrenByParentId 用于按 parent 查询直接子部门（不含递归）。
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {

    /**
     * 按 parent_id 查询直接子部门（已过滤软删除，按 sort_order asc）。
     */
    @Select("""
        SELECT id, parent_id, name, code, sort_order, level, path, leader_id,
               created_at, updated_at, is_deleted
        FROM sys_schema.t_department
        WHERE parent_id = #{parentId} AND is_deleted = FALSE
        ORDER BY sort_order ASC, id ASC
        """)
    List<Department> selectChildrenByParentId(Long parentId);

    /**
     * 按物化路径前缀查整个子树（含自身）。
     * path 格式 /a/b/c  →  LIKE '/a/b/c%'
     */
    @Select("""
        SELECT id, parent_id, name, code, sort_order, level, path, leader_id,
               created_at, updated_at, is_deleted
        FROM sys_schema.t_department
        WHERE path LIKE CONCAT(#{pathPrefix}, '%') AND is_deleted = FALSE
        ORDER BY level ASC, sort_order ASC, id ASC
        """)
    List<Department> selectSubtreeByPathPrefix(String pathPrefix);

    /**
     * 按 code 查单个部门（用于编码唯一性校验）。
     */
    @Select("""
        SELECT id, parent_id, name, code, sort_order, level, path, leader_id,
               created_at, updated_at, is_deleted
        FROM sys_schema.t_department
        WHERE code = #{code} AND is_deleted = FALSE
        LIMIT 1
        """)
    Department selectByCode(String code);
}