package com.zhutao.medrms.requirement.service;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R208 v1.65: Excel 模板生成服务（FR-1.13 验收硬指标）
 *
 * 复用 R199 ProductExportService 的 SXSSFWorkbook 模式（流式写入）
 * 输出 4 个模板：URS / PRS / SRS / DRS
 *
 * 模板设计：
 *  - 第 1 行：表头（列名）
 *  - 第 2 行：示例数据（黄色背景，用户可参考）
 *  - 枚举列：数据校验下拉（priority/riskLevel/safetyClass/source/requirementCategory）
 */
@Slf4j
@Service
public class RequirementExcelTemplateService {

    /** URS 模板列定义：列名 → 枚举（下拉选项，null 表示文本）*/
    private static final Map<String, String[]> URS_COLUMNS = new LinkedHashMap<>();
    static {
        URS_COLUMNS.put("requirementNo", null);
        URS_COLUMNS.put("title", null);
        URS_COLUMNS.put("description", null);
        URS_COLUMNS.put("priority", new String[]{"MUST", "SHOULD", "COULD", "WONT"});
        URS_COLUMNS.put("riskLevel", new String[]{"HIGH", "MEDIUM", "LOW"});
        URS_COLUMNS.put("safetyClass", new String[]{"A", "B", "C"});
        URS_COLUMNS.put("requirementCategory", new String[]{"SOFTWARE", "HARDWARE", "BOTH"});
        URS_COLUMNS.put("source", new String[]{"CUSTOMER", "MARKET", "REGULATION", "INTERNAL", "COMPETITOR"});
        URS_COLUMNS.put("sourceNo", null);
        URS_COLUMNS.put("productId", null);
        URS_COLUMNS.put("acceptanceCriteria", null);
        URS_COLUMNS.put("upstreamNos", null);
    }

    /** PRS / SRS / DRS 共用 URS 基础列，附加层级特有列 */
    private static final Map<String, String[]> BASE_COLUMNS = URS_COLUMNS;

    private static final Map<String, String> LAYER_EXTRA_COLUMNS = Map.of(
        "PRS", "productModule",
        "SRS", "systemModule,testStrategy",
        "DRS", "module,assigneeId"
    );

    private static final String[] TEST_STRATEGY_OPTIONS = new String[]{
        "UNIT", "INTEGRATION", "SYSTEM", "VERIFICATION"
    };

    /**
     * 下载指定层级的 Excel 模板
     */
    public void downloadTemplate(HttpServletResponse response, String requirementType) throws IOException {
        Map<String, String[]> columns = buildColumns(requirementType);
        if (columns == null) {
            throw new IllegalArgumentException("不支持的层级: " + requirementType);
        }

        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet(requirementType + "_template");

            // 表头样式
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 示例行样式（黄色背景）
            CellStyle exampleStyle = wb.createCellStyle();
            exampleStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            exampleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 写表头
            Row header = sheet.createRow(0);
            int colIdx = 0;
            for (String colName : columns.keySet()) {
                Cell cell = header.createCell(colIdx++);
                cell.setCellValue(colName);
                cell.setCellStyle(headerStyle);
            }

            // 写示例行
            Row example = sheet.createRow(1);
            colIdx = 0;
            for (Map.Entry<String, String[]> entry : columns.entrySet()) {
                Cell cell = example.createCell(colIdx++);
                cell.setCellValue(buildExampleValue(entry.getKey(), requirementType));
                cell.setCellStyle(exampleStyle);
            }

            // 数据校验下拉（DataValidation）
            DataValidationHelper dvHelper = sheet.getDataValidationHelper();
            colIdx = 0;
            for (Map.Entry<String, String[]> entry : columns.entrySet()) {
                String[] options = entry.getValue();
                if (options != null && options.length > 0) {
                    DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(options);
                    CellRangeAddressList addressList = new CellRangeAddressList(
                        2, MAX_ROWS - 1, colIdx, colIdx);  // 从第 3 行起
                    DataValidation validation = dvHelper.createValidation(constraint, addressList);
                    validation.setSuppressDropDownArrow(true);
                    validation.setShowErrorBox(true);
                    sheet.addValidationData(validation);
                }
                colIdx++;
            }

            // 列宽自适应
            for (int i = 0; i < columns.size(); i++) {
                sheet.setColumnWidth(i, 4000);
            }

            String filename = URLEncoder.encode(
                "需求模板_" + requirementType + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            wb.write(response.getOutputStream());
            log.info("R208 模板下载: type={}, columns={}", requirementType, columns.size());
        }
    }

    /**
     * 组装层级列定义
     */
    private Map<String, String[]> buildColumns(String type) {
        if (!"URS".equals(type) && !"PRS".equals(type) && !"SRS".equals(type) && !"DRS".equals(type)) {
            return null;
        }
        Map<String, String[]> cols = new LinkedHashMap<>(BASE_COLUMNS);
        // 添加层级特有列
        String extra = LAYER_EXTRA_COLUMNS.get(type);
        if (extra != null) {
            for (String col : extra.split(",")) {
                if ("testStrategy".equals(col)) {
                    cols.put(col, TEST_STRATEGY_OPTIONS);
                } else {
                    cols.put(col, null);
                }
            }
        }
        return cols;
    }

    /** 示例值生成（黄色行） */
    private String buildExampleValue(String column, String type) {
        return switch (column) {
            case "requirementNo" -> type + "-P001-001（可留空，系统自动生成）";
            case "title" -> "示例：心电信号 24h 监测";
            case "description" -> "示例：实现 24 小时连续心电信号采集、存储与异常告警";
            case "priority" -> "MUST";
            case "riskLevel" -> "HIGH";
            case "safetyClass" -> "C";
            case "requirementCategory" -> "SOFTWARE";
            case "source" -> "CUSTOMER";
            case "sourceNo" -> "NMPA-2024-001";
            case "productId" -> "1";
            case "acceptanceCriteria" -> "1. 心率范围 30-250bpm\n2. 采样率 ≥ 250Hz\n3. 异常告警延迟 ≤ 1s";
            case "upstreamNos" -> "（多个用逗号分隔，留空表示无上游）";
            case "productModule" -> "心电模块";
            case "systemModule" -> "ECG-Service";
            case "testStrategy" -> "INTEGRATION";
            case "module" -> "ecg-collector";
            case "assigneeId" -> "（研发工程师 user_id）";
            default -> "";
        };
    }

    private static final int MAX_ROWS = 500;
}
