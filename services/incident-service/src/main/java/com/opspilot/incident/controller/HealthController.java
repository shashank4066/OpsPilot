package com.opspilot.incident.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController                                          // Marks this CLASS as a REST controller
public class HealthController {

    @GetMapping("/api/v1/health")                        // Maps GET requests for this URL to this METHOD
    public Map<String, Object> getHealth() {             // METHOD declaration: returns a Map, named getHealth
        Map<String, Object> health = new HashMap<>();    // VARIABLE: creates a new empty Map inside the method
        health.put("service", "incident-service");       // Adds key-value pair to the Map
        health.put("status", "UP");                      // Adds key-value pair to the Map
        health.put("timestamp", LocalDateTime.now());    // Adds current time to the Map
        return health;                                   // Returns the Map — Spring converts it to JSON
    }
}