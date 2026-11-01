package com.pg.security;

import com.pg.entity.User;
import com.pg.entity.enums.Role;
import com.pg.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtFilterTest {

    @Mock JwtUtil jwtUtil;
    @Mock UserRepository userRepository;
    @Mock FilterChain chain;
    @InjectMocks JwtFilter filter;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc");
        when(jwtUtil.isTokenValid("abc")).thenReturn(true);
        when(jwtUtil.extractEmail("abc")).thenReturn("t@example.com");
        return request;
    }

    @Test
    void checkedOutUserWithValidTokenIsNotAuthenticated() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        User inactive = User.builder().email("t@example.com").role(Role.TENANT).isActive(false).build();
        when(userRepository.findByEmail("t@example.com")).thenReturn(Optional.of(inactive));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void deletedUserWithValidTokenIsNotAuthenticated() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        when(userRepository.findByEmail("t@example.com")).thenReturn(Optional.empty());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void activeUserGetsTheRoleFromTheDatabaseNotFromTheToken() throws Exception {
        MockHttpServletRequest request = requestWithToken();
        when(jwtUtil.extractRole("abc")).thenReturn("SUPER_ADMIN"); // stale / forged claim
        User tenant = User.builder().email("t@example.com").role(Role.TENANT).isActive(true).build();
        when(userRepository.findByEmail("t@example.com")).thenReturn(Optional.of(tenant));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("ROLE_TENANT", auth.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void requestWithoutTokenStaysAnonymous() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepository, never()).findByEmail(any());
    }
}
