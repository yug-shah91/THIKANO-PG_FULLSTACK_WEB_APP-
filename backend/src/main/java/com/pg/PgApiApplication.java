package com.pg;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(
    info = @Info(
        title = "PG Management SaaS API",
        version = "1.0",
        description = "Multi-tenant REST API for PG / Hostel management. " +
                      "Automated rent generation, penalty calculation, complaint tracking."
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class PgApiApplication {
    // One explicit timezone for the whole app: "today", visit times, rent due dates and the
    // scheduled jobs all use it. Change with APP_TIMEZONE.
    @jakarta.annotation.PostConstruct
    void setTimezone() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(
                System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Kolkata")));
    }

    public static void main(String[] args) {
        SpringApplication.run(PgApiApplication.class, args);
    }
}
