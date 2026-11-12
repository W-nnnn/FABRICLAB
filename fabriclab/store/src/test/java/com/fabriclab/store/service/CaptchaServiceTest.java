package com.fabriclab.store.service;

import com.fabriclab.store.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 服务端验证码：一次性消费、过期、重复使用、错误、无效各路径。 */
class CaptchaServiceTest {

    /** 可手动推进的测试时钟。 */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-12T09:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final CaptchaService service = new CaptchaService(clock);

    @Test
    void correctCodePassesAndCodeIsFourChars() {
        CaptchaService.Captcha captcha = service.generate();
        assertEquals(4, captcha.code().length());
        assertDoesNotThrow(() -> service.verify(captcha.token(), captcha.code()));
    }

    @Test
    void verifyIsCaseInsensitiveAndTrims() {
        CaptchaService.Captcha captcha = service.generate();
        assertDoesNotThrow(() -> service.verify(captcha.token(), "  " + captcha.code().toLowerCase() + " "));
    }

    @Test
    void wrongCodeRejected() {
        CaptchaService.Captcha captcha = service.generate();
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verify(captcha.token(), "ZZZZ"));
        assertEquals("验证码错误", e.getMessage());
    }

    @Test
    void reusedTokenRejectedEvenWithCorrectCode() {
        CaptchaService.Captcha captcha = service.generate();
        assertDoesNotThrow(() -> service.verify(captcha.token(), captcha.code()));
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verify(captcha.token(), captcha.code()));
        assertEquals("验证码已使用，请获取新的验证码", e.getMessage());
    }

    @Test
    void expiredCodeRejected() {
        CaptchaService.Captcha captcha = service.generate();
        clock.advance(Duration.ofMinutes(6));
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verify(captcha.token(), captcha.code()));
        assertEquals("验证码已过期，请获取新的验证码", e.getMessage());
    }

    @Test
    void unknownTokenRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verify("no-such-token", "ABCD"));
        assertEquals("验证码无效，请获取新的验证码", e.getMessage());
        assertThrows(BusinessException.class, () -> service.verify(null, "ABCD"));
    }

    @Test
    void generatedCodesDifferOverTime() {
        String first = service.generate().code();
        String second = service.generate().code();
        String third = service.generate().code();
        assertNotEquals(first + second, second + third);
    }
}
