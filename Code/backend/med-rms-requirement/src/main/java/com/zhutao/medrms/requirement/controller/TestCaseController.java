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
    public Result<TestCase> create(@RequestBody CreateTestCaseRequest request) {
        long count = testCaseMapper.selectCount(null);
        TestCase tc = new TestCase();
        tc.setTestCaseNo(String.format("TC-%06d", count + 1));
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
    public Result<TestCase> update(@PathVariable Long id, @RequestBody TestCase testCase) {
        testCase.setId(id);
        testCaseMapper.updateById(testCase);
        return Result.success(testCaseMapper.selectById(id));
    }

    @Operation(summary = "删除测试用例")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        testCaseMapper.deleteById(id);
        return Result.success(null);
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
}