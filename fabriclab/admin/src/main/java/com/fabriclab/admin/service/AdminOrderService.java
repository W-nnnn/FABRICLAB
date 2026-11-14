package com.fabriclab.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.admin.entity.SalesOrder;
import com.fabriclab.admin.entity.SalesOrderItem;
import com.fabriclab.admin.entity.StoreUser;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.mapper.SalesOrderItemMapper;
import com.fabriclab.admin.mapper.SalesOrderMapper;
import com.fabriclab.admin.mapper.StoreUserMapper;
import com.fabriclab.admin.model.OrderItemRow;
import com.fabriclab.admin.model.OrderPage;
import com.fabriclab.admin.model.OrderQuery;
import com.fabriclab.admin.model.OrderRow;
import com.fabriclab.admin.model.OrderView;
import com.fabriclab.admin.model.ReceiverForm;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单管理（UC-A8、UC-A9、UC-A10、FR-A-05、FR-A-06）。
 *
 * <p>查单走订单号完整精确匹配（超 40 字符直接 400，与 DDL 列宽一致）；五状态筛选与分页
 * 缺省 10 条。详情读成交快照：金额、明细、支付凭证一律只读。可写的只有三处——收货信息
 * （限待支付 / 待发货）、发货（仅 PAID → SHIPPED，条件更新写首次发货时间）、删除
 * （仅已取消且无支付凭证的教学演示订单，先明细后主表）。其余状态迁移由用户端产生，
 * 后台不写。</p>
 */
@Service
public class AdminOrderService {

    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PAID = "PAID";
    public static final String SHIPPED = "SHIPPED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private static final int ORDER_NO_MAX = 40;
    private static final DateTimeFormatter ZH = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final String[] MONTHS = {"Jan","Feb","Mar","Apr","May","Jun",
            "Jul","Aug","Sep","Oct","Nov","Dec"};

    private static final Set<String> STATUSES = Set.of(
            PENDING_PAYMENT, PAID, SHIPPED, COMPLETED, CANCELLED);

    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper itemMapper;
    private final StoreUserMapper userMapper;
    private final MessageSource messages;

    public AdminOrderService(SalesOrderMapper orderMapper, SalesOrderItemMapper itemMapper,
                             StoreUserMapper userMapper, MessageSource messages) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
        this.messages = messages;
    }

    private String t(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }

    private boolean isEn() {
        return "en".equals(LocaleContextHolder.getLocale().getLanguage());
    }

    public OrderPage page(OrderQuery q) {
        int size = (q.size() == 10 || q.size() == 20 || q.size() == 50) ? q.size() : 10;
        String filter = normalizeStatus(q.status());
        String kw = q.kw();
        if (kw.length() > ORDER_NO_MAX) {
            throw new BusinessException(t("admin.err.order.noTooLong"));
        }
        LambdaQueryWrapper<SalesOrder> w = new LambdaQueryWrapper<>();
        if (!kw.isEmpty()) {
            w.eq(SalesOrder::getOrderNo, kw);
        }
        if (!"all".equals(filter)) {
            w.eq(SalesOrder::getStatus, filter);
        }
        w.orderByDesc(SalesOrder::getCreatedAt).orderByDesc(SalesOrder::getId);
        Page<SalesOrder> result = orderMapper.selectPage(new Page<>(q.page(), size), w);
        List<OrderRow> rows = result.getRecords().stream().map(this::row).collect(Collectors.toList());
        int pages = Math.max(1, (int) ((result.getTotal() + size - 1) / size));
        return new OrderPage(rows, result.getTotal(), q.page(), pages, size,
                count(null), count(PENDING_PAYMENT), count(PAID),
                count(SHIPPED), count(COMPLETED), count(CANCELLED));
    }

    /** 状态筛选白名单：全大写状态值或常用小写别名，其余一律 400。 */
    private String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw)) {
            return "all";
        }
        String upper = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (STATUSES.contains(upper)) {
            return upper;
        }
        throw new BusinessException(t("admin.err.order.statusInvalid"));
    }

    private long count(String status) {
        LambdaQueryWrapper<SalesOrder> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(SalesOrder::getStatus, status);
        }
        Long n = orderMapper.selectCount(w);
        return n == null ? 0L : n;
    }

    private OrderRow row(SalesOrder o) {
        boolean canShip = PAID.equals(o.getStatus());
        return new OrderRow(o.getOrderNo(), o.getReceiverName(), money(o.getTotalAmount()),
                o.getStatus(), statusLabel(o.getStatus()), statusClass(o.getStatus()),
                date(o.getCreatedAt()), canShip);
    }

    /** 详情：按订单号取单，不存在 404；明细与买家随单装配，全部只读。 */
    public OrderView view(String orderNo) {
        SalesOrder o = requireOrder(orderNo);
        List<SalesOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<SalesOrderItem>()
                .eq(SalesOrderItem::getOrderId, o.getId())
                .orderByAsc(SalesOrderItem::getId));
        List<OrderItemRow> itemRows = items.stream()
                .map(i -> new OrderItemRow(i.getSkuSnapshot(), i.getProductNameSnapshot(),
                        money(i.getUnitPrice()), i.getQuantity() == null ? 0 : i.getQuantity(),
                        money(i.getLineAmount())))
                .collect(Collectors.toList());
        StoreUser buyer = o.getUserId() == null ? null : userMapper.selectById(o.getUserId());
        String buyerLabel = buyer == null ? "—"
                : tr(buyer.getDisplayName()) + " · @" + buyer.getUsername();
        return new OrderView(o.getOrderNo(), o.getStatus(), statusLabel(o.getStatus()),
                statusClass(o.getStatus()), money(o.getTotalAmount()), buyerLabel,
                date(o.getCreatedAt()), itemRows, timeline(o),
                o.getReceiverName(), o.getReceiverPhone(), o.getReceiverAddress(),
                o.getPaymentMethod() == null ? t("admin.order.f.notPaid") : t("admin.order.f.mock"),
                o.getPaymentNo() == null ? "—" : o.getPaymentNo(),
                date(o.getPaidAt()), date(o.getCancelledAt()), date(o.getShippedAt()),
                date(o.getCompletedAt()),
                canEditReceiver(o), PAID.equals(o.getStatus()), canDelete(o));
    }

    private List<OrderView.TimelineStep> timeline(SalesOrder o) {
        List<OrderView.TimelineStep> steps = new ArrayList<>();
        steps.add(new OrderView.TimelineStep(t("admin.order.tl.placed"), date(o.getCreatedAt()), true));
        steps.add(new OrderView.TimelineStep(t("admin.order.tl.paid"), date(o.getPaidAt()),
                o.getPaidAt() != null));
        steps.add(new OrderView.TimelineStep(t("admin.order.tl.shipped"), date(o.getShippedAt()),
                o.getShippedAt() != null));
        steps.add(new OrderView.TimelineStep(t("admin.order.tl.completed"), date(o.getCompletedAt()),
                o.getCompletedAt() != null));
        steps.add(new OrderView.TimelineStep(t("admin.order.tl.cancelled"), date(o.getCancelledAt()),
                o.getCancelledAt() != null));
        return steps;
    }

    /** 收货信息：仅待支付、待发货可改；条件更新带状态，防并发状态变更后被改。 */
    public void updateReceiver(ReceiverForm f) {
        SalesOrder o = requireOrder(f.orderNo());
        if (!canEditReceiver(o)) {
            throw new BusinessException(t("admin.err.order.receiverLocked"));
        }
        String name = required(f.receiverName(), "admin.err.order.nameRequired", 50,
                "admin.err.order.nameLength");
        String phone = required(f.receiverPhone(), "admin.err.order.phoneRequired", 30,
                "admin.err.order.phoneLength");
        if (!phone.matches("^[0-9+\\-() ]+$")) {
            throw new BusinessException(t("admin.err.order.phoneInvalid"));
        }
        String address = required(f.receiverAddress(), "admin.err.order.addressRequired", 255,
                "admin.err.order.addressLength");
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderNo, o.getOrderNo())
                .in(SalesOrder::getStatus, PENDING_PAYMENT, PAID)
                .set(SalesOrder::getReceiverName, name)
                .set(SalesOrder::getReceiverPhone, phone)
                .set(SalesOrder::getReceiverAddress, address));
        if (rows == 0) {
            throw new BusinessException(t("admin.err.order.receiverLocked"));
        }
    }

    /** 发货：仅 PAID → SHIPPED，条件更新写首次发货时间；重复发货不改首次时间。 */
    public void ship(String orderNo) {
        SalesOrder o = requireOrder(orderNo);
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderNo, o.getOrderNo())
                .eq(SalesOrder::getStatus, PAID)
                .set(SalesOrder::getStatus, SHIPPED)
                .set(SalesOrder::getShippedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException(t("admin.err.order.shipRejected") + " · " + statusLabel(
                    freshStatus(o.getOrderNo())));
        }
    }

    /** 删除：仅已取消且无支付凭证的教学演示订单；先明细后主表，同一事务。 */
    @Transactional
    public void delete(String orderNo) {
        SalesOrder o = requireOrder(orderNo);
        if (!canDelete(o)) {
            throw new BusinessException(t("admin.err.order.deleteRestricted"));
        }
        itemMapper.delete(new LambdaQueryWrapper<SalesOrderItem>()
                .eq(SalesOrderItem::getOrderId, o.getId()));
        orderMapper.deleteById(o.getId());
    }

    private boolean canEditReceiver(SalesOrder o) {
        return PENDING_PAYMENT.equals(o.getStatus()) || PAID.equals(o.getStatus());
    }

    private boolean canDelete(SalesOrder o) {
        return CANCELLED.equals(o.getStatus()) && o.getPaymentNo() == null;
    }

    private String freshStatus(String orderNo) {
        SalesOrder current = orderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderNo, orderNo)
                .select(SalesOrder::getStatus));
        return current == null || current.getStatus() == null ? "" : current.getStatus();
    }

    private SalesOrder requireOrder(String orderNo) {
        String no = orderNo == null ? "" : orderNo.trim();
        if (no.isEmpty()) {
            throw new NotFoundException(t("admin.err.order.notFound"));
        }
        if (no.length() > ORDER_NO_MAX) {
            throw new BusinessException(t("admin.err.order.noTooLong"));
        }
        SalesOrder o = orderMapper.selectOne(new LambdaQueryWrapper<SalesOrder>()
                .eq(SalesOrder::getOrderNo, no));
        if (o == null) {
            throw new NotFoundException(t("admin.err.order.notFound"));
        }
        return o;
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case PENDING_PAYMENT -> t("admin.order.statusPending");
            case PAID -> t("admin.order.statusPaid");
            case SHIPPED -> t("admin.order.statusShipped");
            case COMPLETED -> t("admin.order.statusCompleted");
            case CANCELLED -> t("admin.order.statusCancelled");
            default -> status == null ? "—" : status;
        };
    }

    private String statusClass(String status) {
        return switch (status == null ? "" : status) {
            case PENDING_PAYMENT -> "tag--act";
            case PAID -> "tag--live";
            case SHIPPED -> "tag--live";
            case COMPLETED -> "tag--done";
            case CANCELLED -> "tag--end";
            default -> "";
        };
    }

    private String required(String raw, String requiredKey, int max, String lengthKey) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) throw new BusinessException(t(requiredKey));
        if (v.length() > max) throw new BusinessException(t(lengthKey));
        return v;
    }

    private String tr(String text) {
        return new com.fabriclab.admin.config.ContentI18n().t(text);
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
}
