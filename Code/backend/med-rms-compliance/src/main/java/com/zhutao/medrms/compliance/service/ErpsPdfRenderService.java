package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.compliance.exception.DhfRenderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * R209 v1.66: NMPA eRPS 中文 PDF 渲染服务（FR-1.12 PRD §7.8.3）
 *
 * 复用 R207 DhfPdfRenderService 的 Thymeleaf + Flying Saucer 模式
 * 输入：ErpsExportService.exportProject() 的 Map 输出
 * 输出：PDF 字节流（中文）
 *
 * 文件名：NMPA-eRPS-报告-{projectNo}-{yyyyMMdd}.pdf
 * 字体：Windows msyh.ttc（与 R207 相同的反射注册策略）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpsPdfRenderService {

    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String FONT_PRIMARY = "C:/Windows/Fonts/msyh.ttc";
    private static final String FONT_FALLBACK = "C:/Windows/Fonts/simsun.ttc";
    private static final String FONT_FAMILY = "SimSun";

    private TemplateEngine templateEngine;

    private synchronized TemplateEngine getTemplateEngine() {
        if (templateEngine != null) return templateEngine;
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/erps/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        resolver.setOrder(1);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        this.templateEngine = engine;
        return engine;
    }

    /**
     * 渲染 eRPS 中文 PDF
     */
    public byte[] renderErpsPdf(Map<String, Object> erpsData) {
        log.info("R209 渲染 eRPS PDF 中文版: schema={}", erpsData.get("schema"));
        long start = System.currentTimeMillis();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Context context = new Context(Locale.CHINA);
            context.setVariable("erps", erpsData);
            context.setVariable("productInfo", erpsData.get("productInfo"));
            context.setVariable("softwareDescription", erpsData.get("softwareDescription"));
            context.setVariable("riskManagementSummary", erpsData.get("riskManagementSummary"));
            context.setVariable("requirementTrace", erpsData.get("requirementTrace"));
            context.setVariable("changeControl", erpsData.get("changeControl"));
            context.setVariable("problemReports", erpsData.get("problemReports"));
            context.setVariable("iec62304Summary", erpsData.get("iec62304Summary"));
            context.setVariable("checksum", erpsData.get("checksum"));
            context.setVariable("generatedAt", erpsData.get("generatedAt"));

            String html = getTemplateEngine().process("erps-package", context);
            renderPdfFromHtml(html, baos);

            byte[] pdf = baos.toByteArray();
            log.info("R209 eRPS PDF 渲染完成: size={}KB, elapsed={}ms",
                    pdf.length / 1024, System.currentTimeMillis() - start);
            return pdf;
        } catch (Exception e) {
            log.error("R209 eRPS PDF 渲染失败: {}", e.getMessage(), e);
            throw new DhfRenderException("eRPS PDF 渲染失败: " + e.getMessage(), e);
        }
    }

    private void renderPdfFromHtml(String html, java.io.OutputStream out) throws Exception {
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(html);
        renderer.layout();
        registerChineseFonts(renderer);
        renderer.createPDF(out);
        renderer.finishPDF();
    }

    private void registerChineseFonts(ITextRenderer renderer) {
        Path chosen = Files.exists(Paths.get(FONT_PRIMARY)) ? Paths.get(FONT_PRIMARY)
                     : Files.exists(Paths.get(FONT_FALLBACK)) ? Paths.get(FONT_FALLBACK)
                     : null;
        if (chosen == null) {
            log.warn("R209 未找到中文字体: {} / {}", FONT_PRIMARY, FONT_FALLBACK);
            return;
        }
        ITextFontResolver fontResolver = renderer.getFontResolver();
        try {
            // 反射遍历 addFont 重载（Flying Saucer 9.x 版本差异）
            for (java.lang.reflect.Method m : fontResolver.getClass().getMethods()) {
                if (!m.getName().equals("addFont")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                    m.invoke(fontResolver, chosen.toString(), FONT_FAMILY);
                    log.info("R209 中文字体注册成功: {}", chosen);
                    return;
                }
                if (params.length == 3 && params[0] == String.class && params[1] == boolean.class
                        && params[2] == String.class) {
                    m.invoke(fontResolver, chosen.toString(), false, FONT_FAMILY);
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("R209 中文字体注册失败: {}，中文可能为 □", e.getMessage());
        }
    }

    public String buildFileName(Map<String, Object> erpsData) {
        @SuppressWarnings("unchecked")
        Map<String, Object> productInfo = (Map<String, Object>) erpsData.get("productInfo");
        String projectNo = productInfo != null && productInfo.get("projectNo") != null
                ? String.valueOf(productInfo.get("projectNo")) : "UNKNOWN";
        String date = LocalDateTime.now().format(FMT_DATE);
        return String.format("NMPA-eRPS-报告-%s-%s.pdf", projectNo, date);
    }
}
