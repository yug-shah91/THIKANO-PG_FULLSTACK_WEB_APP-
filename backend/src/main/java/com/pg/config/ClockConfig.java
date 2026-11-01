package com.pg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** One shared clock, so tests can replace "now" and check expiry boundaries exactly. */
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone(); // the JVM zone is set from APP_TIMEZONE (see PgApiApplication)
    }
}
