package com.fabriclab.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.admin.entity.Product;
import com.fabriclab.admin.entity.SalesOrder;
import com.fabriclab.admin.mapper.ProductMapper;
import com.fabriclab.admin.mapper.SalesOrderMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 顶栏通知的真实计数：待发货笔数与低库存款数都来自当次 COUNT。
 * 数据源不存在（切片测试）时给 0，不伪造数字。
 */
@ControllerAdvice
public class AdminBadgeAdvice {

    private final ObjectProvider<ProductMapper> productMapper;
    private final ObjectProvider<SalesOrderMapper> orderMapper;

    public AdminBadgeAdvice(ObjectProvider<ProductMapper> productMapper,
                            ObjectProvider<SalesOrderMapper> orderMapper) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
    }

    @ModelAttribute
    public void badges(Model model) {
        long low = 0;
        long ship = 0;
        ProductMapper pm = productMapper.getIfAvailable();
        if (pm != null) {
            Long n = pm.selectCount(new LambdaQueryWrapper<Product>()
                    .eq(Product::getStatus, "ON_SALE")
                    .between(Product::getStock, 1, 3));
            low = n == null ? 0 : n;
        }
        SalesOrderMapper om = orderMapper.getIfAvailable();
        if (om != null) {
            Long n = om.selectCount(new LambdaQueryWrapper<SalesOrder>()
                    .eq(SalesOrder::getStatus, "PAID"));
            ship = n == null ? 0 : n;
        }
        model.addAttribute("shipCount", ship);
        model.addAttribute("lowCount", low);
        model.addAttribute("badgeCount", ship + low);
    }
}
