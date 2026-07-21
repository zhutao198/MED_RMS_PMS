package com.zhutao.medrms.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhutao.medrms.product.domain.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /** 按产品线筛选 active 产品 */
    List<Product> selectActiveByLine(@Param("productLine") String productLine);
}