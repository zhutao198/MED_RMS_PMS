package com.zhutao.medrms.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.product.domain.entity.Product;
import com.zhutao.medrms.product.mapper.ProductMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * R199 v1.62: 产品 Excel 导出（POI 复用 R175 模式）
 * 列：ID / 产品编码 / 产品名称 / 产品线 / 状态 / 描述 / 创建时间
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductExportService {

    private final ProductMapper productMapper;

    private static final String[] HEADERS = {
            "ID", "产品编码", "产品名称", "产品线", "状态", "描述", "创建时间"
    };

    public void export(HttpServletResponse response, String keyword, String productLine)
            throws IOException {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .and(keyword != null && !keyword.isBlank(),
                     w -> w.like(Product::getProductCode, keyword)
                           .or().like(Product::getProductName, keyword))
                .eq(productLine != null && !productLine.isBlank(),
                    Product::getProductLine, productLine)
                .orderByAsc(Product::getProductCode);
        List<Product> products = productMapper.selectList(q);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet("产品清单");
            Row header = sheet.createRow(0);
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Product p : products) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(p.getId() == null ? 0 : p.getId());
                row.createCell(1).setCellValue(p.getProductCode());
                row.createCell(2).setCellValue(p.getProductName());
                row.createCell(3).setCellValue(p.getProductLine());
                row.createCell(4).setCellValue(p.getStatus());
                row.createCell(5).setCellValue(p.getDescription() == null ? "" : p.getDescription());
                row.createCell(6).setCellValue(
                        p.getCreatedAt() == null ? "" : p.getCreatedAt().toString());
            }

            String filename = URLEncoder.encode("产品清单.xlsx", StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            wb.write(response.getOutputStream());
            log.info("ProductExportService 导出 {} 条记录", products.size());
        }
    }
}