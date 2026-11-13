package com.fabriclab.store.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.service.BrandService;
import com.fabriclab.store.service.CartService;
import com.fabriclab.store.service.CurrentUserService;
import com.fabriclab.store.service.ProductService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * 购物车页与写操作（FR-C-01、FR-C-02）：本人车从服务端会话身份取得，
 * 不接受表单自报用户。加入失败回商品页、修改与移除失败回购物车页，
 * 通过会话级提示清楚指明原因；成功写操作后重定向展示最新全车。
 * 游客访问 /cart 被访问检查拦截，先登录再返回。
 */
@Controller
public class CartController {

    /** 购物袋页推荐位条数，取最近上架的在售商品，不参与结算。 */
    private static final int RECOMMEND_LIMIT = 4;

    private final BrandService brandService;
    private final CartService cartService;
    private final CurrentUserService currentUserService;
    private final ProductMapper productMapper;

    public CartController(BrandService brandService, CartService cartService,
                          CurrentUserService currentUserService, ProductMapper productMapper) {
        this.brandService = brandService;
        this.cartService = cartService;
        this.currentUserService = currentUserService;
        this.productMapper = productMapper;
    }

    @GetMapping("/cart")
    public String cart(Model model, HttpSession session) {
        CurrentUser current = requireCurrent(session);
        if (current == null) {
            return "redirect:/signon";
        }
        fillCommon(model);
        model.addAttribute("view", cartService.view(current.id()));
        return "cart";
    }

    /** 加入购物车：成功回商品页反馈合并数量；失败回原页并保留原车。 */
    @PostMapping("/cart/add")
    public String add(@RequestParam(required = false) String productId,
                      @RequestParam(required = false) String quantity,
                      HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = requireCurrent(session);
        if (current == null) {
            return "redirect:/signon";
        }
        String fallback = fallbackProductPage(productId);
        try {
            int merged = cartService.addToCart(current.id(), productId, quantity);
            redirect.addFlashAttribute("cartAdded", merged);
            return "redirect:/product?id=" + productId.trim();
        } catch (BusinessException e) {
            redirect.addFlashAttribute("cartError", e.getMessage());
            return "redirect:" + fallback;
        }
    }

    /** 绝对修改数量：成功回购物车页；旧版本或非法输入被拒且不覆盖当前车。 */
    @PostMapping("/cart/update")
    public String update(@RequestParam(required = false) String itemId,
                         @RequestParam(required = false) String version,
                         @RequestParam(required = false) String quantity,
                         HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = requireCurrent(session);
        if (current == null) {
            return "redirect:/signon";
        }
        try {
            cartService.updateQuantity(current.id(), itemId, version, quantity);
        } catch (BusinessException e) {
            redirect.addFlashAttribute("cartError", e.getMessage());
        }
        return "redirect:/cart";
    }

    /** 移除条目：失效与有效条目均可移除；旧版本被拒。 */
    @PostMapping("/cart/remove")
    public String remove(@RequestParam(required = false) String itemId,
                         @RequestParam(required = false) String version,
                         HttpSession session, RedirectAttributes redirect) {
        CurrentUser current = requireCurrent(session);
        if (current == null) {
            return "redirect:/signon";
        }
        try {
            cartService.remove(current.id(), itemId, version);
        } catch (BusinessException e) {
            redirect.addFlashAttribute("cartError", e.getMessage());
        }
        return "redirect:/cart";
    }

    private String fallbackProductPage(String productId) {
        if (productId != null) {
            try {
                Long.parseLong(productId.trim());
                return "/product?id=" + productId.trim();
            } catch (NumberFormatException ignored) {
                // 非整数商品 ID 无法定位来源商品页，回首页提示
            }
        }
        return "/main";
    }

    private CurrentUser requireCurrent(HttpSession session) {
        return currentUserService.from(session);
    }

    private void fillCommon(Model model) {
        model.addAttribute("brand", brandService.brand());
        model.addAttribute("tagline", brandService.tagline());
        model.addAttribute("recommendations", recommend());
        model.addAttribute("onSaleTotal", onSaleTotal());
    }

    /** 在售商品总数：推荐位标题里的件数取真实值。 */
    private long onSaleTotal() {
        Long total = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductService.STATUS_ON_SALE));
        return total == null ? 0L : total;
    }

    /** 推荐位：在售且有库存的商品按上架顺序取前四条，取不到时给空列表由模板跳过。 */
    private List<Product> recommend() {
        List<Product> rows = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductService.STATUS_ON_SALE)
                .gt(Product::getStock, 0)
                .orderByDesc(Product::getId)
                .last("LIMIT " + RECOMMEND_LIMIT));
        return rows == null ? List.of() : rows;
    }
}
