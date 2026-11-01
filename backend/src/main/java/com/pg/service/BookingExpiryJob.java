package com.pg.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every 10 minutes: booking requests the owner never answered give their bed back.
 * Each request is expired in its OWN transaction (see RoomBookingService.expireOne), because one
 * long transaction would hold locks on many rooms and could deadlock with other workflows.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryJob {

    private final RoomBookingService bookingService;

    @Scheduled(cron = "0 */10 * * * *")
    public void run() {
        int expired = 0;
        for (Long id : bookingService.expiredIds()) {
            try {
                if (bookingService.expireOne(id)) expired++;
            } catch (RuntimeException e) {
                // one bad request must not stop the others
                log.error("Could not expire booking {}", id, e);
            }
        }
        if (expired > 0) log.info("Expired {} unanswered booking requests", expired);
    }
}
