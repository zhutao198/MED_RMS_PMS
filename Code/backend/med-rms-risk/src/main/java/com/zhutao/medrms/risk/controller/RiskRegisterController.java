package com.zhutao.medrms.risk.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.risk.domain.entity.RiskRegister;
import com.zhutao.medrms.risk.service.RiskRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/risk/register")
@RequiredArgsConstructor
public class RiskRegisterController {

    private final RiskRegisterService riskRegisterService;

    @GetMapping("/list")
    public Result<List<RiskRegister>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String category,
                                          // R109 G3 修复：可选 projectId 参数（null = 不过滤）
                                          @RequestParam(required = false) Long projectId) {
        return Result.success(riskRegisterService.list(status, category, projectId));
    }

    @GetMapping("/{id}")
    public Result<RiskRegister> getById(@PathVariable Long id) {
        return Result.success(riskRegisterService.getById(id));
    }

    @PostMapping
    public Result<RiskRegister> create(@RequestBody RiskRegister risk) {
        return Result.success(riskRegisterService.create(risk));
    }

    @PutMapping("/{id}")
    public Result<RiskRegister> update(@PathVariable Long id, @RequestBody RiskRegister updates) {
        return Result.success(riskRegisterService.update(id, updates));
    }

    @PostMapping("/{id}/close")
    public Result<RiskRegister> close(@PathVariable Long id, @RequestParam String closureNote) {
        return Result.success(riskRegisterService.close(id, closureNote));
    }

    @PostMapping("/{id}/accept")
    public Result<RiskRegister> acceptRisk(@PathVariable Long id) {
        return Result.success(riskRegisterService.acceptRisk(id));
    }
}