package com.fabriclab.admin.service;

import com.fabriclab.admin.model.AdminModule;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 控制台内容：只呈现当前版本已交付的模块入口与后台范围说明。
 * 不输出任何未经真实统计的数字——订单量、销售额、访客数一律不显示。
 */
@Service
public class ConsoleService {

    private final List<AdminModule> modules = List.of(
            new AdminModule("分类与商品", "/admin/products", "两级分类维护、款式新增与编辑、上下架",
                    "FR-A-02、FR-A-03", false),
            new AdminModule("库存与相册", "/admin/products", "增量补货、平铺 / 上身 / 细节相册",
                    "FR-A-04、FR-A-07", false),
            new AdminModule("账号管理", "/admin/users", "顾客信息修改、口令重置、启用停用",
                    "FR-A-07、指导书后台用户管理", false),
            new AdminModule("订单管理", "/admin/orders", "全站查单、收货三项修改、受限删除、发货",
                    "FR-A-05、FR-A-06", false));

    public List<AdminModule> modules() {
        return modules;
    }
}
