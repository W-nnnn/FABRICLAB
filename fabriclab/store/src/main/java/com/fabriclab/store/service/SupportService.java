package com.fabriclab.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.store.entity.Product;
import com.fabriclab.store.entity.SalesOrder;
import com.fabriclab.store.entity.ServiceMessage;
import com.fabriclab.store.entity.ServiceThread;
import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.mapper.ProductMapper;
import com.fabriclab.store.mapper.SalesOrderMapper;
import com.fabriclab.store.mapper.ServiceMessageMapper;
import com.fabriclab.store.mapper.ServiceThreadMapper;
import com.fabriclab.store.mapper.StoreUserMapper;
import com.fabriclab.store.model.CurrentUser;
import com.fabriclab.store.model.ServiceCategory;
import com.fabriclab.store.model.ServiceForm;
import com.fabriclab.store.model.ServiceQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 选购咨询（客服模块 · 用户端）：一次会话由主表记录状态，消息表承载完整对话。
 * 会员只能读写自己的会话，越权统一按 404；状态迁移用「编号 + 本人 + 原状态」条件更新，
 * 重复提交与两端并发都不会写出第二条迁移。关联订单号与款号在写库前复核归属与存在性，
 * 只作展示，不改变订单状态、金额或库存，不实现 PRD 3.3 排除的售后工单流程。
 */
@Service
public class SupportService {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_REPLIED = "REPLIED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String SENDER_CUSTOMER = "CUSTOMER";
    public static final String SENDER_ADMIN = "ADMIN";

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter VIEW_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    /** 列表行：状态与类别的文案键、真实消息数与最后一条消息摘要。 */
    public record ThreadRow(ServiceThread thread, String statusKey, String categoryKey,
                            int messageCount, String lastSnippet, String createdText,
                            String activityText) {}

    /** 详情消息行：发送方与时间由服务端判定并格式化，模板不引用实体枚举。 */
    public record MessageRow(String body, boolean fromCustomer, String authorLabel, String time) {}

    /** 详情视图。 */
    public record ThreadView(ServiceThread thread, String statusKey, String categoryKey,
                             List<MessageRow> messages, String createdText) {}

    private final ServiceThreadMapper threadMapper;
    private final ServiceMessageMapper messageMapper;
    private final SalesOrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final StoreUserMapper userMapper;
    private final CurrentUserService currentUserService;

    public SupportService(ServiceThreadMapper threadMapper, ServiceMessageMapper messageMapper,
                          SalesOrderMapper orderMapper, ProductMapper productMapper,
                          StoreUserMapper userMapper, CurrentUserService currentUserService) {
        this.threadMapper = threadMapper;
        this.messageMapper = messageMapper;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.currentUserService = currentUserService;
    }

    /** 本人咨询列表：状态筛选与分页共同生效，按最后活动倒序。 */
    public Page<ServiceThread> list(long userId, ServiceQuery query) {
        LambdaQueryWrapper<ServiceThread> wrapper = new LambdaQueryWrapper<ServiceThread>()
                .eq(ServiceThread::getUserId, userId);
        if (query.status() != null) {
            wrapper.eq(ServiceThread::getStatus, query.status().name());
        }
        wrapper.orderByDesc(ServiceThread::getLastActivityAt, ServiceThread::getId);
        return threadMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
    }

    /** 列表行的消息数与摘要来自当次查询，不写静态数字。 */
    public List<ThreadRow> rows(List<ServiceThread> threads) {
        Map<Long, List<ServiceMessage>> grouped = messagesOf(threads);
        List<ThreadRow> rows = new ArrayList<>();
        for (ServiceThread thread : threads) {
            List<ServiceMessage> messages = grouped.getOrDefault(thread.getId(), List.of());
            String last = messages.isEmpty() ? "" : messages.get(messages.size() - 1).getBody();
            rows.add(new ThreadRow(thread,
                    com.fabriclab.store.model.ServiceStatus.of(thread.getStatus()).labelKey(),
                    ServiceCategory.of(thread.getCategory()).labelKey(),
                    messages.size(),
                    last.length() > 64 ? last.substring(0, 64) + "…" : last,
                    time(thread.getCreatedAt()), time(thread.getLastActivityAt())));
        }
        return rows;
    }

    /** 本人待回复数，供客服中心横幅展示真实统计。 */
    public long openCount(long userId) {
        Long n = threadMapper.selectCount(new LambdaQueryWrapper<ServiceThread>()
                .eq(ServiceThread::getUserId, userId)
                .eq(ServiceThread::getStatus, STATUS_OPEN));
        return n == null ? 0 : n;
    }

    /** 本人会话详情：非本人按 404，不泄露他人咨询是否存在。 */
    public ThreadView view(CurrentUser current, long threadId) {
        ServiceThread thread = owned(current, threadId);
        List<MessageRow> messages = new ArrayList<>();
        for (ServiceMessage message : messageMapper.selectList(new LambdaQueryWrapper<ServiceMessage>()
                .eq(ServiceMessage::getThreadId, threadId)
                .orderByAsc(ServiceMessage::getCreatedAt)
                .orderByAsc(ServiceMessage::getId))) {
            boolean fromCustomer = SENDER_CUSTOMER.equals(message.getSender());
            messages.add(new MessageRow(message.getBody(), fromCustomer,
                    fromCustomer ? message.getAuthorName() : "SERVICE",
                    time(message.getCreatedAt())));
        }
        return new ThreadView(thread,
                com.fabriclab.store.model.ServiceStatus.of(thread.getStatus()).labelKey(),
                ServiceCategory.of(thread.getCategory()).labelKey(), messages,
                time(thread.getCreatedAt()));
    }

    /** 新建咨询：校验、复核关联信息、写主表与第一条消息，同一事务。 */
    @Transactional
    public ServiceThread create(CurrentUser current, ServiceForm raw) {
        ServiceForm form = raw.checked();
        if (form.orderNo() != null) {
            Long count = orderMapper.selectCount(new LambdaQueryWrapper<SalesOrder>()
                    .eq(SalesOrder::getOrderNo, form.orderNo())
                    .eq(SalesOrder::getUserId, current.id()));
            if (count == null || count == 0) {
                throw new BusinessException("orderNo", "订单号不存在或不属于当前账号");
            }
        }
        if (form.sku() != null) {
            Long count = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                    .eq(Product::getSku, form.sku()));
            if (count == null || count == 0) {
                throw new BusinessException("sku", "没有找到这个款号");
            }
        }
        StoreUser user = userMapper.selectById(current.id());
        String authorName = user == null || user.getDisplayName() == null
                ? current.username() : user.getDisplayName();
        LocalDateTime now = LocalDateTime.now();
        ServiceThread thread = new ServiceThread();
        thread.setThreadNo(newThreadNo(now));
        thread.setUserId(current.id());
        thread.setCategory(form.category());
        thread.setSubject(form.subject());
        thread.setStatus(STATUS_OPEN);
        thread.setRelatedOrderNo(form.orderNo());
        thread.setRelatedSku(form.sku());
        thread.setCreatedAt(now);
        thread.setLastActivityAt(now);
        threadMapper.insert(thread);

        ServiceMessage message = new ServiceMessage();
        message.setThreadId(thread.getId());
        message.setSender(SENDER_CUSTOMER);
        message.setAuthorId(current.id());
        message.setAuthorName(authorName);
        message.setBody(form.body());
        message.setCreatedAt(now);
        messageMapper.insert(message);
        return thread;
    }

    /** 追问：仅待回复 / 已回复可追加，写消息并把状态置回待回复。 */
    @Transactional
    public void append(CurrentUser current, long threadId, String body) {
        String text = ServiceForm.of(null, null, body, null, null).checkedBody();
        ServiceThread thread = owned(current, threadId);
        if (STATUS_CLOSED.equals(thread.getStatus())) {
            throw new BusinessException("咨询已关闭，请先重新打开后再追问");
        }
        LocalDateTime now = LocalDateTime.now();
        int changed = threadMapper.update(null, new LambdaUpdateWrapper<ServiceThread>()
                .eq(ServiceThread::getId, threadId)
                .eq(ServiceThread::getUserId, current.id())
                .in(ServiceThread::getStatus, List.of(STATUS_OPEN, STATUS_REPLIED))
                .set(ServiceThread::getStatus, STATUS_OPEN)
                .set(ServiceThread::getLastActivityAt, now));
        if (changed != 1) {
            throw new BusinessException("咨询状态已变化，请刷新后重试");
        }
        insertMessage(current, threadId, text, now);
    }

    /** 关闭：重复关闭按已处理返回，不重复写关闭时间。 */
    @Transactional
    public boolean close(CurrentUser current, long threadId) {
        owned(current, threadId);
        return threadMapper.update(null, new LambdaUpdateWrapper<ServiceThread>()
                .eq(ServiceThread::getId, threadId)
                .eq(ServiceThread::getUserId, current.id())
                .ne(ServiceThread::getStatus, STATUS_CLOSED)
                .set(ServiceThread::getStatus, STATUS_CLOSED)
                .set(ServiceThread::getClosedAt, LocalDateTime.now())) == 1;
    }

    /** 重新打开：仅已关闭可重开，清空关闭时间并回到待回复。 */
    @Transactional
    public boolean reopen(CurrentUser current, long threadId) {
        owned(current, threadId);
        LocalDateTime now = LocalDateTime.now();
        return threadMapper.update(null, new LambdaUpdateWrapper<ServiceThread>()
                .eq(ServiceThread::getId, threadId)
                .eq(ServiceThread::getUserId, current.id())
                .eq(ServiceThread::getStatus, STATUS_CLOSED)
                .set(ServiceThread::getStatus, STATUS_OPEN)
                .set(ServiceThread::getClosedAt, null)
                .set(ServiceThread::getLastActivityAt, now)) == 1;
    }

    private ServiceThread owned(CurrentUser current, long threadId) {
        ServiceThread thread = threadMapper.selectById(threadId);
        if (thread == null) {
            throw new NotFoundException("内容不存在", "咨询不存在或已被移除");
        }
        currentUserService.requireOwner(current, thread.getUserId());
        return thread;
    }

    private void insertMessage(CurrentUser current, long threadId, String body, LocalDateTime now) {
        StoreUser user = userMapper.selectById(current.id());
        ServiceMessage message = new ServiceMessage();
        message.setThreadId(threadId);
        message.setSender(SENDER_CUSTOMER);
        message.setAuthorId(current.id());
        message.setAuthorName(user == null || user.getDisplayName() == null
                ? current.username() : user.getDisplayName());
        message.setBody(body);
        message.setCreatedAt(now);
        messageMapper.insert(message);
    }

    private Map<Long, List<ServiceMessage>> messagesOf(List<ServiceThread> threads) {
        if (threads.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = threads.stream().map(ServiceThread::getId).toList();
        Map<Long, List<ServiceMessage>> grouped = new LinkedHashMap<>();
        for (ServiceMessage message : messageMapper.selectList(new LambdaQueryWrapper<ServiceMessage>()
                .in(ServiceMessage::getThreadId, ids)
                .orderByAsc(ServiceMessage::getCreatedAt)
                .orderByAsc(ServiceMessage::getId))) {
            grouped.computeIfAbsent(message.getThreadId(), k -> new ArrayList<>()).add(message);
        }
        return grouped;
    }

    private static String newThreadNo(LocalDateTime now) {
        String suffix = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000000));
        return "FL-S" + now.format(NO_TIME) + String.format("%6s", suffix).replace(' ', '0')
                .toUpperCase(java.util.Locale.ROOT);
    }

    private static String time(LocalDateTime at) {
        return at == null ? "—" : at.format(VIEW_TIME);
    }
}
