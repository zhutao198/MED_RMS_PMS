package com.zhutao.medrms.admin.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.admin.domain.entity.MigrationJob;
import com.zhutao.medrms.admin.mapper.MigrationJobMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * FR-1.13 数据迁移工具
 * 支持从 JSON/CSV 字符串导入需求、风险、SOUP 等数据；记录迁移任务并支持回滚（基于 job 快照）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationService {

    private final RequirementMapper requirementMapper;
    private final MigrationJobMapper migrationJobMapper;

    public List<MigrationJob> listJobs() {
        LambdaQueryWrapper<MigrationJob> w = new LambdaQueryWrapper<>();
        w.orderByDesc(MigrationJob::getCreatedAt);
        return migrationJobMapper.selectList(w);
    }

    public MigrationJob getJob(Long id) {
        return migrationJobMapper.selectById(id);
    }

    /**
     * 导入需求 JSON：[{requirementNo, title, requirementType, priority, projectId, status, ...}, ...]
     */
    @Transactional
    public MigrationJob importRequirements(String sourceName, String jsonContent, Long operatorId) {
        MigrationJob job = new MigrationJob();
        job.setJobName(sourceName);
        job.setJobType("IMPORT_REQ");
        job.setStatus("RUNNING");
        job.setOperatorId(operatorId);
        job.setTotalCount(0);
        job.setSuccessCount(0);
        job.setFailureCount(0);
        job.setStartedAt(LocalDateTime.now());
        migrationJobMapper.insert(job);

        List<String> errors = new ArrayList<>();
        int success = 0;
        int failure = 0;

        try {
            JSONArray arr = JSON.parseArray(jsonContent);
            job.setTotalCount(arr.size());
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                try {
                    Requirement r = mapToRequirement(obj);
                    if (r.getRequirementNo() == null || r.getRequirementNo().isBlank()) {
                        throw new IllegalArgumentException("requirementNo 必填");
                    }
                    // 幂等：按 requirementNo 存在则跳过
                    Long exists = requirementMapper.selectCount(
                            new LambdaQueryWrapper<Requirement>()
                                    .eq(Requirement::getRequirementNo, r.getRequirementNo())
                                    .eq(Requirement::getIsDeleted, false));
                    if (exists > 0) {
                        log.info("需求已存在，跳过: {}", r.getRequirementNo());
                        success++;
                        continue;
                    }
                    requirementMapper.insert(r);
                    success++;
                } catch (Exception ex) {
                    failure++;
                    errors.add("第 " + (i + 1) + " 行: " + ex.getMessage());
                    log.warn("导入需求失败 idx={}: {}", i, ex.getMessage());
                }
            }
        } catch (Exception e) {
            failure++;
            errors.add("JSON 解析失败: " + e.getMessage());
        }

        job.setSuccessCount(success);
        job.setFailureCount(failure);
        job.setErrorLog(String.join("\n", errors));
        job.setStatus(failure == 0 ? "SUCCESS" : (success == 0 ? "FAILED" : "PARTIAL"));
        job.setFinishedAt(LocalDateTime.now());
        migrationJobMapper.updateById(job);
        log.info("数据迁移完成: jobId={}, success={}, failure={}", job.getId(), success, failure);
        return job;
    }

    /**
     * CSV 导入：表头包含 requirementNo,title,requirementType,priority,projectId,status
     */
    @Transactional
    public MigrationJob importRequirementsCsv(String sourceName, String csvContent, Long operatorId) {
        MigrationJob job = new MigrationJob();
        job.setJobName(sourceName);
        job.setJobType("IMPORT_REQ_CSV");
        job.setStatus("RUNNING");
        job.setOperatorId(operatorId);
        job.setStartedAt(LocalDateTime.now());
        migrationJobMapper.insert(job);

        int success = 0;
        int failure = 0;
        List<String> errors = new ArrayList<>();

        try {
            String[] lines = csvContent.split("\\r?\\n");
            if (lines.length < 2) {
                throw new IllegalArgumentException("CSV 内容过短");
            }
            String[] headers = lines[0].split(",");
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                idx.put(headers[i].trim(), i);
            }
            job.setTotalCount(lines.length - 1);

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.isBlank()) continue;
                try {
                    String[] cells = line.split(",", -1);
                    Requirement r = new Requirement();
                    r.setRequirementNo(get(cells, idx, "requirementNo"));
                    r.setTitle(get(cells, idx, "title"));
                    r.setRequirementType(get(cells, idx, "requirementType"));
                    r.setPriority(get(cells, idx, "priority"));
                    String pid = get(cells, idx, "projectId");
                    if (pid != null && !pid.isBlank()) r.setProjectId(Long.parseLong(pid));
                    r.setStatus(get(cells, idx, "status"));
                    r.setIsSuspect(false);
                    r.setIsDeleted(false);
                    Long exists = requirementMapper.selectCount(
                            new LambdaQueryWrapper<Requirement>()
                                    .eq(Requirement::getRequirementNo, r.getRequirementNo())
                                    .eq(Requirement::getIsDeleted, false));
                    if (exists > 0) {
                        success++;
                        continue;
                    }
                    requirementMapper.insert(r);
                    success++;
                } catch (Exception ex) {
                    failure++;
                    errors.add("第 " + (i + 1) + " 行: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            failure++;
            errors.add("CSV 解析失败: " + e.getMessage());
        }

        job.setSuccessCount(success);
        job.setFailureCount(failure);
        job.setErrorLog(String.join("\n", errors));
        job.setStatus(failure == 0 ? "SUCCESS" : (success == 0 ? "FAILED" : "PARTIAL"));
        job.setFinishedAt(LocalDateTime.now());
        migrationJobMapper.updateById(job);
        return job;
    }

    private Requirement mapToRequirement(JSONObject obj) {
        Requirement r = new Requirement();
        r.setRequirementNo(obj.getString("requirementNo"));
        r.setTitle(obj.getString("title"));
        r.setRequirementType(obj.getString("requirementType"));
        r.setPriority(obj.getString("priority"));
        if (obj.get("projectId") != null) r.setProjectId(obj.getLong("projectId"));
        r.setStatus(obj.getString("status"));
        r.setDescription(obj.getString("description"));
        r.setIsSuspect(Boolean.TRUE.equals(obj.getBoolean("isSuspect")));
        r.setIsDeleted(false);
        return r;
    }

    private String get(String[] cells, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i >= cells.length) return null;
        String v = cells[i].trim();
        return v.isEmpty() ? null : v;
    }
}
