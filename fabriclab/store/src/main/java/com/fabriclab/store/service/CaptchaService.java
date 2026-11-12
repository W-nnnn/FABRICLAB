package com.fabriclab.store.service;

import com.fabriclab.store.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端图形验证码（教学实现）：生成、存储、校验全部在服务端，
 * 页面只携带令牌与用户输入。一次性使用——任何一次校验（含失败）即消费；
 * 有效期 5 分钟；过期、重复使用、错误、无效分别给出可理解提示。
 */
@Service
public class CaptchaService {

    /** 验证码有效期。 */
    static final Duration TTL = Duration.ofMinutes(5);
    /** 过期后的宽限期：宽限期内仍提示「已过期」，超过后按「无效」处理。 */
    private static final Duration GRACE = TTL.multipliedBy(2);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;

    private record Entry(String code, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final Set<String> used = ConcurrentHashMap.newKeySet();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public CaptchaService() {
        this(Clock.systemDefaultZone());
    }

    /** 测试用：注入可控时钟验证过期路径。 */
    CaptchaService(Clock clock) {
        this.clock = clock;
    }

    /** 生成新验证码，返回令牌与明文码（服务端渲染到页面）。 */
    public Captcha generate() {
        purge();
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        String token = java.util.UUID.randomUUID().toString();
        store.put(token, new Entry(code.toString(), clock.instant().plus(TTL)));
        return new Captcha(token, code.toString());
    }

    /**
     * 一次性校验：无论结果如何都消费该验证码，重复提交同一令牌提示「已使用」。
     * 校验不区分大小写。
     */
    public void verify(String token, String input) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("验证码无效，请获取新的验证码");
        }
        Entry entry = store.get(token);
        if (entry == null) {
            if (used.contains(token)) {
                throw new BusinessException("验证码已使用，请获取新的验证码");
            }
            throw new BusinessException("验证码无效，请获取新的验证码");
        }
        consume(token);
        if (clock.instant().isAfter(entry.expiresAt())) {
            throw new BusinessException("验证码已过期，请获取新的验证码");
        }
        if (input == null || input.isBlank() || !entry.code().equalsIgnoreCase(input.trim())) {
            throw new BusinessException("验证码错误");
        }
    }

    private void consume(String token) {
        store.remove(token);
        used.add(token);
    }

    /** 生成时清理：超过宽限期的过期项与已用项，避免常驻内存增长。 */
    private void purge() {
        Instant grace = clock.instant().minus(GRACE);
        if (store.size() > 1000) {
            store.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(grace));
        }
        if (used.size() > 10000) {
            used.clear();
        }
    }

    /** 验证码载体：令牌用于服务端取回条目，明文码由页面渲染。 */
    public record Captcha(String token, String code) {
    }
}
