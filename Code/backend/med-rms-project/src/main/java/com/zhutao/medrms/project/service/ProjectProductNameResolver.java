package com.zhutao.medrms.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * R199 v1.62: 项目-产品名解析器
 *
 * 设计决策：med-rms-project 不依赖 med-rms-product（避免循环依赖，遵循项目铁律）
 * 通过 JdbcTemplate 跨 schema 查询 prd_schema.t_product
 *
 * 适用场景：
 *   1. ProjectDetail 页展示"主产品"完整名称
 *   2. ProjectList 批量显示产品名（IN clause 单次查询，避免 N+1）
 *   3. 报表 / 导出场景联表显示
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectProductNameResolver {

    private final JdbcTemplate jdbc;

    /** 单条查询 SQL */
    private static final String SQL_SINGLE =
            "SELECT id, product_code, product_name " +
            "FROM prd_schema.t_product WHERE id = ? AND NOT is_deleted";

    /** 批量查询 SQL（IN clause） */
    private static final String SQL_BATCH =
            "SELECT id, product_code, product_name " +
            "FROM prd_schema.t_product WHERE id IN (:ids) AND NOT is_deleted";

    /**
     * 解析单个产品 ID 为展示字符串（"productCode productName"）
     * @param productId 产品 ID（可空）
     * @return 展示字符串（如 "8333 8333 多参数监护仪"），空时返回 null
     */
    public String resolveDisplayName(Long productId) {
        if (productId == null) return null;
        try {
            Map<String, Object> row = jdbc.queryForMap(SQL_SINGLE, productId);
            String code = (String) row.get("product_code");
            String name = (String) row.get("product_name");
            return code + " " + name;
        } catch (EmptyResultDataAccessException e) {
            log.debug("Product not found id={}", productId);
            return null;
        }
    }

    /**
     * 批量解析（避免 N+1）
     * @param productIds 产品 ID 集合
     * @return Map<productId, displayName>
     */
    public Map<Long, String> batchResolveDisplayName(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return new HashMap<>();
        }
        // 过滤 null 和重复
        List<Long> distinctIds = productIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return new HashMap<>();
        }

        // PostgreSQL NamedParameterJdbcTemplate 支持 IN (:ids)
        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params =
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("ids", distinctIds);
        org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedJdbc =
                new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);

        List<Map<String, Object>> rows = namedJdbc.queryForList(SQL_BATCH, params);
        return rows.stream().collect(Collectors.toMap(
                row -> ((Number) row.get("id")).longValue(),
                row -> (String) row.get("product_code") + " " + (String) row.get("product_name")
        ));
    }
}