package com.fabriclab.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fabriclab.admin.entity.ServiceMessage;
import com.fabriclab.admin.entity.ServiceThread;
import com.fabriclab.admin.entity.StoreUser;
import com.fabriclab.admin.exception.BusinessException;
import com.fabriclab.admin.exception.NotFoundException;
import com.fabriclab.admin.mapper.ServiceMessageMapper;
import com.fabriclab.admin.mapper.ServiceThreadMapper;
import com.fabriclab.admin.mapper.StoreUserMapper;
import com.fabriclab.admin.model.AdminUser;
import com.fabriclab.admin.model.ServiceKeys;
import com.fabriclab.admin.model.ServicePage;
import com.fabriclab.admin.model.ServiceQuery;
import com.fabriclab.admin.model.ServiceRow;
import com.fabriclab.admin.model.ServiceView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服咨询管理（后台）：列表、真实状态计数、完整对话与回复 / 关闭 / 重开。
 * 只读写两张咨询扩展表与共享 store_user，不改订单状态、金额、库存，也不实现售后工单。
 * 状态迁移用「编号 + 原状态」条件更新，重复提交与两端并发都不会重复迁移。
 */
@Service
public class AdminSupportService {

    public static final String OPEN = "OPEN";
    public static final String REPLIED = "REPLIED";
    public static final String CLOSED = "CLOSED";
    public static final int MAX_REPLY = 1000;

    private static final DateTimeFormatter VIEW = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final ServiceThreadMapper threadMapper;
    private final ServiceMessageMapper messageMapper;
    private final StoreUserMapper userMapper;

    public AdminSupportService(ServiceThreadMapper threadMapper, ServiceMessageMapper messageMapper,
                               StoreUserMapper userMapper) {
        this.threadMapper = threadMapper;
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
    }

    /** 列表：关键字可匹配编号、主题、款号、订单号或会员用户名；按最后活动倒序。 */
    public ServicePage page(ServiceQuery q) {
        // 页大小夹紧到 1..50：既允许小页验证分页，也不接受超大页拖垮查询
        int size = Math.max(1, Math.min(q.size(), 50));
        String filter = switch (q.status()) {
            case "open", "replied", "closed" -> q.status();
            default -> "all";
        };
        LambdaQueryWrapper<ServiceThread> w = new LambdaQueryWrapper<>();
        if (!q.kw().isBlank()) {
            List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<StoreUser>()
                            .like(StoreUser::getUsername, q.kw())
                            .or().like(StoreUser::getDisplayName, q.kw())
                            .select(StoreUser::getId))
                    .stream().map(StoreUser::getId).toList();
            w.and(nested -> {
                nested.like(ServiceThread::getThreadNo, q.kw())
                        .or().like(ServiceThread::getSubject, q.kw())
                        .or().like(ServiceThread::getRelatedOrderNo, q.kw())
                        .or().like(ServiceThread::getRelatedSku, q.kw());
                if (!userIds.isEmpty()) {
                    nested.or().in(ServiceThread::getUserId, userIds);
                }
            });
        }
        switch (filter) {
            case "open" -> w.eq(ServiceThread::getStatus, OPEN);
            case "replied" -> w.eq(ServiceThread::getStatus, REPLIED);
            case "closed" -> w.eq(ServiceThread::getStatus, CLOSED);
            default -> { }
        }
        w.orderByDesc(ServiceThread::getLastActivityAt, ServiceThread::getId);
        Page<ServiceThread> result = threadMapper.selectPage(new Page<>(q.page(), size), w);
        List<ServiceThread> threads = result.getRecords();

        Map<Long, StoreUser> users = usersOf(threads);
        Map<Long, List<ServiceMessage>> grouped = messagesOf(threads);
        List<ServiceRow> rows = new ArrayList<>();
        for (ServiceThread t : threads) {
            StoreUser u = users.get(t.getUserId());
            List<ServiceMessage> messages = grouped.getOrDefault(t.getId(), List.of());
            String last = messages.isEmpty() ? "" : messages.get(messages.size() - 1).getBody();
            rows.add(new ServiceRow(t.getId(), t.getThreadNo(),
                    u == null ? "—" : u.getUsername(),
                    u == null || u.getDisplayName() == null ? "—" : u.getDisplayName(),
                    ServiceKeys.categoryKey(t.getCategory()), ServiceKeys.statusKey(t.getStatus()),
                    t.getStatus(), t.getSubject(),
                    last.length() > 72 ? last.substring(0, 72) + "…" : last,
                    messages.size(), time(t.getLastActivityAt()), time(t.getCreatedAt()),
                    t.getRelatedOrderNo(), t.getRelatedSku()));
        }
        int pages = Math.max(1, (int) ((result.getTotal() + size - 1) / size));
        return new ServicePage(rows, result.getTotal(), q.page(), pages, size,
                count(null), count(OPEN), count(REPLIED), count(CLOSED));
    }

    /** 详情：会话、会员联系资料与完整对话，非存在的编号按 404。 */
    public ServiceView view(long id) {
        ServiceThread t = threadMapper.selectById(id);
        if (t == null) {
            throw new NotFoundException("内容不存在", "咨询不存在或已被移除");
        }
        StoreUser u = userMapper.selectById(t.getUserId());
        List<ServiceView.Message> messages = new ArrayList<>();
        for (ServiceMessage m : messageMapper.selectList(new LambdaQueryWrapper<ServiceMessage>()
                .eq(ServiceMessage::getThreadId, id)
                .orderByAsc(ServiceMessage::getCreatedAt)
                .orderByAsc(ServiceMessage::getId))) {
            messages.add(new ServiceView.Message(m.getBody(),
                    "CUSTOMER".equals(m.getSender()), m.getAuthorName(), time(m.getCreatedAt())));
        }
        return new ServiceView(t, ServiceKeys.statusKey(t.getStatus()),
                ServiceKeys.categoryKey(t.getCategory()),
                u == null ? "—" : u.getUsername(),
                u == null || u.getDisplayName() == null ? "—" : u.getDisplayName(),
                u == null || u.getEmail() == null ? "—" : u.getEmail(),
                u == null || u.getPhone() == null ? "—" : u.getPhone(),
                messages, time(t.getCreatedAt()), time(t.getLastActivityAt()),
                time(t.getFirstRepliedAt()));
    }

    /** 回复：写管理员消息并把状态置为已回复，首次回复时间只写一次。 */
    @Transactional
    public void reply(long id, AdminUser admin, String raw) {
        String body = raw == null ? "" : raw.trim();
        if (body.isEmpty()) {
            throw new BusinessException("body", "请填写回复内容");
        }
        if (body.length() > MAX_REPLY) {
            throw new BusinessException("body", "回复内容最长 " + MAX_REPLY + " 个字符");
        }
        ServiceThread t = threadMapper.selectById(id);
        if (t == null) {
            throw new NotFoundException("内容不存在", "咨询不存在或已被移除");
        }
        if (CLOSED.equals(t.getStatus())) {
            throw new BusinessException("咨询已关闭，请先重新打开后再回复");
        }
        LocalDateTime now = LocalDateTime.now();
        int changed = threadMapper.update(null, new LambdaUpdateWrapper<ServiceThread>()
                .eq(ServiceThread::getId, id)
                .in(ServiceThread::getStatus, List.of(OPEN, REPLIED))
                .set(ServiceThread::getStatus, REPLIED)
                .set(ServiceThread::getLastActivityAt, now)
                .set(ServiceThread::getFirstRepliedAt,
                        t.getFirstRepliedAt() == null ? now : t.getFirstRepliedAt()));
        if (changed != 1) {
            throw new BusinessException("咨询状态已变化，请刷新后重试");
        }
        ServiceMessage m = new ServiceMessage();
        m.setThreadId(id);
        m.setSender("ADMIN");
        m.setAuthorId(admin.id());
        m.setAuthorName(admin.displayName() == null ? admin.username() : admin.displayName());
        m.setBody(body);
        m.setCreatedAt(now);
        messageMapper.insert(m);
    }

    /** 关闭：重复关闭返回 false，不重复写关闭时间。 */
    @Transactional
    public boolean close(long id) {
        require(id);
        return threadMapper.update(null, new LambdaUpdateWrapper<ServiceThread>()
                .eq(ServiceThread::getId, id)
                .ne(ServiceThread::getStatus, CLOSED)
                .set(ServiceThread::getStatus, CLOSED)
                .set(ServiceThread::getClosedAt, LocalDateTime.now())) == 1;
    }

    /** 重开：仅已关闭可重开，清空关闭时间并回到待回复。 */
    @Transactional
    public boolean reopen(long id) {
        require(id);
        return threadMapper.update(null, new LambdaUpdateWrapper<ServiceThread>()
                .eq(ServiceThread::getId, id)
                .eq(ServiceThread::getStatus, CLOSED)
                .set(ServiceThread::getStatus, OPEN)
                .set(ServiceThread::getClosedAt, null)
                .set(ServiceThread::getLastActivityAt, LocalDateTime.now())) == 1;
    }

    /** 待回复咨询数：顶栏铃铛与控制台待办都取这个真实计数。 */
    public long openCount() {
        return count(OPEN);
    }

    private void require(long id) {
        if (threadMapper.selectById(id) == null) {
            throw new NotFoundException("内容不存在", "咨询不存在或已被移除");
        }
    }

    private long count(String status) {
        LambdaQueryWrapper<ServiceThread> w = new LambdaQueryWrapper<>();
        if (status != null) {
            w.eq(ServiceThread::getStatus, status);
        }
        Long n = threadMapper.selectCount(w);
        return n == null ? 0 : n;
    }

    private Map<Long, StoreUser> usersOf(List<ServiceThread> threads) {
        List<Long> ids = threads.stream().map(ServiceThread::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, StoreUser> map = new HashMap<>();
        for (StoreUser u : userMapper.selectList(new LambdaQueryWrapper<StoreUser>()
                .in(StoreUser::getId, ids))) {
            map.put(u.getId(), u);
        }
        return map;
    }

    private Map<Long, List<ServiceMessage>> messagesOf(List<ServiceThread> threads) {
        if (threads.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = threads.stream().map(ServiceThread::getId).toList();
        Map<Long, List<ServiceMessage>> grouped = new LinkedHashMap<>();
        for (ServiceMessage m : messageMapper.selectList(new LambdaQueryWrapper<ServiceMessage>()
                .in(ServiceMessage::getThreadId, ids)
                .orderByAsc(ServiceMessage::getCreatedAt)
                .orderByAsc(ServiceMessage::getId))) {
            grouped.computeIfAbsent(m.getThreadId(), k -> new ArrayList<>()).add(m);
        }
        return grouped;
    }

    private static String time(LocalDateTime at) {
        return at == null ? "—" : at.format(VIEW);
    }
}
