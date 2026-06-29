package com.zhutao.medrms.project.controller;

import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.project.domain.entity.ProjectMember;
import com.zhutao.medrms.project.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/project/member")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService memberService;

    @GetMapping("/list/{projectId}")
    public Result<List<ProjectMember>> listByProject(@PathVariable Long projectId) {
        return Result.success(memberService.listByProject(projectId));
    }

    @GetMapping("/{id}")
    public Result<ProjectMember> getById(@PathVariable Long id) {
        return Result.success(memberService.getById(id));
    }

    @PostMapping
    public Result<ProjectMember> addMember(@RequestBody ProjectMember member) {
        return Result.success(memberService.addMember(member));
    }

    @PutMapping("/{id}")
    public Result<ProjectMember> updateMember(@PathVariable Long id, @RequestBody ProjectMember updates) {
        return Result.success(memberService.updateMember(id, updates));
    }

    @DeleteMapping("/{id}")
    public Result<Void> removeMember(@PathVariable Long id) {
        memberService.removeMember(id);
        return Result.success();
    }

    @PostMapping("/{id}/switch-role")
    public Result<ProjectMember> switchRole(@PathVariable Long id, @RequestParam String role) {
        return Result.success(memberService.switchRole(id, role));
    }
}