package com.zhutao.medrms.risk.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.risk.domain.entity.RiskMatrix;
import com.zhutao.medrms.risk.service.RiskMatrixService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/risk/matrix")
@RequiredArgsConstructor
public class RiskMatrixController {

    private final RiskMatrixService riskMatrixService;

    @GetMapping("/list/{projectId}")
    public Result<List<RiskMatrix>> listByProject(@PathVariable Long projectId) {
        return Result.success(riskMatrixService.listByProject(projectId));
    }

    @GetMapping("/list")
    public Result<List<RiskMatrix>> listByType(@RequestParam String matrixType) {
        return Result.success(riskMatrixService.listByType(matrixType));
    }

    @GetMapping("/{id}")
    public Result<RiskMatrix> getById(@PathVariable Long id) {
        return Result.success(riskMatrixService.getById(id));
    }

    @PostMapping
    public Result<RiskMatrix> create(@RequestBody RiskMatrix matrix) {
        return Result.success(riskMatrixService.create(matrix));
    }

    @PutMapping("/{id}")
    public Result<RiskMatrix> update(@PathVariable Long id, @RequestBody RiskMatrix updates) {
        return Result.success(riskMatrixService.update(id, updates));
    }

    @PostMapping("/{id}/residual")
    public Result<RiskMatrix> calculateResidual(@PathVariable Long id,
                                                 @RequestParam String mitigationMeasure) {
        return Result.success(riskMatrixService.calculateResidual(id, mitigationMeasure));
    }
}