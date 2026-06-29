package com.zhutao.medrms.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.project.domain.entity.TaskPredecessor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskPredecessorMapper extends BaseMapper<TaskPredecessor> {

    @Select("SELECT * FROM prj_schema.t_task_predecessor WHERE task_id = #{taskId}")
    List<TaskPredecessor> selectByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT * FROM prj_schema.t_task_predecessor WHERE predecessor_id = #{predecessorId}")
    List<TaskPredecessor> selectByPredecessorId(@Param("predecessorId") Long predecessorId);

    @Delete("DELETE FROM prj_schema.t_task_predecessor WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") Long taskId);
}
