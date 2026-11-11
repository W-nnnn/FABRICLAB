package com.fabriclab.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabriclab.store.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
