package com.fabriclab.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fabriclab.store.entity.SalesOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrder> {
}
