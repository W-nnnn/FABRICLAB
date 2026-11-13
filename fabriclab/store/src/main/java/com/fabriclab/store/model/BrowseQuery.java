package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;

/**
 * 商品列表查询参数解析与归一。
 * 页码缺省 1、小于 1 的整数归一为 1，非整数或溢出返回参数错误；
 * 页大小缺省 24，仅允许 1—50 的整数；
 * 关键字去首尾空白、最长 120 字符、空白等价于不搜索，不静默截断；
 * productType 仅接受 ALL / GARMENTS / ACCESSORIES，sort 仅接受 DEFAULT / PRICE_ASC / PRICE_DESC / NEWEST，非法值返回参数错误。
 */
public class BrowseQuery {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 24;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 50;
    public static final int MAX_KEYWORD_LENGTH = 120;

    private final Long categoryId;
    private final CategoryType productType;
    private final boolean productTypeExplicit;
    private final SortOption sort;
    private final String keyword;
    private final int page;
    private final int size;

    private BrowseQuery(Long categoryId, CategoryType productType, boolean productTypeExplicit,
                        SortOption sort, String keyword, int page, int size) {
        this.categoryId = categoryId;
        this.productType = productType;
        this.productTypeExplicit = productTypeExplicit;
        this.sort = sort;
        this.keyword = keyword;
        this.page = page;
        this.size = size;
    }

    public static BrowseQuery parse(String category, String productType, String sort,
                                    String keyword, String page, String size) {
        return new BrowseQuery(
                parseCategoryId(category),
                parseProductType(productType),
                isPresent(productType),
                parseSort(sort),
                parseKeyword(keyword),
                parsePage(page),
                parseSize(size));
    }

    private static Long parseCategoryId(String raw) {
        if (!isPresent(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("分类参数必须是整数 ID");
        }
    }

    private static CategoryType parseProductType(String raw) {
        if (!isPresent(raw)) {
            return CategoryType.ALL;
        }
        return switch (raw) {
            case "ALL" -> CategoryType.ALL;
            case "GARMENTS" -> CategoryType.GARMENTS;
            case "ACCESSORIES" -> CategoryType.ACCESSORIES;
            default -> throw new BusinessException("商品类型只接受 ALL、GARMENTS、ACCESSORIES");
        };
    }

    private static SortOption parseSort(String raw) {
        if (!isPresent(raw)) {
            return SortOption.DEFAULT;
        }
        return switch (raw) {
            case "DEFAULT" -> SortOption.DEFAULT;
            case "PRICE_ASC" -> SortOption.PRICE_ASC;
            case "PRICE_DESC" -> SortOption.PRICE_DESC;
            case "NEWEST" -> SortOption.NEWEST;
            default -> throw new BusinessException("排序方式只接受 DEFAULT、PRICE_ASC、PRICE_DESC、NEWEST");
        };
    }

    private static String parseKeyword(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException("搜索关键字最长 " + MAX_KEYWORD_LENGTH + " 字符");
        }
        return trimmed;
    }

    private static int parsePage(String raw) {
        if (!isPresent(raw)) {
            return DEFAULT_PAGE;
        }
        long value = parseNumber(raw, "页码");
        if (value < DEFAULT_PAGE) {
            return DEFAULT_PAGE;
        }
        if (value > Integer.MAX_VALUE) {
            throw new BusinessException("页码超出范围");
        }
        return (int) value;
    }

    private static int parseSize(String raw) {
        if (!isPresent(raw)) {
            return DEFAULT_SIZE;
        }
        long value = parseNumber(raw, "页大小");
        if (value < MIN_SIZE || value > MAX_SIZE) {
            throw new BusinessException("页大小只允许 " + MIN_SIZE + "—" + MAX_SIZE + " 的整数");
        }
        return (int) value;
    }

    private static long parseNumber(String raw, String field) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(field + "必须是整数");
        }
    }

    private static boolean isPresent(String raw) {
        return raw != null && !raw.isBlank();
    }

    public Long categoryId() {
        return categoryId;
    }

    public CategoryType productType() {
        return productType;
    }

    public boolean productTypeExplicit() {
        return productTypeExplicit;
    }

    public SortOption sort() {
        return sort;
    }

    public String keyword() {
        return keyword;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    /** 分页与筛选链接用：未显式选择类型时不携带该参数，保证二级分类入口的确定性。 */
    public String productTypeParam() {
        return productTypeExplicit ? productType.name() : null;
    }
}
