package com.zhutao.medrms.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.admin.domain.entity.DictItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DictItemMapper extends BaseMapper<DictItem> {

    @Select("SELECT id, dict_type, item_code AS dict_code, item_name AS dict_name, sort_order, is_deleted, created_at, updated_at FROM sys_schema.t_dict_item WHERE dict_type = #{dictType} AND is_deleted = false ORDER BY sort_order")
    List<DictItem> selectByType(@Param("dictType") String dictType);

    @Select("SELECT id, dict_type, item_code AS dict_code, item_name AS dict_name, sort_order, is_deleted, created_at, updated_at FROM sys_schema.t_dict_item WHERE is_deleted = false ORDER BY dict_type, sort_order")
    List<DictItem> selectAll();
}