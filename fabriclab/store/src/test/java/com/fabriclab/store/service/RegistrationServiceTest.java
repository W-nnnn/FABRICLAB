package com.fabriclab.store.service;

import com.fabriclab.store.entity.StoreUser;
import com.fabriclab.store.exception.BusinessException;
import com.fabriclab.store.mapper.StoreUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 注册服务：账号口径、唯一性、验证码先于落库、BCrypt 存储。 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private StoreUserMapper storeUserMapper;

    @Mock
    private CaptchaService captchaService;

    @InjectMocks
    private RegistrationService service;

    @Test
    void createsCustomerAccountWithBcryptHash() {
        when(storeUserMapper.selectCount(any())).thenReturn(0L);

        StoreUser user = service.register("  newuser  ", "abcd1234", "abcd1234", "tok", "AB2C");

        assertEquals("newuser", user.getUsername());
        assertEquals(RegistrationService.ROLE_CUSTOMER, user.getRole());
        assertEquals(RegistrationService.STATUS_ENABLED, user.getStatus());
        assertEquals("newuser", user.getDisplayName());
        assertTrue(user.getPasswordHash().startsWith("$2"), "密码必须以 BCrypt 散列存储");
        assertNotEquals("abcd1234", user.getPasswordHash());
        assertTrue(user.getPasswordHash().length() > 50, "散列长度应明显大于明文");

        InOrder order = inOrder(captchaService, storeUserMapper);
        order.verify(captchaService).verify("tok", "AB2C");
        order.verify(storeUserMapper).selectCount(any());
        order.verify(storeUserMapper).insert(user);
    }

    @Test
    void duplicateUsernameRejectedBeforeInsert() {
        when(storeUserMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.register("alice", "abcd1234", "abcd1234", "tok", "AB2C"));
        assertEquals("用户名已被使用", e.getMessage());
        verify(storeUserMapper, never()).insert(any(StoreUser.class));
    }

    @Test
    void duplicateKeyRaceAlsoMapsToFriendlyMessage() {
        when(storeUserMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_user_username")).when(storeUserMapper).insert(any(StoreUser.class));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.register("alice", "abcd1234", "abcd1234", "tok", "AB2C"));
        assertEquals("用户名已被使用", e.getMessage());
    }

    @Test
    void invalidInputsRejectedWithoutCaptchaConsumption() {
        assertThrows(BusinessException.class, () -> service.register(" ", "abcd1234", "abcd1234", "tok", "AB2C"));
        assertThrows(BusinessException.class, () -> service.register("u".repeat(51), "abcd1234", "abcd1234", "tok", "AB2C"));
        assertThrows(BusinessException.class, () -> service.register("bob", "short1", "short1", "tok", "AB2C"));
        assertThrows(BusinessException.class, () -> service.register("bob", "abcdefgh", "abcdefgh", "tok", "AB2C"));
        assertThrows(BusinessException.class, () -> service.register("bob", "abcd1234", "abcd9999", "tok", "AB2C"));

        verify(captchaService, never()).verify(anyString(), anyString());
        verify(storeUserMapper, never()).insert(any(StoreUser.class));
    }

    @Test
    void captchaFailureStopsRegistration() {
        org.mockito.Mockito.doThrow(new BusinessException("验证码错误"))
                .when(captchaService).verify(eq("tok"), eq("BAD!"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.register("carol", "abcd1234", "abcd1234", "tok", "BAD!"));
        assertEquals("验证码错误", e.getMessage());
        verify(storeUserMapper, never()).insert(any(StoreUser.class));
    }

    @Test
    void storedHashMatchesBcryptFormat() {
        when(storeUserMapper.selectCount(any())).thenReturn(0L);
        service.register("dave", "Passw0rd!", "Passw0rd!", "tok", "AB2C");

        ArgumentCaptor<StoreUser> captor = ArgumentCaptor.forClass(StoreUser.class);
        verify(storeUserMapper).insert(captor.capture());
        String hash = captor.getValue().getPasswordHash();
        assertTrue(hash.matches("\\$2[aby]\\$10\\$[./A-Za-z0-9]{53}"), "应为标准 BCrypt 散列：" + hash.length());
    }
}
