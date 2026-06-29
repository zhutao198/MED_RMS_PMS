package com.zhutao.medrms.admin.controller;

import com.zhutao.medrms.admin.domain.entity.MigrationJob;
import com.zhutao.medrms.admin.service.DataMigrationService;
import com.zhutao.medrms.common.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MigrationController 单元测试（W14-D1）
 * FR-1.13 数据迁移 Controller
 */
@ExtendWith(MockitoExtension.class)
class MigrationControllerTest {

    @Mock private DataMigrationService dataMigrationService;

    @InjectMocks private MigrationController controller;

    private MigrationJob newJob() {
        MigrationJob job = new MigrationJob();
        job.setId(1L);
        job.setJobName("import.json");
        return job;
    }

    @Test
    @DisplayName("listJobs-查询迁移任务列表")
    void listJobs() {
        when(dataMigrationService.listJobs()).thenReturn(List.of(newJob()));

        Result<List<MigrationJob>> result = controller.listJobs();

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    @DisplayName("getJob-查询任务详情")
    void getJob() {
        when(dataMigrationService.getJob(1L)).thenReturn(newJob());

        Result<MigrationJob> result = controller.getJob(1L);

        assertEquals(1L, result.getData().getId());
    }

    @Test
    @DisplayName("importRequirementsJson-导入 JSON 文本")
    void importRequirementsJson() {
        when(dataMigrationService.importRequirements(anyString(), anyString(), any()))
                .thenReturn(newJob());

        MigrationController.MigrationJsonRequest req = new MigrationController.MigrationJsonRequest();
        req.setSourceName("import.json");
        req.setContent("{\"requirements\":[]}");
        req.setOperatorId(100L);

        Result<MigrationJob> result = controller.importRequirementsJson(req);

        assertEquals(200, result.getCode());
        verify(dataMigrationService).importRequirements("import.json", "{\"requirements\":[]}", 100L);
    }

    @Test
    @DisplayName("uploadJson-上传 JSON 文件")
    void uploadJson() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file", "req.json", "application/json",
                "[{\"id\":1}]".getBytes());
        when(dataMigrationService.importRequirements(anyString(), anyString(), any()))
                .thenReturn(newJob());

        Result<MigrationJob> result = controller.uploadJson(file, "upload", 100L);

        assertEquals(200, result.getCode());
        verify(dataMigrationService).importRequirements(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("uploadCsv-上传 CSV 文件")
    void uploadCsv() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file", "req.csv", "text/csv",
                "id,title\n1,Test\n".getBytes());
        when(dataMigrationService.importRequirementsCsv(anyString(), anyString(), any()))
                .thenReturn(newJob());

        Result<MigrationJob> result = controller.uploadCsv(file, "csv-import", 100L);

        assertEquals(200, result.getCode());
        verify(dataMigrationService).importRequirementsCsv(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("uploadJson-sourceName 为空时使用文件名")
    void uploadJson_useFilename() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file", "auto-named.json", "application/json",
                "[]".getBytes());
        when(dataMigrationService.importRequirements(anyString(), anyString(), any()))
                .thenReturn(newJob());

        Result<MigrationJob> result = controller.uploadJson(file, null, 100L);

        assertEquals(200, result.getCode());
    }
}
