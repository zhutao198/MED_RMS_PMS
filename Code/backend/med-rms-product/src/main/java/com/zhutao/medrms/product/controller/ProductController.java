package com.zhutao.medrms.product.controller;

import com.zhutao.medrms.common.annotation.AuditLog;
import com.zhutao.medrms.common.result.PageResult;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.product.domain.entity.Product;
import com.zhutao.medrms.product.dto.ProductCreateRequest;
import com.zhutao.medrms.product.dto.ProductResponse;
import com.zhutao.medrms.product.dto.ProductUpdateRequest;
import com.zhutao.medrms.product.service.ProductExportService;
import com.zhutao.medrms.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * R199 v1.62: 产品管理 Controller
 * 7 个核心端点 + 1 Excel 导出，共 8 个
 */
@Tag(name = "产品管理", description = "R199 医疗器械型号字典")
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductExportService productExportService;

    @Operation(summary = "分页查询产品")
    @GetMapping
    public Result<PageResult<ProductResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String productLine,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<Product> result = productService.list(keyword, productLine, status, page, size);
        return Result.success(toResponsePage(result));
    }

    @Operation(summary = "查询所有 ACTIVE 产品（下拉框使用，5min 缓存）")
    @GetMapping("/all")
    public Result<List<ProductResponse>> listAllActive() {
        List<Product> list = productService.listAllActive();
        return Result.success(list.stream().map(ProductResponse::from).toList());
    }

    @Operation(summary = "产品详情")
    @GetMapping("/{id}")
    public Result<ProductResponse> getById(@PathVariable Long id) {
        return Result.success(ProductResponse.from(productService.getById(id)));
    }

    @Operation(summary = "创建产品（需双签）")
    @PostMapping
    @AuditLog(eventType = "CREATE", entityType = "PRODUCT", operation = "创建产品")
    public Result<ProductResponse> create(
            @RequestBody @Valid ProductCreateRequest req,
            @RequestHeader("X-Second-Signer-Id") Long secondSignerId) {
        Product created = productService.create(req, secondSignerId);
        return Result.success(ProductResponse.from(created));
    }

    @Operation(summary = "编辑产品（需双签）")
    @PutMapping("/{id}")
    @AuditLog(eventType = "MODIFY", entityType = "PRODUCT", operation = "编辑产品", entityIdSpel = "#id")
    public Result<ProductResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ProductUpdateRequest req,
            @RequestHeader("X-Second-Signer-Id") Long secondSignerId) {
        Product updated = productService.update(id, req, secondSignerId);
        return Result.success(ProductResponse.from(updated));
    }

    @Operation(summary = "删除产品（软删除 + 双签）")
    @DeleteMapping("/{id}")
    @AuditLog(eventType = "DELETE", entityType = "PRODUCT", operation = "删除产品", entityIdSpel = "#id")
    public Result<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-Second-Signer-Id") Long secondSignerId) {
        productService.delete(id, secondSignerId);
        return Result.success();
    }

    @Operation(summary = "Excel 导出（复用 R175 POI 模式）")
    @GetMapping("/export")
    public void exportExcel(HttpServletResponse response,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String productLine)
            throws IOException {
        productExportService.export(response, keyword, productLine);
    }

    private PageResult<ProductResponse> toResponsePage(PageResult<Product> src) {
        PageResult<ProductResponse> r = new PageResult<>();
        r.setData(src.getData().stream().map(ProductResponse::from).toList());
        r.setTotal(src.getTotal());
        r.setPage(src.getPage());
        r.setSize(src.getSize());
        r.setPages(src.getPages());
        return r;
    }
}