package com.fabriclab.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.admin.entity.SalesOrder;
import com.fabriclab.admin.entity.StoreUser;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.mapper.SalesOrderMapper;
import com.fabriclab.admin.mapper.StoreUserMapper;
import com.fabriclab.admin.model.UserForm;
import com.fabriclab.admin.model.UserPage;
import com.fabriclab.admin.model.UserQuery;
import com.fabriclab.admin.model.UserRow;
import com.fabriclab.admin.model.UserView;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 顾客账号维护（UC-A7、FR-A-07）。只返回 role = CUSTOMER 的账号，管理员账号既不出现在
 * 列表里也不能被这个入口读写——任何非顾客 ID 一律按 404 处理，避免自我锁死与角色越界。
 *
 * <p>可改的是显示名、邮箱、电话；用户名与角色创建后不可改。口令重置为统一初始教学口令，
 * 旧散列被整体替换，用户端在会话里保存的散列指纹随即对不上，下一次受保护请求即失效。
 * 启停重复操作不写库，直接提示当前状态。</p>
 */
@Service
public class AdminUserService {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    /** 统一初始教学口令：与演示种子数据一致，满足用户端注册策略（8—32 位、含多类字符）。 */
    public static final String INITIAL_PASSWORD = "Passw0rd!2026";

    private static final DateTimeFormatter ZH = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final String[] MONTHS = {"Jan","Feb","Mar","Apr","May","Jun",
            "Jul","Aug","Sep","Oct","Nov","Dec"};

    private final StoreUserMapper userMapper;
    private final SalesOrderMapper orderMapper;
    private final MessageSource messages;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminUserService(StoreUserMapper userMapper, SalesOrderMapper orderMapper,
                            MessageSource messages) {
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    private boolean isEn() {
        return "en".equals(LocaleContextHolder.getLocale().getLanguage());
    }

    public UserPage page(UserQuery q) {
        int size = (q.size() == 10 || q.size() == 20 || q.size() == 50) ? q.size() : 20;
        String filter = switch (q.status()) {
            case "enabled", "ENABLED", "disabled", "DISABLED" -> q.status();
            default -> "all";
        };
        LambdaQueryWrapper<StoreUser> w = new LambdaQueryWrapper<StoreUser>()
                .eq(StoreUser::getRole, CUSTOMER);
        if (!q.kw().isBlank()) {
            w.like(StoreUser::getUsername, q.kw());
        }
        switch (filter) {
            case "enabled", "ENABLED" -> w.eq(StoreUser::getStatus, ENABLED);
            case "disabled", "DISABLED" -> w.eq(StoreUser::getStatus, DISABLED);
            default -> { }
        }
        w.orderByAsc(StoreUser::getId);
        Page<StoreUser> result = userMapper.selectPage(new Page<>(q.page(), size), w);

        Map<Long, long[]> counts = new HashMap<>();
        Map<Long, BigDecimal> totals = new HashMap<>();
        List<Long> ids = result.getRecords().stream().map(StoreUser::getId).toList();
        if (!ids.isEmpty()) {
            List<SalesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                    .in(SalesOrder::getUserId, ids)
                    .select(SalesOrder::getUserId, SalesOrder::getStatus,
                            SalesOrder::getTotalAmount));
            for (SalesOrder o : orders) {
                counts.computeIfAbsent(o.getUserId(), k -> new long[1])[0]++;
                BigDecimal amount = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
                totals.merge(o.getUserId(), amount, BigDecimal::add);
            }
        }

        List<UserRow> rows = result.getRecords().stream()
                .map(u -> row(u, orderCountOf(counts, u.getId()),
                        totals.getOrDefault(u.getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());
        int pages = Math.max(1, (int) ((result.getTotal() + size - 1) / size));
        return new UserPage(rows, result.getTotal(), q.page(), pages, size,
                count(null),
                count(ENABLED),
                count(DISABLED));
    }

    private long orderCountOf(Map<Long, long[]> counts, long userId) {
        long[] box = counts.get(userId);
        return box == null ? 0L : box[0];
    }

    private long count(String status) {
        LambdaQueryWrapper<StoreUser> w = new LambdaQueryWrapper<StoreUser>()
                .eq(StoreUser::getRole, CUSTOMER);
        if (status != null) {
            w.eq(StoreUser::getStatus, status);
        }
        Long n = userMapper.selectCount(w);
        return n == null ? 0L : n;
    }

    private UserRow row(StoreUser u, long orderCount, BigDecimal total) {
        boolean on = ENABLED.equals(u.getStatus());
        return new UserRow(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.getPhone(),
                u.getStatus(),
                on ? t("admin.user.statusOn") : t("admin.user.statusOff"),
                on ? "tag--live" : "tag--end",
                dateOnly(u.getCreatedAt()), orderCount, money(total));
    }

    /** 详情：顾客账号才可进入；管理员或不存在一律 404，不暴露是否存在。 */
    public UserView view(long id) {
        StoreUser u = requireCustomer(id);
        Long total = orderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getUserId, id));
        List<SalesOrder> recent = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getUserId, id)
                .orderByDesc(SalesOrder::getCreatedAt).orderByDesc(SalesOrder::getId)
                .last("LIMIT 3"));
        BigDecimal sum = orderMapper.selectList(new LambdaQueryWrapper<SalesOrder>()
                        .eq(SalesOrder::getUserId, id).select(SalesOrder::getTotalAmount))
                .stream().map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean on = ENABLED.equals(u.getStatus());
        List<UserView.OrderBrief> briefs = new ArrayList<>(recent.size());
        for (SalesOrder o : recent) {
            briefs.add(new UserView.OrderBrief(o.getOrderNo(), date(o.getCreatedAt()),
                    money(o.getTotalAmount()), orderStatus(o.getStatus()),
                    orderStatusClass(o.getStatus())));
        }
        return new UserView(u.getId(), u.getUsername(), u.getRole(), u.getDisplayName(),
                u.getEmail(), u.getPhone(), u.getStatus(),
                on ? t("admin.user.statusOn") : t("admin.user.statusOff"),
                on ? "tag--live" : "tag--end",
                date(u.getCreatedAt()), date(u.getUpdatedAt()),
                total == null ? 0 : total, money(sum), briefs);
    }

    public UserForm form(long id) {
        StoreUser u = requireCustomer(id);
        return new UserForm(u.getId(), u.getUsername(), u.getRole(), u.getDisplayName(),
                u.getEmail() == null ? "" : u.getEmail(),
                u.getPhone() == null ? "" : u.getPhone());
    }

    /** 只改显示名、邮箱、电话；用户名与角色即使被提交也不接受变更。 */
    public void update(long id, UserForm f) {
        StoreUser u = requireCustomer(id);
        if (f.username() != null && !f.username().isBlank()
                && !Objects.equals(f.username().trim(), u.getUsername())) {
            throw new BusinessException(t("admin.err.user.usernameImmutable"));
        }
        if (f.role() != null && !f.role().isBlank()
                && !Objects.equals(f.role().trim(), u.getRole())) {
            throw new BusinessException(t("admin.err.user.roleImmutable"));
        }
        String displayName = required(f.displayName(), "admin.err.user.displayRequired", 50,
                "admin.err.user.displayLength");
        String email = optional(f.email(), 100, "admin.err.user.emailLength");
        if (!email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException(t("admin.err.user.emailInvalid"));
        }
        String phone = optional(f.phone(), 30, "admin.err.user.phoneLength");
        if (!phone.isEmpty() && !phone.matches("^[0-9+\\-() ]+$")) {
            throw new BusinessException(t("admin.err.user.phoneInvalid"));
        }
        userMapper.update(null, new LambdaUpdateWrapper<StoreUser>()
                .eq(StoreUser::getId, id)
                .set(StoreUser::getDisplayName, displayName)
                .set(StoreUser::getEmail, email.isEmpty() ? null : email)
                .set(StoreUser::getPhone, phone.isEmpty() ? null : phone));
    }

    /** 重置为统一初始口令：整条替换散列，用户端旧会话的散列指纹随即失效。 */
    public void resetPassword(long id) {
        requireCustomer(id);
        userMapper.update(null, new LambdaUpdateWrapper<StoreUser>()
                .eq(StoreUser::getId, id)
                .set(StoreUser::getPasswordHash, encoder.encode(INITIAL_PASSWORD)));
    }

    /** 启停：重复操作不写库，返回 false 由控制器提示「当前已是该状态」。 */
    public boolean setStatus(long id, String target) {
        StoreUser u = requireCustomer(id);
        String next = DISABLED.equalsIgnoreCase(target) ? DISABLED : ENABLED;
        if (next.equals(u.getStatus())) {
            return false;
        }
        userMapper.update(null, new LambdaUpdateWrapper<StoreUser>()
                .eq(StoreUser::getId, id).eq(StoreUser::getStatus, u.getStatus())
                .set(StoreUser::getStatus, next));
        return true;
    }

    public String currentStatusLabel(long id) {
        StoreUser u = requireCustomer(id);
        return ENABLED.equals(u.getStatus()) ? t("admin.user.statusOn") : t("admin.user.statusOff");
    }

    /** 只认顾客账号：不存在、管理员或角色异常都按 404，不泄露存在性。 */
    private StoreUser requireCustomer(long id) {
        StoreUser u = userMapper.selectById(id);
        if (u == null || !CUSTOMER.equals(u.getRole())) {
            throw new NotFoundException(t("admin.err.user.notFound"));
        }
        return u;
    }

    private String required(String raw, String requiredKey, int max, String lengthKey) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) throw new BusinessException(t(requiredKey));
        if (v.length() > max) throw new BusinessException(t(lengthKey));
        return v;
    }

    private String optional(String raw, int max, String lengthKey) {
        String v = raw == null ? "" : raw.trim();
        if (v.length() > max) throw new BusinessException(t(lengthKey));
        return v;
    }

    /** 列表只需要注册日期：带时间在 100px 列宽里会顶到状态标记。 */
    private String dateOnly(LocalDateTime at) {
        if (at == null) return "—";
        return isEn() ? at.getDayOfMonth() + " " + MONTHS[at.getMonthValue() - 1] + " " + at.getYear()
                : at.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
    }

    private String date(LocalDateTime at) {
        if (at == null) return "—";
        return isEn() ? at.getDayOfMonth() + " " + MONTHS[at.getMonthValue() - 1] + " " + at.getYear()
                + " " + String.format("%02d:%02d", at.getHour(), at.getMinute())
                : at.format(ZH);
    }

    private String money(BigDecimal amount) {
        BigDecimal v = amount == null ? BigDecimal.ZERO : amount;
        return "¥" + String.format(java.util.Locale.ROOT, "%,.2f", v);
    }

    private String orderStatus(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING_PAYMENT" -> t("admin.order.statusPending");
            case "PAID" -> t("admin.order.statusPaid");
            case "SHIPPED" -> t("admin.order.statusShipped");
            case "COMPLETED" -> t("admin.order.statusCompleted");
            case "CANCELLED" -> t("admin.order.statusCancelled");
            default -> status == null ? "—" : status;
        };
    }

    private String orderStatusClass(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING_PAYMENT" -> "tag--act";
            case "PAID" -> "tag--live";
            case "COMPLETED" -> "tag--done";
            case "CANCELLED" -> "tag--end";
            default -> "";
        };
    }
}
