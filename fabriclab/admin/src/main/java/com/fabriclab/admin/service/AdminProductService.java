package com.fabriclab.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.admin.entity.Category;
import com.fabriclab.admin.entity.Product;
import com.fabriclab.admin.entity.ProductImage;
import com.fabriclab.admin.entity.SalesOrderItem;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.mapper.CategoryMapper;
import com.fabriclab.admin.mapper.ProductImageMapper;
import com.fabriclab.admin.mapper.ProductMapper;
import com.fabriclab.admin.mapper.SalesOrderItemMapper;
import com.fabriclab.admin.model.ProductForm;
import com.fabriclab.admin.model.ProductPage;
import com.fabriclab.admin.model.ProductQuery;
import com.fabriclab.admin.model.ProductRow;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品维护（UC-A4、FR-A-03、AC-25/AC-27）。款号唯一且创建后不可变、必须归属二级分类、
 * 价格大于 0、状态只接受 DRAFT / ON_SALE；停售用下架表达。
 * 所有改写走 version 条件更新，旧表单保存被拒而不是覆盖新库存（AC-27）。
 */
@Service
public class AdminProductService {

    public static final String DRAFT = "DRAFT";
    public static final String ON_SALE = "ON_SALE";
    public static final String DEFAULT_COVER = "images/logo.png";
    private static final DateTimeFormatter ZH = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String[] MONTHS = {"Jan","Feb","Mar","Apr","May","Jun",
            "Jul","Aug","Sep","Oct","Nov","Dec"};

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductImageMapper imageMapper;
    private final SalesOrderItemMapper itemMapper;
    private final MessageSource messages;

    public AdminProductService(ProductMapper productMapper, CategoryMapper categoryMapper,
                               ProductImageMapper imageMapper, SalesOrderItemMapper itemMapper,
                               MessageSource messages) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.imageMapper = imageMapper;
        this.itemMapper = itemMapper;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    private boolean isEn() {
        return "en".equals(LocaleContextHolder.getLocale().getLanguage());
    }

    public ProductPage page(ProductQuery q) {
        int size = (q.size() == 10 || q.size() == 20 || q.size() == 50) ? q.size() : 20;
        String filter = switch (q.filter()) {
            case "live", "ON_SALE", "draft", "DRAFT", "sold", "low" -> q.filter();
            default -> "all";
        };
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();
        if (!q.kw().isBlank()) {
            String kw = q.kw();
            w.and(x -> x.like(Product::getSku, kw).or().like(Product::getName, kw));
        }
        switch (filter) {
            case "live", "ON_SALE" -> w.eq(Product::getStatus, ON_SALE);
            case "draft", "DRAFT" -> w.eq(Product::getStatus, DRAFT);
            case "sold" -> w.eq(Product::getStatus, ON_SALE).eq(Product::getStock, 0);
            case "low" -> w.eq(Product::getStatus, ON_SALE)
                    .between(Product::getStock, 1, 3);
            default -> { }
        }
        w.orderByDesc(Product::getUpdatedAt).orderByDesc(Product::getId);
        Page<Product> result = productMapper.selectPage(new Page<>(q.page(), size), w);

        Map<Long, String> categoryNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        List<ProductRow> rows = result.getRecords().stream()
                .map(p -> row(p, categoryNames)).collect(Collectors.toList());
        int pages = Math.max(1, (int) ((result.getTotal() + size - 1) / size));
        return new ProductPage(rows, result.getTotal(), q.page(), pages, size,
                count(new LambdaQueryWrapper<>()),
                count(new LambdaQueryWrapper<Product>().eq(Product::getStatus, ON_SALE)),
                count(new LambdaQueryWrapper<Product>().eq(Product::getStatus, DRAFT)),
                count(new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, ON_SALE).eq(Product::getStock, 0)));
    }

    private long count(LambdaQueryWrapper<Product> w) {
        Long n = productMapper.selectCount(w);
        return n == null ? 0L : n;
    }

    private ProductRow row(Product p, Map<Long, String> cats) {
        DescriptionText.Fields f = DescriptionText.parse(p.getDescription());
        int stock = p.getStock() == null ? 0 : p.getStock();
        boolean live = ON_SALE.equals(p.getStatus());
        String label, cls;
        if (!live) { label = t("admin.prod.statusDraft"); cls = ""; }
        else if (stock == 0) { label = t("admin.prod.statusSold"); cls = "tag--end"; }
        else if (stock <= 3) { label = t("admin.prod.statusLow"); cls = "tag--act"; }
        else { label = t("admin.prod.statusLive"); cls = "tag--live"; }
        return new ProductRow(p.getId(), p.getSku(), p.getName(),
                DescriptionText.fabricLine(f, p.getSummary()),
                cats.getOrDefault(p.getCategoryId(), "—"),
                p.getPrice(), stock, p.getStatus(), label, cls,
                date(p.getUpdatedAt()), shape(p.getName()), tone(p.getName()));
    }

    private String date(LocalDateTime at) {
        if (at == null) return "—";
        return isEn() ? at.getDayOfMonth() + " " + MONTHS[at.getMonthValue() - 1] + " " + at.getYear()
                : at.format(ZH);
    }

    /** 服装剪影：按商品名称归类到设计稿的 8 种廓形，仅用于缩略示意。 */
    public static String shape(String name) {
        String n = name == null ? "" : name;
        if (n.contains("围巾")) return "scarf";
        if (n.contains("袜")) return "socks";
        if (n.contains("裙")) return "dress";
        if (n.contains("衬衫") || n.contains("罩衫")) return "shirt";
        if (n.contains("Polo") || n.contains("T 恤") || n.contains("背心")) return "polo";
        if (n.contains("夹克") || n.contains("马甲")) return "jacket";
        if (n.contains("外套") || n.contains("大衣") || n.contains("西装")) return "coat";
        return "turtleneck";
    }

    public static String tone(String name) {
        String n = name == null ? "" : name;
        if (n.contains("墨")) return "#767B74";
        if (n.contains("陶土") || n.contains("棕") || n.contains("驼")) return "#C08A6E";
        return "#D8CFBC";
    }

    public Product require(long id) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new NotFoundException(t("admin.err.prod.notFound"));
        return p;
    }

    public ProductForm blankForm() {
        return new ProductForm(null, "", null, "", "", "", 0, DRAFT, DEFAULT_COVER, 0,
                "", "", "", "", "", 0L);
    }

    public ProductForm form(long id) {
        Product p = require(id);
        DescriptionText.Fields f = DescriptionText.parse(p.getDescription());
        return new ProductForm(p.getId(), p.getSku(), p.getCategoryId(), p.getName(), p.getSummary(),
                p.getPrice() == null ? "" : p.getPrice().setScale(2).toPlainString(),
                p.getStock(), p.getStatus(), p.getCoverImage(), p.getSortOrder(),
                f.fabric(), f.weight(), f.fit(), f.craft(), f.extra(), p.getVersion());
    }

    public Product create(ProductForm f) {
        String sku = text(f.sku(), "admin.err.prod.skuRequired", 40, "admin.err.prod.skuLength");
        String name = text(f.name(), "admin.err.prod.nameRequired", 120, "admin.err.prod.nameLength");
        BigDecimal price = price(f.price());
        String status = status(f.status());
        String cover = cover(f.coverImage());
        int sort = sort(f.sortOrder());
        int stock = f.stock() == null ? 0 : f.stock();
        if (stock < 0) throw new BusinessException(t("admin.err.prod.stockInvalid"));
        requireSecondLevel(f.categoryId());
        if (count(new LambdaQueryWrapper<Product>().eq(Product::getSku, sku)) > 0) {
            throw new BusinessException(t("admin.err.prod.skuExists"));
        }
        Product p = new Product();
        p.setSku(sku); p.setCategoryId(f.categoryId()); p.setName(name);
        p.setSummary(trim(f.summary())); p.setDescription(description(f));
        p.setCoverImage(cover); p.setPrice(price); p.setStock(stock);
        p.setStatus(status); p.setSortOrder(sort); p.setVersion(0L);
        productMapper.insert(p);
        return p;
    }

    /** AC-27：version 条件更新，影响 0 行即为旧表单，拒绝而不是覆盖。 */
    public void update(long id, ProductForm f) {
        Product existing = require(id);
        String sku = text(f.sku(), "admin.err.prod.skuRequired", 40, "admin.err.prod.skuLength");
        String name = text(f.name(), "admin.err.prod.nameRequired", 120, "admin.err.prod.nameLength");
        if (!sku.equals(existing.getSku())) {
            throw new BusinessException(t("admin.err.prod.skuImmutable"));
        }
        if (!Objects.equals(f.categoryId(), existing.getCategoryId())) {
            throw new BusinessException(t("admin.err.prod.categoryImmutable"));
        }
        BigDecimal price = price(f.price());
        String status = status(f.status());
        String cover = cover(f.coverImage());
        int sort = sort(f.sortOrder());
        long expected = f.version() == null ? -1L : f.version();

        LambdaUpdateWrapper<Product> uw = new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .eq(Product::getVersion, expected)
                .set(Product::getName, name)
                .set(Product::getSummary, trim(f.summary()))
                .set(Product::getDescription, description(f))
                .set(Product::getPrice, price)
                .set(Product::getStatus, status)
                .set(Product::getCoverImage, cover)
                .set(Product::getSortOrder, sort)
                .setSql("version = version + 1");
        if (productMapper.update(null, uw) == 0) {
            throw new BusinessException(t("admin.err.prod.versionConflict"));
        }
    }

    /** 上下架：重查当前状态后条件更新，重复操作不覆盖 version。 */
    public void setStatus(long id, String target) {
        Product p = require(id);
        String next = ON_SALE.equalsIgnoreCase(target) ? ON_SALE : DRAFT;
        if (next.equals(p.getStatus())) return;
        LambdaUpdateWrapper<Product> uw = new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id).eq(Product::getStatus, p.getStatus())
                .set(Product::getStatus, next).setSql("version = version + 1");
        productMapper.update(null, uw);
    }

    /**
     * 增量补货（UC-A5、AC-28）：只接受正整数增量，读取最新一行的库存与 version 后条件加算，
     * 期间若被用户端下单、取消或另一次补货改写则整条更新落空并提示重试，不覆盖别人的写入。
     */
    public int restock(long id, Integer delta) {
        if (delta == null || delta <= 0) {
            throw new BusinessException(t("admin.err.stock.deltaInvalid"));
        }
        if (delta > 9999) {
            throw new BusinessException(t("admin.err.stock.deltaTooLarge"));
        }
        Product p = require(id);
        long expected = p.getVersion() == null ? 0L : p.getVersion();
        int base = p.getStock() == null ? 0 : p.getStock();
        LambdaUpdateWrapper<Product> uw = new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .eq(Product::getVersion, expected)
                .setSql("stock = stock + " + delta + ", version = version + 1");
        if (productMapper.update(null, uw) == 0) {
            throw new BusinessException(t("admin.err.stock.conflict"));
        }
        return base + delta;
    }

    /** 只有无订单明细、无相册引用的商品可删；其余用下架表达停售。 */
    public void delete(long id) {
        require(id);
        Long lines = itemMapper.selectCount(new LambdaQueryWrapper<SalesOrderItem>()
                .eq(SalesOrderItem::getProductId, id));
        if (lines != null && lines > 0) {
            throw new BusinessException(t("admin.err.prod.hasOrderItems"));
        }
        Long images = imageMapper.selectCount(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, id));
        if (images != null && images > 0) {
            throw new BusinessException(t("admin.err.prod.hasImages"));
        }
        productMapper.deleteById(id);
    }

    private void requireSecondLevel(Long categoryId) {
        if (categoryId == null) throw new BusinessException(t("admin.err.prod.categoryRequired"));
        Category c = categoryMapper.selectById(categoryId);
        if (c == null) throw new BusinessException(t("admin.err.prod.categoryMissing"));
        if (c.getParentId() == null) {
            throw new BusinessException(t("admin.err.prod.categoryLevel"));
        }
    }

    private String text(String raw, String requiredKey, int max, String lengthKey) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) throw new BusinessException(t(requiredKey));
        if (v.length() > max) throw new BusinessException(t(lengthKey));
        return v;
    }

    private BigDecimal price(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(t("admin.err.prod.priceRequired"));
        }
        try {
            BigDecimal v = new BigDecimal(raw.trim());
            if (v.signum() <= 0 || v.stripTrailingZeros().scale() > 2) {
                throw new BusinessException(t("admin.err.prod.priceInvalid"));
            }
            return v.setScale(2);
        } catch (NumberFormatException e) {
            throw new BusinessException(t("admin.err.prod.priceInvalid"));
        }
    }

    private String status(String raw) {
        String v = raw == null ? DRAFT : raw.trim();
        if (!DRAFT.equals(v) && !ON_SALE.equals(v)) {
            throw new BusinessException(t("admin.err.prod.statusInvalid"));
        }
        return v;
    }

    private String cover(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) throw new BusinessException(t("admin.err.prod.coverRequired"));
        return v.length() > 255 ? v.substring(0, 255) : v;
    }

    private int sort(Integer value) {
        int v = value == null ? 0 : value;
        if (v < 0) throw new BusinessException(t("admin.err.prod.sortNegative"));
        return v;
    }

    private String trim(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private String description(ProductForm f) {
        return DescriptionText.compose(new DescriptionText.Fields(
                f.fabric(), f.weightNote(), f.fitNote(), f.craftNote(), f.descriptionExtra()));
    }
}
