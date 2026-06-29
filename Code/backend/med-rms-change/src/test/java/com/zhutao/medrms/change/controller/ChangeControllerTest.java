package com.zhutao.medrms.change.controller;

import com.zhutao.medrms.change.service.ChangeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeControllerTest {

    @Mock
    private ChangeService changeService;

    @InjectMocks
    private ChangeController changeController;

    @Test
    void testCreateChangeRequest() {
        var req = new ChangeController.CreateChangeRequest();
        req.setRequirementId(1L);
        req.setChangeType("CORRECTIVE");
        req.setReason("测试原因");
        req.setUrgency("NORMAL");
        req.setRequestedBy(1L);
        req.setTitle("测试变更");

        var result = changeController.createChangeRequest(req);
        assertNotNull(result);
    }
}