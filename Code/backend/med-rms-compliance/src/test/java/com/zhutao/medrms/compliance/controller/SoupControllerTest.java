package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.compliance.domain.entity.SoupComponent;
import com.zhutao.medrms.compliance.service.SoupComponentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoupControllerTest {

    @Mock
    private SoupComponentService soupComponentService;

    @InjectMocks
    private SoupController soupController;

    @Test
    void testListSoupComponents() {
        when(soupComponentService.list(isNull(), isNull())).thenReturn(List.of());

        var result = soupController.list(null, null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void testListSoupComponentsWithFilters() {
        when(soupComponentService.list("ACTIVE", "HIGH")).thenReturn(List.of());

        var result = soupController.list("ACTIVE", "HIGH");

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void testGetSoupComponentById() {
        SoupComponent component = new SoupComponent();
        component.setId(1L);
        component.setComponentName("Test");
        when(soupComponentService.getById(1L)).thenReturn(component);

        var result = soupController.getById(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }
}