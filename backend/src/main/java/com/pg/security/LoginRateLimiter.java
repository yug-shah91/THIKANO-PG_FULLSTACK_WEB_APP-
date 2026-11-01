package com.pg.security;

import com.pg.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Blocks a (email + IP) pair after too many wrong passwords in a short time.
 *
 * HOW IT STAYS CORRECT WHEN REQUESTS ARRIVE AT THE SAME TIME
 *  The old version did "check the counter" and "add a failure" as two separate steps, so ten
 *  simultaneous requests could all pass the check before any of them was counted.
 *  Now there is ONE step, {@link #acquire}: it checks AND counts the attempt in the same locked
 *  section, BEFORE the password is verified. So at most MAX_FAILURES attempts per window ever reach
 *  the password check, however many arrive at once. A successful login calls {@link #recordSuccess},
 *  which gives the attempts back.
 *
 * HOW MEMORY STAYS BOUNDED
 *  All reads and writes of the map happen inside the same lock, and a new key is only added after
 *  making room, so the map can NEVER hold more than MAX_ENTRIES keys (not even for a moment).
 *  One short lock is fine here: the slow part (BCrypt) runs outside it.
 *
 * Kept in memory, so it resets on restart and is per server instance. For several servers, move the
 * counters to Redis or the database.
 * Keyed by email AND IP so a stranger cannot lock a real user out from another network.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_ENTRIES = 10_000; // hard limit, enforced inside the lock

    private record Attempts(int count, Instant first) {}

    private final Map<String, Attempts> attempts = new HashMap<>(); // guarded by "this"
    private final Clock clock;

    @Autowired
    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    // tests pass their own clock so they can move time forward
    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Call BEFORE checking the password. Counts this attempt, or throws if the limit is already
     * reached. Check and count happen together, so simultaneous requests can not slip past.
     */
    public synchronized void acquire(String email, String ip) {
        String key = key(email, ip);
        Instant now = clock.instant();
        Attempts a = attempts.get(key);

        if (a == null || expired(a, now)) {
            if (a == null) makeRoomForOneMore(now);
            attempts.put(key, new Attempts(1, now));
            return;
        }
        if (a.count() >= MAX_FAILURES) {
            throw new TooManyRequestsException("Too many failed logins. Try again in a few minutes.");
        }
        attempts.put(key, new Attempts(a.count() + 1, a.first()));
    }

    /** Correct password: forget the attempts of this email + IP. */
    public synchronized void recordSuccess(String email, String ip) {
        attempts.remove(key(email, ip));
    }

    /** Every 5 minutes: forget entries whose 15-minute window is over, even when traffic is low. */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public synchronized void sweep() {
        Instant now = clock.instant();
        attempts.values().removeIf(a -> expired(a, now));
    }

    synchronized int size() {
        return attempts.size();
    }

    /**
     * Called (inside the lock) before a NEW key is added. If the map is full: drop expired entries;
     * if still full, drop the least dangerous ones (lowest count, then oldest) down to 90% of the
     * limit, so this sorting does not run on every request. Blocked entries (5 failures) go last.
     */
    private void makeRoomForOneMore(Instant now) {
        if (attempts.size() < MAX_ENTRIES) return;
        attempts.values().removeIf(a -> expired(a, now));
        if (attempts.size() < MAX_ENTRIES) return;
        int toRemove = attempts.size() - MAX_ENTRIES * 9 / 10;
        attempts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Attempts> e) -> e.getValue().count())
                        .thenComparing(e -> e.getValue().first()))
                .limit(toRemove).map(Map.Entry::getKey).toList()
                .forEach(attempts::remove);
    }

    private static boolean expired(Attempts a, Instant now) {
        return a.first().plus(WINDOW).isBefore(now);
    }

    private static String key(String email, String ip) {
        return (email == null ? "" : email.trim().toLowerCase()) + "|" + ip;
    }
}
