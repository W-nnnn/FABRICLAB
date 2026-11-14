package com.fabriclab.admin.service;

import com.fabriclab.admin.model.AdminModule;
import org.springframework.context.MessageSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 控制台内容（FR-A-01、NFR-11）：全部数字来自当次 SQL 的 COUNT，
 * 陈列位与最近上架来自同一份 product 查询。不输出销售额、访客数、转化率等
 * 任何未经真实统计的指标；没有数据时不填示例数字。
 */
@Service
public class ConsoleService {

    public record Featured(String sku, String name, String summary, BigDecimal price,
                           int stock, String status, String statusLabel, String shape) {}
    public record Recent(long id, String sku, String name, String status, String time, boolean live) {}
    public record Need(String label, String title, String detail, String href, boolean accent) {}
    public record Dashboard(Featured featured, long live, long draft, long low, long ship,
                            List<Need> needs, List<Recent> recent, String today) {}

    private static final DateTimeFormatter ZH_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String[] MONTHS = {"Jan","Feb","Mar","Apr","May","Jun",
            "Jul","Aug","Sep","Oct","Nov","Dec"};

    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final MessageSource messages;

    public ConsoleService(ObjectProvider<JdbcTemplate> jdbcProvider, MessageSource messages) {
        this.jdbcProvider = jdbcProvider;
        this.messages = messages;
    }

    /**
     * 切片测试（@WebMvcTest）不装配数据源，此时返回全零的空看板而不伪造统计；
     * 真实运行与集成测试中 JdbcTemplate 存在，全部数字来自当次 SQL。
     */
    private JdbcTemplate jdbc() {
        return jdbcProvider.getIfAvailable();
    }

    /** 只列出当前版本真正能进入的模块；未交付入口仍呈现范围，但不给链接、不给数字。 */
    public List<AdminModule> modules() {
        return List.of(
                new AdminModule(m("admin.console.module.catalog"), "/admin/products",
                        m("admin.console.module.catalog.scope"), "FR-A-02、FR-A-03", true),
                new AdminModule(m("admin.console.module.stock"), "/admin/products",
                        m("admin.console.module.stock.scope"), "FR-A-04、FR-A-07", true),
                new AdminModule(m("admin.console.module.accounts"), "/admin/users",
                        m("admin.console.module.accounts.scope"), "FR-A-07", true),
                new AdminModule(m("admin.console.module.orders"), "/admin/orders",
                        m("admin.console.module.orders.scope"), "FR-A-05、FR-A-06", false));
    }

    public Dashboard dashboard() {
        if (jdbc() == null) {
            return new Dashboard(null, 0, 0, 0, 0, List.of(), List.of(), today());
        }
        long live = count("SELECT COUNT(*) FROM product WHERE status='ON_SALE'");
        long draft = count("SELECT COUNT(*) FROM product WHERE status='DRAFT'");
        long low = count("SELECT COUNT(*) FROM product WHERE status='ON_SALE' AND stock BETWEEN 1 AND 3");
        long ship = count("SELECT COUNT(*) FROM sales_order WHERE status='PAID'");

        Featured featured = jdbc().query(
                "SELECT sku, name, summary, price, stock, status FROM product " +
                        "WHERE status='ON_SALE' ORDER BY updated_at DESC, id DESC LIMIT 1",
                rs -> rs.next() ? new Featured(rs.getString("sku"), rs.getString("name"),
                        rs.getString("summary"), rs.getBigDecimal("price"),
                        rs.getInt("stock"), rs.getString("status"),
                        m("admin.prod.hero.live"), shape(rs.getString("name"))) : null);

        List<Recent> recent = jdbc().query(
                "SELECT id, sku, name, status, updated_at FROM product ORDER BY updated_at DESC, id DESC LIMIT 3",
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp("updated_at");
                    LocalDateTime at = ts == null ? LocalDateTime.now() : ts.toLocalDateTime();
                    return new Recent(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                            "ON_SALE".equals(rs.getString("status"))
                                    ? m("admin.prod.statusLive") : m("admin.prod.statusDraft"),
                            relative(at), "ON_SALE".equals(rs.getString("status")));
                });

        List<Need> needs = new ArrayList<>();
        if (ship > 0) {
            needs.add(new Need(m("admin.console.label.ship"),
                    fmt("admin.console.need.ship.title", ship),
                    m("admin.console.need.ship.detail"), "/admin/products?status=PAID", false));
        }
        if (low > 0) {
            needs.add(new Need(m("admin.console.label.low"),
                    fmt("admin.console.need.low.title", low),
                    m("admin.console.need.low.detail"), "/admin/products?filter=low", true));
        }
        if (draft > 0) {
            needs.add(new Need(m("admin.console.label.draft"),
                    fmt("admin.console.need.draft.title", draft),
                    m("admin.console.need.draft.detail"), "/admin/products?filter=DRAFT", false));
        }

        return new Dashboard(featured, live, draft, low, ship, needs, recent, today());
    }

    private String shape(String name) {
        String n = name == null ? "" : name;
        if (n.contains("围巾") || n.contains("帽")) return "scarf";
        if (n.contains("袜")) return "socks";
        if (n.contains("裙")) return "dress";
        if (n.contains("衬衫") || n.contains("罩衫")) return "shirt";
        if (n.contains("Polo") || n.contains("T 恤") || n.contains("背心")) return "polo";
        if (n.contains("夹克") || n.contains("马甲")) return "jacket";
        if (n.contains("外套") || n.contains("大衣") || n.contains("西装")) return "coat";
        return "turtleneck";
    }

    private long count(String sql) {
        Long value = jdbc().queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private String m(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    private String fmt(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        String pattern = messages.getMessage(key, null, key, locale);
        return new MessageFormat(pattern, locale).format(args);
    }

    private String relative(LocalDateTime at) {
        long minutes = Math.max(0, Duration.between(at, LocalDateTime.now()).toMinutes());
        if (minutes < 1) return m("admin.console.time.just");
        if (minutes < 60) return fmt("admin.console.time.min", minutes);
        long hours = minutes / 60;
        if (hours < 24) return fmt("admin.console.time.hour", hours);
        return fmt("admin.console.time.day", hours / 24);
    }

    private String today() {
        LocalDate d = LocalDate.now();
        if ("en".equals(LocaleContextHolder.getLocale().getLanguage())) {
            return d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1] + " " + d.getYear();
        }
        return d.format(ZH_DATE);
    }
}
