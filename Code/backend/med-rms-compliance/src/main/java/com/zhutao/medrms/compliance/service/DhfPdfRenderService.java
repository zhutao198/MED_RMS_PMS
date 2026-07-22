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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * DHF 证据包 PDF 渲染服务 - R207（FR-1.4 PRD §7.5.4）
 *
 * 核心能力：
 *  1. Thymeleaf 模板引擎（classpath:/templates/dhf/*.html）
 *  2. Flying Saucer ITextRenderer 将 HTML 转 PDF
 *  3. 中文字体加载（Windows 系统 msyh.ttc 微软雅黑 + simsun.ttc 宋体回退）
 *  4. A4 横向/纵向、页眉页脚、目录、分页
 *
 * 文件名规范：`DHF-证据包-{projectNo}-{DCP阶段}-{yyyyMMdd}.pdf`
 * 性能目标：≤30 秒完成 12 章节渲染
 *
 * 字体策略：
 *  - 优先 C:/Windows/Fonts/msyh.ttc（微软雅黑，覆盖率高）
 *  - 备选 C:/Windows/Fonts/simsun.ttc（宋体）
 *  - 字体缺失时降级：使用 Flying Saucer 默认字体（英文正常，中文可能方框）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DhfPdfRenderService {

    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String FONT_PRIMARY = "C:/Windows/Fonts/msyh.ttc";
    private static final String FONT_FALLBACK = "C:/Windows/Fonts/simsun.ttc";
    private static final String FONT_FAMILY = "SimSun"; // Flying Saucer 内部字体别名

    private TemplateEngine templateEngine;

    /**
     * 初始化 Thymeleaf 模板引擎（懒加载，避免启动时扫描全项目）
     */
    private synchronized TemplateEngine getTemplateEngine() {
        if (templateEngine != null) return templateEngine;
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/dhf/");
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
     * 渲染 DHF 证据包 PDF
     *
     * @param pkg DhfEvidenceService.generateDhfPackage() 的返回值（Map 形式）
     * @return PDF 字节流
     */
    public byte[] renderDhfPdf(Map<String, Object> pkg) {
        log.info("开始渲染 DHF PDF(R207): packageId={}", pkg.get("packageId"));
        long start = System.currentTimeMillis();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Context context = new Context(Locale.CHINA);
            context.setVariable("pkg", pkg);
            context.setVariable("project", pkg.get("project"));
            context.setVariable("coverageStats", pkg.get("coverageStats"));
            context.setVariable("iec62304Stats", pkg.get("iec62304Stats"));
            context.setVariable("traceMatrix", pkg.get("traceMatrix"));
            context.setVariable("dhfEvidences", pkg.get("dhfEvidences"));
            context.setVariable("changeHistory", pkg.get("changeHistory"));
            context.setVariable("auditLogs", pkg.get("auditLogs"));
            context.setVariable("signatureLogs", pkg.get("signatureLogs"));
            context.setVariable("regulatoryMappings", pkg.get("regulatoryMappings"));
            context.setVariable("baselines", pkg.get("baselines"));
            context.setVariable("soupComponents", pkg.get("soupComponents"));
            context.setVariable("problemReports", pkg.get("problemReports"));
            context.setVariable("verdict", pkg.get("verdict"));
            context.setVariable("generatedAt", pkg.get("generatedAt"));
            context.setVariable("packageId", pkg.get("packageId"));

            String html = getTemplateEngine().process("dhf-package", context);
            renderPdfFromHtml(html, baos);

            byte[] pdf = baos.toByteArray();
            log.info("DHF PDF 渲染完成(R207): packageId={}, size={}KB, elapsed={}ms",
                    pkg.get("packageId"), pdf.length / 1024, System.currentTimeMillis() - start);
            return pdf;
        } catch (Exception e) {
            log.error("DHF PDF 渲染失败(R207): {}", e.getMessage(), e);
            throw new DhfRenderException("DHF 证据包 PDF 渲染失败: " + e.getMessage(), e);
        }
    }

    /**
     * Flying Saucer HTML→PDF 渲染（含中文字体注册）
     */
    private void renderPdfFromHtml(String html, OutputStream out) throws Exception {
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(html);
        renderer.layout();

        // 注册中文字体（关键！否则中文显示为方框）
        registerChineseFonts(renderer);

        renderer.createPDF(out);
        renderer.finishPDF();
    }

    /**
     * 注册中文字体到 Flying Saucer
     * 优先 msyh.ttc（微软雅黑），缺失则降级 simsun.ttc
     * 注：Flying Saucer 9.x addFont 重载在不同子版本签名差异大，
     *     使用反射调用避免编译期绑定（失败时降级到默认字体，中文可能为 □）。
     */
    private void registerChineseFonts(ITextRenderer renderer) {
        Path primary = Paths.get(FONT_PRIMARY);
        Path fallback = Paths.get(FONT_FALLBACK);
        Path chosen = Files.exists(primary) ? primary
                     : Files.exists(fallback) ? fallback
                     : null;
        if (chosen == null) {
            log.warn("未找到中文字体(R207): {} / {}，PDF 中文可能显示为 □",
                    FONT_PRIMARY, FONT_FALLBACK);
            return;
        }
        ITextFontResolver fontResolver = renderer.getFontResolver();
        // 反射遍历 addFont 重载，找第一个匹配的方法调用
        try {
            for (java.lang.reflect.Method m : fontResolver.getClass().getMethods()) {
                if (!m.getName().equals("addFont")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 2 && params[0] == String.class && params[1] == String.class) {
                    m.invoke(fontResolver, chosen.toString(), FONT_FAMILY);
                    log.info("中文字体注册成功(R207, addFont 双参版): {}", chosen);
                    return;
                }
                if (params.length == 3 && params[0] == String.class && params[1] == boolean.class
                        && params[2] == String.class) {
                    m.invoke(fontResolver, chosen.toString(), false, FONT_FAMILY);
                    log.info("中文字体注册成功(R207, addFont 三参版): {}", chosen);
                    return;
                }
            }
            log.warn("Flying Saucer 未找到匹配的 addFont 重载(R207)，中文可能为 □");
        } catch (Exception e) {
            log.warn("中文字体注册失败(R207): {}，中文可能为 □", e.getMessage());
        }
    }

    /**
     * 生成规范文件名：DHF-证据包-{projectNo}-{DCP阶段}-{yyyyMMdd}.pdf
     */
    public String buildFileName(Map<String, Object> pkg) {
        @SuppressWarnings("unchecked")
        Map<String, Object> project = (Map<String, Object>) pkg.get("project");
        String projectNo = project != null && project.get("projectNo") != null
                ? String.valueOf(project.get("projectNo")) : "UNKNOWN";

        // 从 verdict.status 推断 DCP 阶段（项目 status 决定）
        String dcpStage = inferDcpStage(project);
        String date = LocalDateTime.now().format(FMT_DATE);
        return String.format("DHF-证据包-%s-%s-%s.pdf", projectNo, dcpStage, date);
    }

    /**
     * 从项目状态推断 DCP 阶段
     * M1=CONCEPT, M2=PLANNING, M3=DEVELOPMENT, M4=VERIFICATION, M5=RELEASE
     */
    private String inferDcpStage(Map<String, Object> project) {
        if (project == null || project.get("status") == null) return "DCP-UNKNOWN";
        String status = String.valueOf(project.get("status")).toUpperCase();
        return switch (status) {
            case "CONCEPT", "DCP1", "PLANNING_INIT" -> "DCP1";
            case "PLANNING", "DCP2" -> "DCP2";
            case "DEVELOPMENT", "DCP3" -> "DCP3";
            case "VERIFICATION", "DCP4" -> "DCP4";
            case "RELEASE", "DCP5", "CLOSED" -> "DCP5";
            default -> "DCP-OTHER";
        };
    }
}
