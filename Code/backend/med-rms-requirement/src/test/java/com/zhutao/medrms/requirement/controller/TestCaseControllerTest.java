package com.zhutao.medrms.requirement.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.requirement.domain.entity.TestCase;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.requirement.mapper.TestCaseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * TestCaseController 单元测试（v1.27 R28）
 * 覆盖测试用例列表/创建/更新/删除/覆盖率
 */
@ExtendWith(MockitoExtension.class)
class TestCaseControllerTest {

    @Mock
    private TestCaseMapper testCaseMapper;

    @Mock
    private RequirementMapper requirementMapper;

    @InjectMocks
    private TestCaseController controller;

    @Test
    void list_returnsAll() {
        when(testCaseMapper.selectList(isNull())).thenReturn(Collections.emptyList());

        Result<List<TestCase>> result = controller.list();

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    void getByRequirement_returnsList() {
        when(testCaseMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        Result<List<TestCase>> result = controller.getByRequirement(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void getByProject_returnsList() {
        when(testCaseMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        Result<List<TestCase>> result = controller.getByProject(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void create_assignsTestCaseNo() {
        when(testCaseMapper.selectCount(null)).thenReturn(0L);
        when(testCaseMapper.insert(any(TestCase.class))).thenAnswer(inv -> {
            TestCase arg = inv.getArgument(0);
            arg.setId(1L);
            return 1;
        });

        TestCaseController.CreateTestCaseRequest req = new TestCaseController.CreateTestCaseRequest();
        req.setTitle("T1");
        req.setProjectId(1L);
        req.setRequirementId(1L);

        Result<TestCase> result = controller.create(req);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData().getTestCaseNo());
        assertTrue(result.getData().getTestCaseNo().startsWith("TC-"));
        assertEquals("DRAFT", result.getData().getStatus());
    }

    @Test
    void update_returnsUpdated() {
        TestCase existing = new TestCase();
        existing.setId(1L);
        existing.setTitle("OLD");
        when(testCaseMapper.selectById(1L)).thenReturn(existing);
        when(testCaseMapper.updateById(any(TestCase.class))).thenReturn(1);

        TestCase updates = new TestCase();
        updates.setTitle("NEW");

        Result<TestCase> result = controller.update(1L, updates);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void delete_returnsSuccess() {
        when(testCaseMapper.deleteById(1L)).thenReturn(1);

        Result<Void> result = controller.delete(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void calculateCoverage_zeroTotal_returnsZero() {
        when(testCaseMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        Result<Double> result = controller.calculateCoverage(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(0.0, result.getData());
    }

    @Test
    void calculateCoverage_partialPass() {
        TestCase t1 = new TestCase(); t1.setStatus("PASSED");
        TestCase t2 = new TestCase(); t2.setStatus("FAILED");
        TestCase t3 = new TestCase(); t3.setStatus("PASSED");
        TestCase t4 = new TestCase(); t4.setStatus("PASSED");
        when(testCaseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(t1, t2, t3, t4));

        Result<Double> result = controller.calculateCoverage(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(75.0, result.getData()); // 3/4 * 100
    }
}
