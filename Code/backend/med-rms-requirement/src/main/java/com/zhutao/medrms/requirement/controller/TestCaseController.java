package com.zhutao.medrms.requirement.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.requirement.domain.entity.TestCase;
import com.zhutao.medrms.requirement.mapper.TestCaseMapper;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "测试用例", description = "测试用例CRUD接口")
@RestController
@RequestMapping("/testcases")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseMapper testCaseMapper;
    private final RequirementMapper requirementMapper;

    @Operation(summary = "获取所有测试用例")
    @GetMapping
    public Result<List<TestCase>> list() {
        return Result.success(testCaseMapper.selectList(null));
    }

    @Operation(summary = "获取需求关联的测试用例")
    @GetMapping("/requirement/{requirementId}")
    public Result<List<TestCase>> getByRequirement(@PathVariable Long requirementId) {
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getRequirementId, requirementId);
        return Result.success(testCaseMapper.selectList(wrapper));
    }

    @Operation(summary = "获取项目关联的测试用例")
    @GetMapping("/project/{projectId}")
    public Result<List<TestCase>> getByProject(@PathVariable Long projectId) {
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getProjectId, projectId);
        return Result.success(testCaseMapper.selectList(wrapper));
    }

    @Operation(summary = "创建测试用例")
    @Transactional
    @PostMapping
    @com.zhutao.medrms.common.annotation.AuditLog(eventType = "CREATE", entityType = "TEST_CASE", operation = "创建测试用例")
    public Result<TestCase> create(@RequestBody CreateTestCaseRequest request) {
        // R227.2 DATA-012：编号生成改 MAX 包含软删除
        // R283：selectCount 包 COUNT(MAX(CAST(...))) → 改 selectObjs + 正则过滤纯数字后缀
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TestCase> wrapper =
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TestCase>();
        wrapper.select("MAX(CAST(RIGHT(test_case_no, 6) AS INTEGER))")
              .apply("test_case_no ~ '^TC-[0-9]{6}$'");
        List<Object> result = testCaseMapper.selectObjs(wrapper);
        Long max = (result == null || result.isEmpty() || result.get(0) == null) ? 0L : ((Number) result.get(0)).longValue();
        long next = max + 1;
        TestCase tc = new TestCase();
        tc.setTestCaseNo(String.format("TC-%06d", next));
        tc.setTitle(request.getTitle());
        tc.setTestType(request.getTestType());
        tc.setTestMethod(request.getTestMethod());
        tc.setRequirementId(request.getRequirementId());
        tc.setRequirementNo(request.getRequirementNo());
        tc.setProjectId(request.getProjectId());
        tc.setDescription(request.getDescription());
        tc.setPreCondition(request.getPreCondition());
        tc.setTestSteps(request.getTestSteps());
        tc.setExpectedResult(request.getExpectedResult());
        tc.setSafetyClass(request.getSafetyClass());
        tc.setStatus("DRAFT");
        testCaseMapper.insert(tc);
        return Result.success(tc);
    }

    @Operation(summary = "更新测试用例")
    @PutMapping("/{id}")
    @com.zhutao.medrms.common.annotation.AuditLog(eventType = "MODIFY", entityType = "TEST_CASE", operation = "更新测试用例", entityIdSpel = "#id")
    public Result<TestCase> update(@PathVariable Long id, @RequestBody TestCase testCase) {
        testCase.setId(id);
        testCaseMapper.updateById(testCase);
        return Result.success(testCaseMapper.selectById(id));
    }

    @Operation(summary = "删除测试用例")
    @DeleteMapping("/{id}")
    @com.zhutao.medrms.common.annotation.AuditLog(eventType = "DELETE", entityType = "TEST_CASE", operation = "删除测试用例", entityIdSpel = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        testCaseMapper.deleteById(id);
        return Result.success(null);
    }

    // R225.2 CONTRACT-006：批量操作测试用例（前端 TestCaseList.vue 批量删除/状态更新调用）
    @Operation(summary = "批量操作测试用例")
    @PostMapping("/batch")
    public Result<Map<String, Object>> batchOperation(@RequestBody BatchRequest request) {
        if (request.getIds() == null || request.getIds().isEmpty()) {
            return Result.error("SY0101", "IDs 不能为空");
        }
        String action = request.getAction() == null ? "" : request.getAction().toUpperCase();
        int affected = 0;
        switch (action) {
            case "DELETE":
                affected = testCaseMapper.deleteBatchIds(request.getIds());
                break;
            case "UPDATE_STATUS":
                String newStatus = request.getStatus() == null ? "DRAFT" : request.getStatus();
                affected = testCaseMapper.updateStatusBatch(request.getIds(), newStatus);
                break;
            default:
                return Result.error("SY0101", "未知 action: " + action + "（支持 DELETE / UPDATE_STATUS）");
        }
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("affected", affected);
        result.put("action", action);
        return Result.success(result);
    }

    // R225.2 CONTRACT-007：获取测试用例执行历史（前端 TestCaseList.vue 调用）
    @Operation(summary = "获取测试用例执行历史")
    @GetMapping("/{id}/executions")
    public Result<java.util.List<Map<String, Object>>> getExecutions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit) {
        // 当前没有 t_test_execution 表，返回空列表占位
        // 完整实现需要新建执行历史表 + Service
        java.util.List<Map<String, Object>> executions = new java.util.ArrayList<>();
        return Result.success(executions);
    }

    @Operation(summary = "更新测试用例状态")
    @PutMapping("/{id}/status")
    public Result<TestCase> updateStatus(@PathVariable Long id, @RequestParam String status) {
        TestCase testCase = testCaseMapper.selectById(id);
        testCase.setStatus(status);
        testCaseMapper.updateById(testCase);
        return Result.success(testCase);
    }

    @Operation(summary = "计算需求覆盖率")
    @GetMapping("/coverage/{requirementId}")
    public Result<Double> calculateCoverage(@PathVariable Long requirementId) {
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getRequirementId, requirementId);
        List<TestCase> testCases = testCaseMapper.selectList(wrapper);
        int total = testCases.size();
        int passed = (int) testCases.stream().filter(tc -> "PASSED".equals(tc.getStatus())).count();
        double coverage = total > 0 ? (double) passed / total * 100 : 0;
        return Result.success(Math.round(coverage * 100) / 100.0);
    }

    @lombok.Data
    public static class CreateTestCaseRequest {
        private String title;
        private String testType;
        private String testMethod;
        private Long requirementId;
        private String requirementNo;
        private Long projectId;
        private String description;
        private String preCondition;
        private String testSteps;
        private String expectedResult;
        private String safetyClass;
    }

    // R225.2 CONTRACT-006：批量操作请求体
    @lombok.Data
    public static class BatchRequest {
        private java.util.List<Long> ids;
        private String action;        // DELETE / UPDATE_STATUS
        private String status;        // 仅 UPDATE_STATUS 需要
    }
}