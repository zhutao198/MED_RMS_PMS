package com.zhutao.medrms.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.project.domain.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * R222.3 修复：task_no 生成需统计「所有」记录（含逻辑删除）。
     * MyBatis-Plus 全局逻辑删除会自动给 MP 生成的查询注入 is_deleted=false，
     * 导致 MAX 仅统计未删编号，生成的 task_no 会与已软删但 UNIQUE 仍占用的编号冲突。
     * 用自定义原生 SQL 绕过逻辑删除拦截器。
     */
    @Select("SELECT MAX(CAST(RIGHT(task_no, LENGTH(task_no) - 5) AS INTEGER)) " +
            "FROM proj_schema.t_task WHERE task_no LIKE 'TASK-%' AND task_no IS NOT NULL")
    Integer selectMaxTaskNoSuffix();
}