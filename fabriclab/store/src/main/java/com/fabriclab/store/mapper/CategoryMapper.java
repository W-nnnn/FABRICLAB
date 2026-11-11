package com.fabriclab.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabriclab.store.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
