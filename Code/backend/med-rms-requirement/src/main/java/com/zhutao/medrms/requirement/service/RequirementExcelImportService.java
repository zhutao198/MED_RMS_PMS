package com.zhutao.medrms.requirement.service;

import lombok.extern.slf4j.Slf4j;
import com.zhutao.medrms.common.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * R208 v1.65: 四层需求 Excel 导入解析服务（FR-1.13）
 *
 * 复用 R199 ProductExportService 的 POI 经验，导出用 SXSSFWorkbook，导入用 XSSFWorkbook
 * 输出 List<Map<columnLetter, value>>，交给 RequirementExcelValidator + RequirementService 后续处理
 *
 * 支持的层级：URS / PRS / SRS / DRS（与模板对齐）
 * 单文件最大 500 行（PRD 性能硬指标）
 */
@Slf4j
@Service
public class RequirementExcelImportService {

    private static final int MAX_ROWS = 500;
    private static final Set<String> SUPPORTED_TYPES = Set.of("URS", "PRS", "SRS", "DRS");

    /**
     * 解析 Excel 文件为行数据 + 表头
     *
     * @param file 上传的 .xlsx 文件
     * @return ParseResult { headers: [...], rows: [{列名: 值}, ...] }
     * @throws IOException 解析失败
     */
    public ParseResult parse(MultipartFile file, String requirementType) throws IOException {
        if (!SUPPORTED_TYPES.contains(requirementType)) {
            throw BusinessException.param(
                "不支持的层级: " + requirementType + "（仅 URS/PRS/SRS/DRS）");
        }
        try (InputStream is = file.getInputStream();
             XSSFWorkbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel 文件无有效工作表");
            }

            // 第 1 行 = 表头
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel 第 1 行为空，缺少表头");
            }
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                headers.add(getCellString(cell));
            }

            // 数据行（第 2 行起）
            List<Map<String, String>> rows = new ArrayList<>();
            int physicalRowCount = sheet.getPhysicalNumberOfRows();
            for (int i = 1; i < physicalRowCount; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> rowMap = new LinkedHashMap<>();
                boolean allBlank = true;
                for (int col = 0; col < headers.size(); col++) {
                    Cell cell = row.getCell(col);
                    String val = getCellString(cell);
                    if (val != null && !val.isBlank()) allBlank = false;
                    rowMap.put(headers.get(col), val);
                }
                if (allBlank) continue; // 跳过空行
                rows.add(rowMap);
                if (rows.size() > MAX_ROWS) {
                    throw new IllegalArgumentException(
                        "Excel 行数超过上限 " + MAX_ROWS + "（PRD 性能硬指标），请分批导入");
                }
            }

            log.info("R208 Excel 解析完成: type={}, headers={}, rows={}",
                    requirementType, headers.size(), rows.size());
            return new ParseResult(headers, rows);
        }
    }

    /**
     * 提取单元格为 String（统一处理数字/日期/布尔）
     */
    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                // 避免科学计数法（ID/编号场景）
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    yield String.valueOf((long) v);
                }
                yield String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }
            default -> "";
        };
    }

    /**
     * 解析结果
     */
    public record ParseResult(List<String> headers, List<Map<String, String>> rows) {}
}
