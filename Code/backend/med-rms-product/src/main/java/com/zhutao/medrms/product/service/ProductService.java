package com.zhutao.medrms.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.result.PageResult;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.common.util.TimedCache;
import com.zhutao.medrms.product.domain.entity.Product;
import com.zhutao.medrms.product.dto.ProductCreateRequest;
import com.zhutao.medrms.product.dto.ProductUpdateRequest;
import com.zhutao.medrms.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * R199 v1.62: 产品管理 Service
 * 关键设计：
 *   1. TimedCache 缓存 listAllActive 5min（复用 R198b v1.61 工具类）
 *   2. CRUD 操作强制双签（21 CFR Part 11 §11.200）— currentUserId != secondSignerId
 *   3. 删除走软删除（@TableLogic），DB 触发器 trg_prevent_hard_delete 物理阻断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;

    /** 缓存 key：所有 ACTIVE 产品列表（5min TTL） */
    private static final String CACHE_KEY_ACTIVE = "active";
    private static final long CACHE_TTL_MS = Duration.ofMinutes(5).toMillis();

    /** TimedCache 实例（R198b v1.61 工具类） */
    private final TimedCache<String, List<Product>> activeCache =
            new TimedCache<>(CACHE_TTL_MS);

    /**
     * 分页查询产品列表（管理后台用）
     */
    public PageResult<Product> list(String keyword, String productLine, String status,
                                     int page, int size) {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .and(keyword != null && !keyword.isBlank(),
                     w -> w.like(Product::getProductCode, keyword)
                           .or().like(Product::getProductName, keyword))
                .eq(productLine != null && !productLine.isBlank(),
                    Product::getProductLine, productLine)
                .eq(status != null && !status.isBlank(),
                    Product::getStatus, status)
                .orderByDesc(Product::getId);
        IPage<Product> p = productMapper.selectPage(new Page<>(page, size), q);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    /**
     * 获取所有 ACTIVE 产品（前端下拉框使用，5min 缓存）
     */
    public List<Product> listAllActive() {
        return activeCache.get(CACHE_KEY_ACTIVE, () ->
                productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, "ACTIVE")
                        .orderByAsc(Product::getProductCode)));
    }

    public Product getById(Long id) {
        if (id == null) return null;
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException("SY0301", "产品不存在 id=" + id);
        return p;
    }

    /**
     * 创建产品 — R199 v1.62 双签约束（21 CFR Part 11 §11.200）
     * @param secondSignerId 第二签名人 ID（前端 X-Second-Signer-Id header）
     */
    @Transactional
    public Product create(ProductCreateRequest req, Long secondSignerId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        validateDoubleSign(currentUserId, secondSignerId);

        // 校验 productCode 唯一（应用层提前失败；只看未删除的）
        Long existing = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductCode, req.getProductCode())
                .eq(Product::getIsDeleted, false));
        if (existing != null && existing > 0) {
            throw new BusinessException("SY0101", "产品编码已存在：" + req.getProductCode());
        }

        Product p = new Product();
        p.setProductCode(req.getProductCode());
        p.setProductName(req.getProductName());
        p.setProductLine(req.getProductLine());
        p.setStatus(req.getStatus() == null ? "ACTIVE" : req.getStatus());
        p.setDescription(req.getDescription());
        p.setIsDeleted(false);
        productMapper.insert(p);

        activeCache.invalidate(CACHE_KEY_ACTIVE);
        log.info("Product created: id={}, code={}, creator={}, secondSigner={}",
                 p.getId(), p.getProductCode(), currentUserId, secondSignerId);
        return p;
    }

    /**
     * 更新产品 — R199 v1.62 双签约束
     */
    @Transactional
    public Product update(Long id, ProductUpdateRequest req, Long secondSignerId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        validateDoubleSign(currentUserId, secondSignerId);

        Product p = getById(id);
        p.setProductName(req.getProductName());
        p.setProductLine(req.getProductLine());
        if (req.getStatus() != null) p.setStatus(req.getStatus());
        p.setDescription(req.getDescription());
        productMapper.updateById(p);

        activeCache.invalidate(CACHE_KEY_ACTIVE);
        log.info("Product updated: id={}, updater={}, secondSigner={}",
                 id, currentUserId, secondSignerId);
        return p;
    }

    /**
     * 删除产品 — R199 v1.62 双签 + 软删除（DB trigger 阻止硬删除）
     */
    @Transactional
    public void delete(Long id, Long secondSignerId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        validateDoubleSign(currentUserId, secondSignerId);

        getById(id); // 校验存在
        // @TableLogic 自动 UPDATE is_deleted=true
        // DB trigger trg_prevent_hard_delete 阻止物理 DELETE
        productMapper.deleteById(id);

        activeCache.invalidate(CACHE_KEY_ACTIVE);
        log.info("Product soft-deleted: id={}, operator={}, secondSigner={}",
                 id, currentUserId, secondSignerId);
    }

    /**
     * R199 v1.62: 双签校验（21 CFR Part 11 §11.200 主数据修改）
     * 同人双签被拒 code=SY0101（与 R153 双签 baseline 一致）
     */
    private void validateDoubleSign(Long currentUserId, Long secondSignerId) {
        if (currentUserId == null) {
            throw new BusinessException("SY0201", "未登录，无法执行主数据修改");
        }
        if (secondSignerId == null) {
            throw new BusinessException("SY0101", "主数据修改需双签，请提供第二签名人（X-Second-Signer-Id header）");
        }
        if (currentUserId.equals(secondSignerId)) {
            throw new BusinessException("SY0101", "主数据修改需双签，两人不可相同（21 CFR Part 11 §11.200）");
        }
    }
}