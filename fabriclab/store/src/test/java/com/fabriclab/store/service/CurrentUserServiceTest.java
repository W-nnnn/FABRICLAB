package com.fabriclab.store.service;

import com.fabriclab.store.exception.NotFoundException;
import com.fabriclab.store.model.CurrentUser;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 会话身份读取与数据归属校验（FR-U-02、AC-18 机制）。 */
class CurrentUserServiceTest {

    private final CurrentUserService service = new CurrentUserService();

    private MockHttpSession sessionWith(long id) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.KEY_USER_ID, id);
        session.setAttribute(CurrentUserService.KEY_USERNAME, "alice");
        session.setAttribute(CurrentUserService.KEY_DISPLAY_NAME, "林晓");
        return session;
    }

    @Test
    void readsIdentityFromSession() {
        CurrentUser current = service.from(sessionWith(7L));
        assertEquals(7L, current.id());
        assertEquals("alice", current.username());
        assertEquals("林晓", current.displayName());
    }

    @Test
    void missingOrIncompleteSessionYieldsNull() {
        assertNull(service.from(null));
        assertNull(service.from(new MockHttpSession()));
        MockHttpSession partial = new MockHttpSession();
        partial.setAttribute(CurrentUserService.KEY_USER_ID, 7L);
        assertNull(service.from(partial));
    }

    @Test
    void ownershipMismatchOrMissingIs404WithoutLeak() {
        CurrentUser alice = service.from(sessionWith(7L));
        service.requireOwner(alice, 7L);
        assertThrows(NotFoundException.class, () -> service.requireOwner(alice, 8L));
        assertThrows(NotFoundException.class, () -> service.requireOwner(alice, null));
        assertThrows(NotFoundException.class, () -> service.requireOwner(null, 7L));
    }
}
