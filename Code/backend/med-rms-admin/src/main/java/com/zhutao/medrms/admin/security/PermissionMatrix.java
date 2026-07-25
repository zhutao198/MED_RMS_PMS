package com.zhutao.medrms.admin.security;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 端点 → 权限码 集中映射（v1.27 R28）
 * 依据：PRD 7.9.1 8 类角色权限矩阵 + Detailed/04-权限设计/权限流程设计.md
 * 设计：先匹配精确路径（action），后匹配前缀路径（CRUD 默认）
 */
@Component
public class PermissionMatrix {

    /** 精确路径规则：METHOD + Ant 风格路径 + 所需 perm */
    public record ExactRule(HttpMethod method, String pathPattern, String permCode) {}

    /** 前缀规则：METHOD + URL 前缀 → perm 码（GET 默认 :list / :read，写操作按动词） */
    public record PrefixRule(HttpMethod method, String pathPrefix, String permCode) {}

    private final List<ExactRule> exactRules = new ArrayList<>();
    private final List<PrefixRule> prefixRules = new ArrayList<>();

    public PermissionMatrix() {
        // ===== 精确路径（action 端点）=====
        // 变更
        addExact(HttpMethod.POST, "/changes/{id}/submit",      "chg:create");
        addExact(HttpMethod.POST, "/changes/{id}/approve",     "chg:approve");
        addExact(HttpMethod.POST, "/changes/{id}/reject",      "chg:approve");
        addExact(HttpMethod.POST, "/changes/{id}/execute",     "chg:execute");
        addExact(HttpMethod.POST, "/changes/{id}/verify",      "chg:execute");
        addExact(HttpMethod.POST, "/changes/{id}/close",       "chg:execute");
        addExact(HttpMethod.GET,  "/changes/{id}/impacts",     "chg:analyze");
        addExact(HttpMethod.POST, "/changes/{id}/assess",      "chg:analyze");
        addExact(HttpMethod.POST, "/changes/{id}/delegate",    "chg:approve");
        addExact(HttpMethod.POST, "/changes/{id}/countersign", "chg:approve");
        addExact(HttpMethod.PATCH,"/changes/{id}/status",        "chg:approve");
        // 需求
        addExact(HttpMethod.POST, "/requirements/{id}/decompose", "req:create");
        addExact(HttpMethod.POST, "/requirements/{id}/review",    "req:review");
        addExact(HttpMethod.POST, "/requirements/{id}/approve",   "req:review");
        addExact(HttpMethod.POST, "/requirements/baseline",       "baseline:create");
        addExact(HttpMethod.GET,  "/requirements/{id}/versions",  "req:list");
        addExact(HttpMethod.GET,  "/requirements/kanban",         "req:list");
        addExact(HttpMethod.GET,  "/requirements/quality",        "req:list");
        // 测试用例
        addExact(HttpMethod.GET,  "/testcases/requirement/{id}",  "test:list");
        addExact(HttpMethod.GET,  "/testcases/project/{id}",      "test:list");
        addExact(HttpMethod.GET,  "/testcases/coverage/{id}",     "test:list");
        addExact(HttpMethod.PUT,  "/testcases/{id}/status",       "test:update");
        // 基线
        addExact(HttpMethod.GET,  "/baselines/project/{id}",      "baseline:list");
        addExact(HttpMethod.GET,  "/baselines/compare",           "baseline:compare");
        addExact(HttpMethod.PATCH,"/baselines/{id}/lock",         "baseline:create");
        addExact(HttpMethod.PATCH,"/baselines/{id}/unlock",       "baseline:create");
        // 追溯
        addExact(HttpMethod.GET,  "/traceability/matrix",         "trace:matrix");
        addExact(HttpMethod.GET,  "/traceability/coverage",       "trace:coverage");
        addExact(HttpMethod.GET,  "/traceability/gaps",           "trace:gaps");
        addExact(HttpMethod.GET,  "/traceability/breakages",      "trace:gaps");
        addExact(HttpMethod.POST, "/traceability/relations",      "trace:create");
        addExact(HttpMethod.POST, "/traceability/testcases",      "trace:create");
        // 追溯图谱
        addExact(HttpMethod.GET,  "/trace-graph/project/{id}",    "trace:matrix");
        addExact(HttpMethod.GET,  "/trace-graph/quality/{id}",    "trace:coverage");
        addExact(HttpMethod.GET,  "/trace-graph/quality/batch",   "trace:coverage");
        // 风险
        addExact(HttpMethod.POST, "/risk/assess",                 "risk:analyze");
        addExact(HttpMethod.GET,  "/risk/requirement/{id}",       "risk:list");
        addExact(HttpMethod.GET,  "/risk/report/{id}",            "risk:list");
        addExact(HttpMethod.PUT,  "/risk/{id}/control",           "risk:control");
        addExact(HttpMethod.POST, "/risk/{id}/fmea",              "risk:analyze");
        addExact(HttpMethod.PUT,  "/risk/{id}/action-status",     "risk:status");
        addExact(HttpMethod.GET,  "/risk/fmea",                   "risk:analyze");
        // 风险矩阵 / 风险登记
        addExact(HttpMethod.GET,  "/risk/matrix/list/{id}",       "risk:list");
        addExact(HttpMethod.GET,  "/risk/matrix/list",            "risk:list");
        addExact(HttpMethod.GET,  "/risk/matrix/{id}",            "risk:list");
        addExact(HttpMethod.POST, "/risk/matrix",                 "risk:create");
        addExact(HttpMethod.PUT,  "/risk/matrix/{id}",            "risk:update");
        addExact(HttpMethod.POST, "/risk/matrix/{id}/residual",   "risk:control");
        addExact(HttpMethod.GET,  "/risk/register/list",          "risk:list");
        addExact(HttpMethod.GET,  "/risk/register/{id}",          "risk:list");
        addExact(HttpMethod.POST, "/risk/register",               "risk:create");
        addExact(HttpMethod.PUT,  "/risk/register/{id}",          "risk:update");
        addExact(HttpMethod.POST, "/risk/register/{id}/close",    "risk:status");
        addExact(HttpMethod.POST, "/risk/register/{id}/accept",   "risk:status");
        // 合规/审计
        addExact(HttpMethod.GET,  "/compliance/audit-logs",                        "audit:read");
        addExact(HttpMethod.GET,  "/compliance/audit-logs/entity/{type}/{id}",     "audit:read");
        addExact(HttpMethod.GET,  "/compliance/audit-logs/operator/{id}",          "audit:read");
        addExact(HttpMethod.GET,  "/compliance/audit-logs/time-range",             "audit:read");
        addExact(HttpMethod.GET,  "/compliance/audit-logs/export",                 "audit:read");
        addExact(HttpMethod.POST, "/compliance/audit-logs/verify",                 "audit:verify");
        addExact(HttpMethod.POST, "/compliance/evidence",                          "compliance:iec62304");
        addExact(HttpMethod.DELETE,"/compliance/evidence/{id}",                    "compliance:iec62304");
        addExact(HttpMethod.GET,  "/compliance/evidence/{id}",                     "compliance:iec62304");
        addExact(HttpMethod.GET,  "/compliance/check/list/{id}",                   "compliance:iec62304");
        addExact(HttpMethod.GET,  "/compliance/check/project/{id}",                "compliance:iec62304");
        addExact(HttpMethod.POST, "/compliance/check",                             "compliance:iec62304");
        addExact(HttpMethod.POST, "/compliance/check/{id}/complete",               "compliance:iec62304");
        addExact(HttpMethod.POST, "/compliance/dhf/generate/{id}",                  "compliance:iec62304");
        addExact(HttpMethod.GET,  "/compliance/dhf/manifest/{id}",                 "compliance:iec62304");
        addExact(HttpMethod.GET,  "/compliance/dhf/download/{id}",                 "compliance:iec62304");
        addExact(HttpMethod.GET,  "/compliance/regulations",                       "regulation:read");
        addExact(HttpMethod.POST, "/compliance/regulations",                       "regulation:read");
        addExact(HttpMethod.POST, "/compliance/problem-reports",                    "pr:create");
        addExact(HttpMethod.GET,  "/compliance/problem-reports",                    "pr:list");
        addExact(HttpMethod.PUT,  "/compliance/problem-reports/{id}/status",       "pr:status");
        addExact(HttpMethod.GET,  "/compliance/reports/traceability",               "report:export");
        addExact(HttpMethod.GET,  "/compliance/reports/audit-trail",                "audit:read");
        addExact(HttpMethod.POST, "/compliance/iec62304/checklist/{id}/init",       "compliance:iec62304");
        addExact(HttpMethod.GET,  "/compliance/iec62304/checklist/{id}",            "compliance:iec62304");
        addExact(HttpMethod.POST, "/compliance/iec62304/checklist/{id}/assess",     "compliance:iec62304");
        addExact(HttpMethod.GET,  "/compliance/iec62304/checklist/{id}/stats",      "report:dashboard");
        addExact(HttpMethod.POST, "/compliance/iec62304/checklist/{id}/run-full-check", "compliance:iec62304");
        addExact(HttpMethod.GET,  "/compliance/erps/export/{id}",                   "report:export");
        addExact(HttpMethod.GET,  "/compliance/erps/download/{id}",                 "report:export");
        // 电子签名
        addExact(HttpMethod.POST, "/esignature/sign",                              "esign:sign");
        addExact(HttpMethod.POST, "/esignature/verify/{id}",                       "esign:verify");
        addExact(HttpMethod.GET,  "/esignature/entity/{type}/{id}",                "esign:read");
        addExact(HttpMethod.POST, "/esignature/{id}/invalidate",                   "esign:verify");
        addExact(HttpMethod.GET,  "/esignature/settings/{id}",                     "esign:read");
        addExact(HttpMethod.POST, "/esignature/settings/{id}/password",            "esign:pwd");
        addExact(HttpMethod.POST, "/esignature/settings/{id}/otp/enable",          "esign:otp");
        addExact(HttpMethod.POST, "/esignature/settings/{id}/otp/disable",         "esign:otp");
        addExact(HttpMethod.POST, "/esignature/settings/{id}/pin",                 "esign:pwd");
        addExact(HttpMethod.POST, "/esignature/settings/{id}/verify-password",     "esign:pwd");
        addExact(HttpMethod.POST, "/esignature/settings/{id}/verify-otp",          "esign:otp");
        addExact(HttpMethod.POST, "/esignature/settings/{id}/otp/generate",        "esign:otp");
        addExact(HttpMethod.GET,  "/esignature/settings/{id}/otp/uri",             "esign:otp");
        addExact(HttpMethod.GET,  "/esignature/signatures",                        "esign:read");
        addExact(HttpMethod.GET,  "/esignature/signatures/{id}",                   "esign:read");
        addExact(HttpMethod.POST, "/esignature/signatures/{id}/re-sign",           "esign:sign");
        // R103 A1 新增：签名意图详情
        addExact(HttpMethod.GET,  "/esignature/intents/{id}",                      "esign:read");
        // 通知
        addExact(HttpMethod.GET,  "/notifications/unread",                        "report:dashboard");
        addExact(HttpMethod.GET,  "/notifications/unread/count",                  "report:dashboard");
        // v1.43 通知模块新增端点
        addExact(HttpMethod.GET,  "/notifications/all",                            "report:dashboard");
        addExact(HttpMethod.DELETE,"/notifications/{id}",                         "report:dashboard");
        addExact(HttpMethod.DELETE,"/notifications/all",                          "report:dashboard");
        addExact(HttpMethod.PUT,  "/notifications/{id}/read",                     "report:dashboard");
        addExact(HttpMethod.PUT,  "/notifications/read/all",                      "report:dashboard");
        addExact(HttpMethod.POST, "/notifications/email/queue",                   "sys:config:list");
        addExact(HttpMethod.POST, "/notifications/email/queue-cc",                "sys:config:list");
        addExact(HttpMethod.GET,  "/notifications/email/pending",                  "sys:config:list");
        addExact(HttpMethod.POST, "/notifications/email/{id}/sent",               "sys:config:list");
        addExact(HttpMethod.POST, "/notifications/email/{id}/failed",             "sys:config:list");
        addExact(HttpMethod.GET,  "/notifications/settings/{id}",                  "report:dashboard");
        addExact(HttpMethod.POST, "/notifications/settings/{id}",                 "report:dashboard");
        // v1.46 P1-后端-4：通知渠道管理
        addExact(HttpMethod.GET,    "/notifications/channels",                    "sys:config:list");
        addExact(HttpMethod.POST,   "/notifications/channels",                    "sys:config:list");
        addExact(HttpMethod.PUT,    "/notifications/channels/{id}",               "sys:config:list");
        addExact(HttpMethod.DELETE, "/notifications/channels/{id}",               "sys:config:list");
        addExact(HttpMethod.GET,    "/notifications/im/pending",                  "sys:config:list");
        // 项目
        addExact(HttpMethod.GET,  "/projects/templates",                          "proj:list");
        addExact(HttpMethod.POST, "/projects/{id}/apply-template",                "proj:update");
        addExact(HttpMethod.POST, "/projects/templates",                          "proj:create");
        addExact(HttpMethod.PUT,  "/projects/templates/{id}",                     "proj:update");
        addExact(HttpMethod.DELETE,"/projects/templates/{id}",                    "proj:update");
        // 需求-任务转化
        addExact(HttpMethod.GET,  "/requirement-tasks/drafts/{id}",               "req:list");
        addExact(HttpMethod.POST, "/requirement-tasks/convert/{id}",              "req:create");
        addExact(HttpMethod.GET,  "/requirement-tasks/by-requirement/{id}",       "req:list");
        addExact(HttpMethod.PUT,  "/requirement-tasks/{id}/status",               "req:update");
        addExact(HttpMethod.GET,  "/requirement-tasks/progress/{id}",             "req:list");
        // P2 新增：可转化候选列表（任务转化页选单）
        addExact(HttpMethod.GET,  "/requirement-tasks/candidates",                "req:list");
        // 项目成员
        addExact(HttpMethod.GET,  "/project/member/list/{id}",                    "proj:member");
        addExact(HttpMethod.GET,  "/project/member/{id}",                         "proj:member");
        addExact(HttpMethod.POST, "/project/member",                              "proj:member");
        addExact(HttpMethod.PUT,  "/project/member/{id}",                         "proj:member");
        addExact(HttpMethod.DELETE,"/project/member/{id}",                        "proj:member");
        addExact(HttpMethod.POST, "/project/member/{id}/switch-role",             "proj:member");
        // IPD 门控
        addExact(HttpMethod.GET,  "/project/ipd-gate/list/{id}",                  "proj:gate:review");
        addExact(HttpMethod.GET,  "/project/ipd-gate/{id}",                       "proj:gate:review");
        addExact(HttpMethod.POST, "/project/ipd-gate",                            "proj:gate:review");
        addExact(HttpMethod.PUT,  "/project/ipd-gate/{id}",                       "proj:gate:review");
        addExact(HttpMethod.POST, "/project/ipd-gate/{id}/pass",                  "proj:gate:review");
        addExact(HttpMethod.POST, "/project/ipd-gate/{id}/fail",                  "proj:gate:review");
        addExact(HttpMethod.POST, "/project/ipd-gate/auto-check",                 "proj:gate:review");
        // 甘特图
        addExact(HttpMethod.GET,  "/gantt/project/{id}",                          "proj:list");
        addExact(HttpMethod.GET,  "/gantt/resources/{id}",                        "proj:list");
        addExact(HttpMethod.POST, "/gantt/tasks",                                 "proj:update");
        addExact(HttpMethod.POST, "/gantt/milestones",                            "proj:update");
        addExact(HttpMethod.GET,  "/gantt/gate/{id}/check",                       "proj:gate:review");
        addExact(HttpMethod.GET,  "/gantt/milestones/project/{id}",               "proj:list");
        addExact(HttpMethod.GET,  "/gantt/tasks/project/{id}",                    "proj:list");
        // R222: 任务更新授权（用户决策 + RBAC 漏洞修复）
        addExact(HttpMethod.PUT,  "/gantt/tasks/{id}",                           "proj:update");
        // 报表 / Dashboard
        addExact(HttpMethod.GET,  "/reports",                                     "report:stats");
        addExact(HttpMethod.POST, "/reports/generate",                            "report:export");
        addExact(HttpMethod.POST, "/reports/dhf",                                 "report:export");
        addExact(HttpMethod.GET,  "/reports/{id}/download",                       "report:export");
        // v1.46 P1-后端-1：报表配置持久化 5 端点
        addExact(HttpMethod.GET,    "/reports/configs",                          "report:stats");
        addExact(HttpMethod.GET,    "/reports/configs/{id}",                     "report:stats");
        addExact(HttpMethod.POST,   "/reports/configs",                          "report:export");
        addExact(HttpMethod.PUT,    "/reports/configs/{id}",                     "report:export");
        addExact(HttpMethod.DELETE, "/reports/configs/{id}",                     "report:export");
        addExact(HttpMethod.GET,  "/dashboard/view/requirements",                 "report:dashboard");
        addExact(HttpMethod.GET,  "/dashboard/view/risk",                         "report:dashboard");
        addExact(HttpMethod.GET,  "/dashboard/view/management",                   "report:dashboard");
        addExact(HttpMethod.GET,  "/dashboard/view/compliance",                   "report:dashboard");
        addExact(HttpMethod.PUT,  "/dashboard/layout",                             "report:dashboard");
        // SOUP
        addExact(HttpMethod.GET,  "/requirement/soup-components",                 "soup:list");
        addExact(HttpMethod.GET,  "/requirement/soup-components/{id}",            "soup:list");
        addExact(HttpMethod.POST, "/requirement/soup-components",                 "soup:create");
        addExact(HttpMethod.PUT,  "/requirement/soup-components/{id}",            "soup:update");
        addExact(HttpMethod.DELETE,"/requirement/soup-components/{id}",           "soup:update");
        addExact(HttpMethod.POST, "/requirement/soup-components/{id}/renew",      "soup:review");
        addExact(HttpMethod.GET,  "/requirement/soup-components/{id}/anomalies",  "soup:list");
        addExact(HttpMethod.GET,  "/requirement/soup-components/anomalies/all",   "soup:list");
        addExact(HttpMethod.POST, "/requirement/soup-components/{id}/anomalies/link-risk", "soup:review");
        // 需求池
        addExact(HttpMethod.GET,  "/requirement-pool",                            "req:list");
        addExact(HttpMethod.POST, "/requirement-pool",                            "req:create");
        addExact(HttpMethod.POST, "/requirement-pool/{id}/convert",               "req:create");
        // 工时
        addExact(HttpMethod.POST, "/worklog",                                     "req:update");
        addExact(HttpMethod.GET,  "/worklog/summary",                             "req:list");
        // 系统用户/角色/字典/配置
        addExact(HttpMethod.GET,  "/system/users",                                "sys:user:list");
        addExact(HttpMethod.GET,  "/system/users/{id}",                           "sys:user:list");
        addExact(HttpMethod.POST, "/system/users",                                "sys:user:list");
        addExact(HttpMethod.PUT,  "/system/users/{id}",                           "sys:user:list");
        addExact(HttpMethod.DELETE,"/system/users/{id}",                          "sys:user:list");
        addExact(HttpMethod.POST, "/system/users/{id}/reset-password",            "sys:user:list");
        addExact(HttpMethod.GET,  "/system/roles",                                "sys:role:list");
        addExact(HttpMethod.POST, "/system/roles",                                "sys:role:list");
        addExact(HttpMethod.PUT,  "/system/roles/{id}",                           "sys:role:list");
        addExact(HttpMethod.DELETE,"/system/roles/{id}",                          "sys:role:list");
        // v1.46 P1-后端-2：角色权限矩阵管理 3 端点
        addExact(HttpMethod.GET,  "/system/permissions",                          "sys:role:list");
        addExact(HttpMethod.GET,  "/system/roles/{id}/permissions",               "sys:role:list");
        addExact(HttpMethod.PUT,  "/system/roles/{id}/permissions",               "sys:role:list");
        addExact(HttpMethod.GET,  "/system/dicts",                                "sys:dict:list");
        addExact(HttpMethod.GET,  "/system/dicts/all",                            "sys:dict:list");
        addExact(HttpMethod.POST, "/system/dicts",                                "sys:dict:list");
        addExact(HttpMethod.PUT,  "/system/dicts/{id}",                           "sys:dict:list");
        addExact(HttpMethod.DELETE,"/system/dicts/{id}",                          "sys:dict:list");
        addExact(HttpMethod.GET,  "/system/configs",                              "sys:config:list");
        addExact(HttpMethod.PUT,  "/system/configs/{id}",                         "sys:config:list");
        // 管理域（admin）
        addExact(HttpMethod.GET,  "/admin/users/me",                              "sys:user:list");
        addExact(HttpMethod.GET,  "/admin/users/{id}",                            "sys:user:list");
        addExact(HttpMethod.POST, "/admin/users/{id}/verify-signature-password",  "esign:pwd");
        addExact(HttpMethod.GET,  "/admin/test",                                  "sys:config:list");
        addExact(HttpMethod.GET,  "/admin/migration/jobs",                        "sys:config:list");
        addExact(HttpMethod.GET,  "/admin/migration/jobs/{id}",                   "sys:config:list");
        addExact(HttpMethod.POST, "/admin/migration/import/requirements/json",    "req:import");
        addExact(HttpMethod.POST, "/admin/migration/import/requirements/upload-json", "req:import");
        addExact(HttpMethod.POST, "/admin/migration/import/requirements/upload-csv",  "req:import");
        // R224.1 SEC-002：OA 同步端点（web 模块下的重复版本已删除，由 admin 模块 requireAdmin 版本生效）
        addPrefix(HttpMethod.POST, "/oa-sync", "sys:user:list");   // OA 写入 sys_schema.t_department/t_user，需 sys:user:list 权限（实际 admin 角色通配 *）
        addExact(HttpMethod.GET,  "/oa-sync/status", "sys:user:list"); // 同步状态查询

        // ===== R224.2 SEC-003 渐进修复：补齐未登记端点（默认拒绝前必须先补齐）=====
        // 部门管理（DepartmentController 6 个）
        addPrefix(HttpMethod.GET,    "/system/departments", "sys:dept:list");      // 新 perm 码
        addPrefix(HttpMethod.POST,   "/system/departments", "sys:dept:list");
        addPrefix(HttpMethod.PUT,    "/system/departments", "sys:dept:list");
        addPrefix(HttpMethod.DELETE, "/system/departments", "sys:dept:list");
        // 用户偏好（UserPreferenceController 4 个）
        addPrefix(HttpMethod.GET,    "/user/preferences", "sys:user:list");
        addPrefix(HttpMethod.PUT,    "/user/preferences", "sys:user:list");
        addPrefix(HttpMethod.DELETE, "/user/preferences", "sys:user:list");
        // SystemController 补充 3 个（profile/change-password/org/tree）
        addExact(HttpMethod.GET,  "/system/profile",                  "sys:user:list");
        addExact(HttpMethod.POST, "/system/users/{id}/change-password", "sys:user:list");
        addExact(HttpMethod.GET,  "/system/org/tree",                 "sys:user:list");
        // 变更 PUT/DELETE（ChangeController 2 个）
        addExact(HttpMethod.PUT,    "/changes/{id}",                  "chg:update");
        addExact(HttpMethod.DELETE, "/changes/attachments/{id}",      "chg:update");
        // Dashboard layout 操作（POST layout / reset）
        addExact(HttpMethod.POST, "/dashboard/layout",       "report:dashboard");
        addExact(HttpMethod.POST, "/dashboard/layout/reset", "report:dashboard");
        // 法规影响（RegulationImpactController 3 个）
        addPrefix(HttpMethod.GET,  "/regulations",         "regulation:read");
        addPrefix(HttpMethod.POST, "/regulations",         "regulation:read");
        addExact(HttpMethod.POST, "/regulations/notify-update", "regulation:read");
        addExact(HttpMethod.GET,  "/regulations/impact/{var}/{var}", "regulation:read");
        // 统计（StatisticsController 6 个）
        addPrefix(HttpMethod.GET, "/statistics", "report:dashboard");
        // 甘特图 predecessors
        addExact(HttpMethod.PUT, "/gantt/tasks/{id}/predecessors", "proj:update");
        // IPD 自动校验 v2
        addExact(HttpMethod.POST, "/project/ipd-gate/auto-check-v2", "proj:gate:review");
        // 项目活动流（ProjectActivityController 2 个）
        addExact(HttpMethod.GET, "/project-activity/{var}",              "proj:list");
        addExact(HttpMethod.GET, "/project-activity/suggest-adjustments", "proj:list");
        // 需求-任务 by-project
        addExact(HttpMethod.GET, "/requirement-tasks/by-project/{var}", "req:list");
        // AI 分析（AIController 2 个，sys:ai:list 新 perm 码）
        addPrefix(HttpMethod.POST, "/ai/requirement", "sys:ai:list");
        // 需求池 DELETE
        addExact(HttpMethod.DELETE, "/requirement-pool/{var}", "req:delete");
        // 追溯链接（TraceLinkController 9 个，复用 trace:*）
        addPrefix(HttpMethod.GET,    "/trace-links", "trace:list");
        addPrefix(HttpMethod.POST,   "/trace-links", "trace:create");
        addPrefix(HttpMethod.PUT,    "/trace-links", "trace:create");
        addPrefix(HttpMethod.DELETE, "/trace-links", "trace:delete");
        addExact(HttpMethod.GET, "/trace-links/check-cycle", "trace:list");
        addExact(HttpMethod.GET, "/trace-links/by-pair",     "trace:list");
        // FeatureFlag（前端启动时调用，应公开；放白名单或低权限码）
        addExact(HttpMethod.GET, "/feature/flags", "sys:config:list");
        // R225.2 CONTRACT-004：PUT /gantt/milestones/{id}（更新里程碑）
        addExact(HttpMethod.PUT, "/gantt/milestones/{id}", "proj:update");
        // R225.2 CONTRACT-005：GET /reports/export（报表导出）
        addExact(HttpMethod.GET, "/reports/export", "report:export");
        // R225.2 CONTRACT-006/007：testcases 批量 + 执行历史
        addExact(HttpMethod.POST, "/testcases/batch",        "test:update");
        addExact(HttpMethod.GET,  "/testcases/{id}/executions", "test:list");
        // R225.2 CONTRACT-008：登录日志
        addPrefix(HttpMethod.GET, "/system/login-logs", "audit:read");

        // ===== 前缀路径（CRUD 默认：写操作按动词）=====
        // 需求 (req:*)
        addPrefix(HttpMethod.GET,  "/requirements", "req:list");
        addPrefix(HttpMethod.POST, "/requirements", "req:create");
        addPrefix(HttpMethod.PUT,  "/requirements", "req:update");
        addPrefix(HttpMethod.DELETE,"/requirements", "req:delete");
        // 测试用例 (test:*) — test case 在 rbac 中没有显式 perm，沿用 req:*
        addPrefix(HttpMethod.GET,  "/testcases", "req:list");
        addPrefix(HttpMethod.POST, "/testcases", "req:create");
        addPrefix(HttpMethod.PUT,  "/testcases", "req:update");
        addPrefix(HttpMethod.DELETE,"/testcases", "req:delete");
        // 变更
        addPrefix(HttpMethod.GET,  "/changes", "chg:list");
        addPrefix(HttpMethod.POST, "/changes", "chg:create");
        // 基线
        addPrefix(HttpMethod.GET,  "/baselines", "baseline:list");
        addPrefix(HttpMethod.POST, "/baselines", "baseline:create");
        addExact(HttpMethod.POST, "/baselines/{id}/lock",    "baseline:lock");
        addExact(HttpMethod.POST, "/baselines/{id}/unlock",  "baseline:unlock");
        addExact(HttpMethod.POST, "/baselines/baseline-requirements", "baseline:create");
        // 风险（兜底，前缀）/risk/
        addPrefix(HttpMethod.GET,  "/risk", "risk:list");
        addPrefix(HttpMethod.POST, "/risk", "risk:create");
        addPrefix(HttpMethod.PUT,  "/risk", "risk:update");
        addExact(HttpMethod.PUT,  "/risk/{id}/control",       "risk:control");
        addExact(HttpMethod.PUT,  "/risk/{id}/action-status", "risk:status");
        addExact(HttpMethod.POST, "/risk/{id}/fmea",          "risk:analyze");
        // 风险登记簿
        addPrefix(HttpMethod.GET,  "/risk/register", "risk:list");
        addPrefix(HttpMethod.POST, "/risk/register", "risk:create");
        addPrefix(HttpMethod.PUT,  "/risk/register", "risk:update");
        // 项目
        addPrefix(HttpMethod.GET,  "/projects", "proj:list");
        addPrefix(HttpMethod.POST, "/projects", "proj:create");
        addPrefix(HttpMethod.PUT,  "/projects", "proj:update");
        addPrefix(HttpMethod.DELETE,"/projects", "proj:update");
        // 项目成员
        addPrefix(HttpMethod.GET,  "/project/member", "proj:member");
        addPrefix(HttpMethod.POST, "/project/member", "proj:member");
        addPrefix(HttpMethod.PUT,  "/project/member", "proj:member");
        addPrefix(HttpMethod.DELETE,"/project/member", "proj:member");
        // 追溯
        addPrefix(HttpMethod.GET,  "/traceability", "trace:list");
        addPrefix(HttpMethod.POST, "/traceability", "trace:create");
        addPrefix(HttpMethod.DELETE,"/traceability", "trace:delete");
        // 合规
        addPrefix(HttpMethod.GET,  "/compliance", "compliance:iec62304");
        addPrefix(HttpMethod.POST, "/compliance", "compliance:iec62304");
        // 电子签名
        addPrefix(HttpMethod.GET,  "/esignature", "esign:read");
        addPrefix(HttpMethod.POST, "/esignature", "esign:sign");
        // 通知
        addPrefix(HttpMethod.GET,  "/notifications", "report:dashboard");
        // 追溯图谱
        addPrefix(HttpMethod.GET,  "/trace-graph", "trace:matrix");
        // dashboard / report
        addPrefix(HttpMethod.GET,  "/dashboard", "report:dashboard");
        addPrefix(HttpMethod.GET,  "/reports", "report:stats");
        addPrefix(HttpMethod.POST, "/reports", "report:export");
        // gantt
        addPrefix(HttpMethod.GET,  "/gantt", "proj:list");
        addPrefix(HttpMethod.POST, "/gantt", "proj:update");
        // SOUP 前缀
        addPrefix(HttpMethod.GET,  "/requirement/soup-components", "soup:list");
        addPrefix(HttpMethod.POST, "/requirement/soup-components", "soup:create");
        addPrefix(HttpMethod.PUT,  "/requirement/soup-components", "soup:update");
        addPrefix(HttpMethod.DELETE,"/requirement/soup-components", "soup:update");
        // 需求池
        addPrefix(HttpMethod.GET,  "/requirement-pool", "req:list");
        addPrefix(HttpMethod.POST, "/requirement-pool", "req:create");

        // ===== R199 v1.62: 产品管理 =====
        // 7 个产品端点 + 1 Excel 导出（共 8 条规则）
        // 全部角色可读（list/all/{id}）；写操作按 R197 策略：PD/QA_MGR/PM 可写，ADMIN 全部
        addPrefix(HttpMethod.GET,    "/products",      "product:list");
        addExact(HttpMethod.GET,     "/products/all",  "product:list");
        addExact(HttpMethod.GET,     "/products/{id}", "product:list");
        addExact(HttpMethod.POST,    "/products",      "product:create");
        addExact(HttpMethod.PUT,     "/products/{id}", "product:update");
        addExact(HttpMethod.DELETE,  "/products/{id}", "product:delete");
        addExact(HttpMethod.GET,     "/products/export", "product:export");

        // 排序：精确路径长度降序，确保最长匹配优先
        exactRules.sort(Comparator.comparing((ExactRule r) -> r.pathPattern().length()).reversed());
    }

    private void addExact(HttpMethod m, String path, String perm) {
        exactRules.add(new ExactRule(m, path, perm));
    }

    private void addPrefix(HttpMethod m, String prefix, String perm) {
        prefixRules.add(new PrefixRule(m, prefix, perm));
    }

    /**
     * 解析 URL → 所需 perm。null 表示白名单放行。
     */
    public String resolve(String method, String path) {
        if (method == null || path == null) return null;
        // 去掉 context-path（application.yml: server.servlet.context-path=/api）
        if (path.startsWith("/api/")) {
            path = path.substring(4);
        } else if (path.equals("/api")) {
            path = "/";
        }
        HttpMethod m = HttpMethod.valueOf(method.toUpperCase());

        // 1. 精确路径匹配（Ant 风格 {id} 占位符已转正则）
        for (ExactRule r : exactRules) {
            if (r.method().matches(method) && matchesPath(r.pathPattern(), path)) {
                return r.permCode();
            }
        }
        // 2. 前缀匹配
        for (PrefixRule r : prefixRules) {
            if (r.method().matches(method) && path.startsWith(r.pathPrefix())) {
                return r.permCode();
            }
        }
        // 未匹配：默认需登录，不强制 perm（白名单路径已经由 SecurityConfig permitAll）
        return null;
    }

    /** Ant 风格 {xxx} 占位符 → 正则 ([^/]+) */
    private boolean matchesPath(String pattern, String path) {
        String regex = pattern.replaceAll("\\{[^/]+?}", "[^/]+");
        return path.matches(regex);
    }
}
