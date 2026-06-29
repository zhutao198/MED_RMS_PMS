package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.compliance.domain.entity.RegulatoryMapping;
import com.zhutao.medrms.compliance.mapper.RegulatoryMappingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatoryMappingService {

    private final RegulatoryMappingMapper regulatoryMappingMapper;

    public List<RegulatoryMapping> listByProjectId(Long projectId) {
        return regulatoryMappingMapper.selectByProjectId(projectId);
    }

    public List<RegulatoryMapping> listByRegulationType(String regulationType) {
        return regulatoryMappingMapper.selectByRegulationType(regulationType);
    }

    public IPage<RegulatoryMapping> listByProjectIdAndRegulationType(Long projectId, String regulationType, int page, int size) {
        Page<RegulatoryMapping> pageObj = new Page<>(page, size);
        return regulatoryMappingMapper.selectPage(pageObj, null);
    }

    @Transactional
    public RegulatoryMapping create(RegulatoryMapping mapping) {
        regulatoryMappingMapper.insert(mapping);
        log.info("创建法规映射: id={}, regulationType={}", mapping.getId(), mapping.getRegulationType());
        return mapping;
    }

    @Transactional
    public RegulatoryMapping update(Long id, RegulatoryMapping mapping) {
        mapping.setId(id);
        regulatoryMappingMapper.updateById(mapping);
        log.info("更新法规映射: id={}", id);
        return mapping;
    }

    @Transactional
    public void delete(Long id) {
        regulatoryMappingMapper.deleteById(id);
        log.info("删除法规映射: id={}", id);
    }
}