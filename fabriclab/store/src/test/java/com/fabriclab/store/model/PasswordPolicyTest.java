package com.fabriclab.store.model;

import com.fabriclab.store.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 注册密码策略：8—32 个字符且至少两类。 */
class PasswordPolicyTest {

    @Test
    void acceptsTwoOrMoreCharacterClassesWithinLength() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("abcd1234"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("Abcdefg1"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("abcdefgh!"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("a".repeat(8) + "1"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("A1" + "x".repeat(30)));
    }

    @Test
    void rejectsTooShortAndTooLong() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("a1" + "x".repeat(5)));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(null));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("A1" + "x".repeat(31)));
    }

    @Test
    void rejectsSingleCharacterClass() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("abcdefgh"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("12345678"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("!@#$%^&*("));
    }

    @Test
    void messageNeverContainsPassword() {
        try {
            PasswordPolicy.validate("short1");
        } catch (BusinessException e) {
            assertDoesNotThrow(() -> {
                if (e.getMessage().contains("short1")) {
                    throw new AssertionError("异常信息不得包含密码明文");
                }
            });
        }
    }
}
