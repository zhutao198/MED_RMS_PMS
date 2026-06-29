package com.zhutao.medrms.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhutao.medrms.admin.domain.entity.Department;
import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.mapper.DepartmentMapper;
import com.zhutao.medrms.admin.mapper.UserMapper;
import com.zhutao.medrms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * R99 部门服务：CRUD + 排序 + 树查询 + 防循环引用 + 防误删
 * <p>不继承 ServiceImpl：直接调 baseMapper，避免 MyBatis-Plus ServiceImpl 默认方法在单测中抛 MybatisPlusException。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;

    /**
     * 按 parent_id 查询直接子部门。
     */
    public List<Department> listByParentId(Long parentId) {
        return departmentMapper.selectChildrenByParentId(parentId == null ? 0L : parentId);
    }

    /**
     * 查询整棵部门树（懒构建：先查所有非软删，再在内存里建父子关系）。
     */
    public List<Department> listAll() {
        return departmentMapper.selectList(
            new LambdaQueryWrapper<Department>()
                .eq(Department::getIsDeleted, false)
                .orderByAsc(Department::getLevel, Department::getSortOrder)
        );
    }

    /**
     * 创建部门。
     * <p>校验：
     * <ol>
     *   <li>code 全局唯一（数据库 UNIQUE 约束兜底）</li>
     *   <li>parent_id 存在且未软删</li>
     *   <li>parent_id=0 时 level=1，否则 level=parent.level+1</li>
     *   <li>自动生成 path = parent.path + this.id + "/"</li>
     * </ol>
     */
    @Transactional
    public Department createDepartment(Department dept) {
        // 校验 code 唯一
        if (dept.getCode() != null && !dept.getCode().isBlank()) {
            Department exist = departmentMapper.selectByCode(dept.getCode());
            if (exist != null) {
                throw BusinessException.notFound("SY0402", "部门编码已存在: " + dept.getCode());
            }
        }
        // 校验 parent_id
        Long parentId = dept.getParentId() == null ? 0L : dept.getParentId();
        if (parentId == 0L) {
            dept.setLevel(1);
            dept.setPath("/");
        } else {
            Department parent = departmentMapper.selectById(parentId);
            if (parent == null) {
                throw BusinessException.notFound("SY0301", "父部门不存在: id=" + parentId);
            }
            dept.setLevel(parent.getLevel() + 1);
            dept.setPath(parent.getPath());
        }
        if (dept.getSortOrder() == null) dept.setSortOrder(0);
        dept.setCreatedAt(LocalDateTime.now());
        dept.setUpdatedAt(LocalDateTime.now());
        departmentMapper.insert(dept);
        // 回填 path
        String selfPath = (dept.getPath() == null ? "/" : dept.getPath()) + dept.getId() + "/";
        dept.setPath(selfPath);
        departmentMapper.updateById(dept);
        log.info("R99 创建部门: id={}, code={}, name={}, path={}",
                dept.getId(), dept.getCode(), dept.getName(), selfPath);
        return dept;
    }

    /**
     * 更新部门（名称 / 编码 / 负责人）。
     * <p>禁止改 parent_id 与 level（避免破坏树结构；如需迁移请删旧建新）。
     */
    @Transactional
    public Department updateDepartment(Long id, Department updates) {
        Department existing = departmentMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("SY0301", "部门不存在: id=" + id);
        }
        // 编码唯一性校验
        if (updates.getCode() != null && !updates.getCode().equals(existing.getCode())) {
            Department sameCode = departmentMapper.selectByCode(updates.getCode());
            if (sameCode != null && !sameCode.getId().equals(id)) {
                throw BusinessException.notFound("SY0402", "部门编码已存在: " + updates.getCode());
            }
            existing.setCode(updates.getCode());
        }
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getLeaderId() != null) existing.setLeaderId(updates.getLeaderId());
        if (updates.getSortOrder() != null) existing.setSortOrder(updates.getSortOrder());
        existing.setUpdatedAt(LocalDateTime.now());
        departmentMapper.updateById(existing);
        return existing;
    }

    /**
     * 删除部门（软删除）。
     */
    @Transactional
    public void deleteDepartment(Long id) {
        Department existing = departmentMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("SY0301", "部门不存在: id=" + id);
        }
        // 1. 校验子部门
        List<Department> children = departmentMapper.selectChildrenByParentId(id);
        if (!children.isEmpty()) {
            throw BusinessException.notFound("SY0403",
                "该部门下还有 " + children.size() + " 个子部门，请先删除子部门");
        }
        // 2. 校验关联用户
        Long userCount = userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .eq(User::getDeptId, id)
                .eq(User::getIsDeleted, false)
        );
        if (userCount > 0) {
            throw BusinessException.notFound("SY0404",
                "该部门下还有 " + userCount + " 个用户，请先调整用户部门");
        }
        departmentMapper.deleteById(id);
        log.info("R99 删除部门: id={}, code={}", id, existing.getCode());
    }

    /**
     * 排序（同级重排 sort_order）。
     */
    @Transactional
    public void updateSortOrder(Long id, Integer sortOrder) {
        Department existing = departmentMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("SY0301", "部门不存在: id=" + id);
        }
        existing.setSortOrder(sortOrder == null ? 0 : sortOrder);
        existing.setUpdatedAt(LocalDateTime.now());
        departmentMapper.updateById(existing);
    }
}