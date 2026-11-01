package com.pg.security;

import com.pg.controller.AuthController;
import com.pg.dto.request.LoginRequest;
import com.pg.dto.response.AuthResponse;
import com.pg.exception.BusinessException;
import com.pg.exception.TooManyRequestsException;
import com.pg.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoginRateLimiterTest {

    /** A clock the test can move forward. */
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-01-01T10:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    MutableClock clock;
    LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new LoginRateLimiter(clock);
    }

    /** n attempts that all count (as failed logins do: nobody calls recordSuccess). */
    private void attempts(String email, String ip, int n) {
        for (int i = 0; i < n; i++) limiter.acquire(email, ip);
    }

    @Test
    void fiveAttemptsAreAllowedThenThatEmailAndIpIsBlocked() {
        attempts("a@x.com", "1.1.1.1", 5);
        assertThrows(TooManyRequestsException.class, () -> limiter.acquire("A@x.com ", "1.1.1.1")); // case/spaces ignored
        assertDoesNotThrow(() -> limiter.acquire("a@x.com", "2.2.2.2")); // other network is fine
        assertDoesNotThrow(() -> limiter.acquire("b@x.com", "1.1.1.1")); // other account is fine
    }

    @Test
    void fourAttemptsAreStillAllowedAndTheFifthToo() {
        assertDoesNotThrow(() -> attempts("a@x.com", "1.1.1.1", 5));
    }

    @Test
    void successGivesTheAttemptsBack() {
        attempts("a@x.com", "1.1.1.1", 4);
        limiter.recordSuccess("a@x.com", "1.1.1.1");
        assertDoesNotThrow(() -> attempts("a@x.com", "1.1.1.1", 5)); // a fresh 5
    }

    @Test
    void blockEndsWhenTheWindowExpires() {
        attempts("a@x.com", "1.1.1.1", 5);
        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));
        assertDoesNotThrow(() -> limiter.acquire("a@x.com", "1.1.1.1"));
    }

    @Test
    void blockedRequestsDoNotExtendTheBlockForever() {
        attempts("a@x.com", "1.1.1.1", 5);
        for (int i = 0; i < 20; i++) {
            assertThrows(TooManyRequestsException.class, () -> limiter.acquire("a@x.com", "1.1.1.1"));
        }
        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));
        assertDoesNotThrow(() -> limiter.acquire("a@x.com", "1.1.1.1")); // spam while blocked did not reset the clock
    }

    // ------------------------------------------------------------ concurrency

    private static final class Outcome { int allowed; int refused; }

    /** Fires `threads` calls at the same instant; returns how many got through and how many were refused. */
    private Outcome fireTogether(int threads, Callable<Void> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try { call.call(); allowed.incrementAndGet(); }
                catch (TooManyRequestsException e) { refused.incrementAndGet(); }
                catch (BusinessException e) { allowed.incrementAndGet(); } // wrong password: it did get through the limiter
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        Outcome o = new Outcome();
        o.allowed = allowed.get();
        o.refused = refused.get();
        return o;
    }

    @Test
    void fiftySimultaneousAttemptsLetExactlyFiveThrough() throws Exception {
        for (int round = 0; round < 20; round++) { // repeat: races are probabilistic
            LoginRateLimiter fresh = new LoginRateLimiter(clock);
            Outcome o = fireTogether(50, () -> { fresh.acquire("a@x.com", "1.1.1.1"); return null; });
            assertEquals(LoginRateLimiter.MAX_FAILURES, o.allowed, "round " + round);
            assertEquals(50 - LoginRateLimiter.MAX_FAILURES, o.refused, "round " + round);
        }
    }

    @Test
    void simultaneousFailedLoginRequestsReachThePasswordCheckAtMostFiveTimes() throws Exception {
        // the real controller + real limiter; only the password check is faked (it always fails)
        AuthService authService = mock(AuthService.class);
        AtomicInteger passwordChecks = new AtomicInteger();
        when(authService.login(any(LoginRequest.class))).thenAnswer(i -> {
            passwordChecks.incrementAndGet();
            throw new BusinessException("Invalid email or password");
        });
        AuthController controller = new AuthController(authService, limiter);
        HttpServletRequest http = mock(HttpServletRequest.class);
        when(http.getRemoteAddr()).thenReturn("9.9.9.9");
        LoginRequest req = new LoginRequest();
        req.setEmail("victim@x.com");
        req.setPassword("guess");

        Outcome o = fireTogether(40, () -> { controller.login(req, http); return null; });

        assertEquals(LoginRateLimiter.MAX_FAILURES, passwordChecks.get()); // never more than 5 guesses
        assertEquals(40 - LoginRateLimiter.MAX_FAILURES, o.refused);
    }

    @Test
    void successfulLoginsRunTogetherWithoutBlockingEachOther() throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.login(any(LoginRequest.class))).thenReturn(AuthResponse.builder().token("t").build());
        AuthController controller = new AuthController(authService, limiter);
        HttpServletRequest http = mock(HttpServletRequest.class);
        when(http.getRemoteAddr()).thenReturn("9.9.9.9");
        LoginRequest req = new LoginRequest();
        req.setEmail("ok@x.com");
        req.setPassword("right");

        // 4 people behind one office IP log in at once; each success hands its attempt back
        Outcome o = fireTogether(4, () -> { controller.login(req, http); return null; });

        assertEquals(4, o.allowed);
        assertEquals(0, o.refused);
    }

    // ------------------------------------------------------------ memory bound

    @Test
    void memoryIsStrictlyBoundedEvenWhenNothingHasExpired() {
        for (int i = 0; i < LoginRateLimiter.MAX_ENTRIES * 2; i++) {
            limiter.acquire("user" + i + "@x.com", "1.1.1.1");
            assertTrue(limiter.size() <= LoginRateLimiter.MAX_ENTRIES, "size was " + limiter.size() + " at i=" + i);
        }
    }

    @Test
    void memoryStaysBoundedWhenManyThreadsFloodWithNewKeys() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger maxSeen = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            final int id = t;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < 5_000; i++) {
                    limiter.acquire("flood" + id + "-" + i + "@x.com", "1.1.1.1");
                    maxSeen.accumulateAndGet(limiter.size(), Math::max);
                }
            }));
        }
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(maxSeen.get() <= LoginRateLimiter.MAX_ENTRIES, "max size seen: " + maxSeen.get());
    }

    @Test
    void floodingCanNotEvictSomeoneWhoIsAlreadyBlocked() {
        attempts("attacker@x.com", "9.9.9.9", 5); // blocked
        for (int i = 0; i < LoginRateLimiter.MAX_ENTRIES * 2; i++) {
            limiter.acquire("flood" + i + "@x.com", "1.1.1.1");
        }
        assertThrows(TooManyRequestsException.class, () -> limiter.acquire("attacker@x.com", "9.9.9.9"));
    }

    @Test
    void scheduledSweepRemovesExpiredEntriesEvenWithLowTraffic() {
        for (int i = 0; i < 50; i++) limiter.acquire("user" + i + "@x.com", "1.1.1.1");
        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));
        limiter.acquire("fresh@x.com", "1.1.1.1");
        assertEquals(51, limiter.size());

        limiter.sweep(); // what the 5-minute schedule calls

        assertEquals(1, limiter.size());
    }

    @Test
    void sweepKeepsEntriesThatAreStillActive() {
        attempts("old@x.com", "1.1.1.1", 5);
        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));
        attempts("recent@x.com", "1.1.1.1", 5);
        limiter.sweep();
        assertThrows(TooManyRequestsException.class, () -> limiter.acquire("recent@x.com", "1.1.1.1"));
        assertDoesNotThrow(() -> limiter.acquire("old@x.com", "1.1.1.1")); // expired, swept
    }
}
