package com.zhutao.medrms.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.admin.domain.dto.OrgNode;
import com.zhutao.medrms.admin.domain.entity.DictItem;
import com.zhutao.medrms.admin.domain.entity.Role;
import com.zhutao.medrms.admin.domain.entity.SystemConfig;
import com.zhutao.medrms.admin.domain.entity.User;
import com.zhutao.medrms.admin.mapper.DictItemMapper;
import com.zhutao.medrms.admin.mapper.RoleMapper;
import com.zhutao.medrms.admin.mapper.SystemConfigMapper;
import com.zhutao.medrms.admin.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final DictItemMapper dictItemMapper;
    private final RoleMapper roleMapper;
    private final SystemConfigMapper configMapper;
    private final UserMapper userMapper;
    private final DepartmentService departmentService;

    public List<DictItem> getDictsByType(String dictType) {
        return dictItemMapper.selectByType(dictType);
    }

    public List<DictItem> getAllDicts() {
        return dictItemMapper.selectAll();
    }

    @Transactional
    public DictItem createDict(DictItem item) {
        dictItemMapper.insert(item);
        log.info("创建字典项: type={}, code={}", item.getDictType(), item.getDictCode());
        return item;
    }

    @Transactional
    public DictItem updateDict(Long id, DictItem updates) {
        DictItem item = dictItemMapper.selectById(id);
        if (item != null) {
            if (updates.getDictName() != null) item.setDictName(updates.getDictName());
            if (updates.getSortOrder() != null) item.setSortOrder(updates.getSortOrder());
            if (updates.getStatus() != null) item.setStatus(updates.getStatus());
            dictItemMapper.updateById(item);
        }
        return item;
    }

    @Transactional
    public void deleteDict(Long id) {
        DictItem item = dictItemMapper.selectById(id);
        if (item != null) {
            item.setIsDeleted(true);
            dictItemMapper.updateById(item);
        }
    }

    public List<Role> getRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().eq(Role::getIsDeleted, false));
    }

    @Transactional
    public Role createRole(Role role) {
        roleMapper.insert(role);
        log.info("创建角色: {}", role.getRoleName());
        return role;
    }

    @Transactional
    public Role updateRole(Long id, Role updates) {
        Role role = roleMapper.selectById(id);
        if (role != null) {
            if (updates.getRoleName() != null) role.setRoleName(updates.getRoleName());
            if (updates.getDescription() != null) role.setDescription(updates.getDescription());
            roleMapper.updateById(role);
        }
        return role;
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role != null) {
            role.setIsDeleted(true);
            roleMapper.updateById(role);
        }
    }

    public List<SystemConfig> getConfigs() {
        return configMapper.selectList(new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getIsDeleted, false));
    }

    @Transactional
    public SystemConfig updateConfig(Long id, SystemConfig updates) {
        SystemConfig config = configMapper.selectById(id);
        if (config != null) {
            if (updates.getConfigValue() != null) config.setConfigValue(updates.getConfigValue());
            if (updates.getDescription() != null) config.setDescription(updates.getDescription());
            configMapper.updateById(config);
        }
        return config;
    }

    // v1.42 BUG #49 修复：组织架构树端点实现
    // R99 重写：原实现从 user.department 字符串字段聚合为 1 层伪树，不满足 PRD §7.9.4 需求。
    // 新实现委托给 DepartmentService，支持任意层级树形结构。
    public List<OrgNode> getOrgTree() {
        List<com.zhutao.medrms.admin.domain.entity.Department> all =
            departmentService.listAll();
        // 按 dept_id 统计用户数
        Map<Long, Integer> userCountMap = new HashMap<>();
        List<User> users = userMapper.selectList(
            new LambdaQueryWrapper<User>().eq(User::getIsDeleted, false)
        );
        for (User u : users) {
            if (u.getDeptId() != null) {
                userCountMap.merge(u.getDeptId(), 1, Integer::sum);
            }
        }
        // 内存构建父子关系
        Map<Long, List<com.zhutao.medrms.admin.domain.entity.Department>> childrenMap = new HashMap<>();
        for (com.zhutao.medrms.admin.domain.entity.Department d : all) {
            childrenMap.computeIfAbsent(d.getParentId() == null ? 0L : d.getParentId(),
                k -> new ArrayList<>()).add(d);
        }
        return buildOrgTree(0L, childrenMap, userCountMap);
    }

    /**
     * R99 递归构建 OrgNode（顶级 root 用 label "Med-RMS"，子节点用部门名）。
     */
    private List<OrgNode> buildOrgTree(Long parentId,
                                       Map<Long, List<com.zhutao.medrms.admin.domain.entity.Department>> childrenMap,
                                       Map<Long, Integer> userCountMap) {
        // 顶层（parentId=0）包一层 "Med-RMS" 虚拟根
        if (parentId == 0L && childrenMap.containsKey(0L) && !childrenMap.get(0L).isEmpty()) {
            OrgNode root = new OrgNode();
            root.setId(0L);
            root.setLabel("Med-RMS");
            int totalUsers = userCountMap.values().stream().mapToInt(Integer::intValue).sum();
            root.setUserCount(totalUsers);
            root.setChildren(buildOrgTreeRecursive(0L, childrenMap, userCountMap));
            return List.of(root);
        }
        return buildOrgTreeRecursive(parentId, childrenMap, userCountMap);
    }

    private List<OrgNode> buildOrgTreeRecursive(Long parentId,
                                                 Map<Long, List<com.zhutao.medrms.admin.domain.entity.Department>> childrenMap,
                                                 Map<Long, Integer> userCountMap) {
        List<com.zhutao.medrms.admin.domain.entity.Department> children =
            childrenMap.getOrDefault(parentId, new ArrayList<>());
        List<OrgNode> result = new ArrayList<>();
        for (com.zhutao.medrms.admin.domain.entity.Department d : children) {
            OrgNode node = new OrgNode();
            node.setId(d.getId());
            node.setLabel(d.getName());
            node.setUserCount(userCountMap.getOrDefault(d.getId(), 0));
            node.setChildren(buildOrgTreeRecursive(d.getId(), childrenMap, userCountMap));
            result.add(node);
        }
        return result;
    }
}