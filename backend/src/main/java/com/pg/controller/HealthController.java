package com.pg.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Tiny open endpoint (GET /api/public/health). The website calls it to find out whether the
 * server is awake: free hosting puts idle servers to sleep, and waking one takes about a minute.
 * It does not touch the database. /api/public/** is already open in SecurityConfig.
 */
@RestController
@RequestMapping("/api/public")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
