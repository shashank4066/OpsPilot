package com.opspilot.incident.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opspilot.incident.dto.CreateIncidentRequest;
import com.opspilot.incident.enums.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Incident REST API.
 * 
 * @SpringBootTest loads the full application context.
 * @AutoConfigureMockMvc provides MockMvc for HTTP testing without a real server.
 * 
 * These tests verify the entire stack: Controller → Service → Repository → H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IncidentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/incidents - should create and return 201")
    void createIncident() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest();
        request.setTitle("API Test Incident");
        request.setServiceName("test-service");
        request.setEnvironment("production");
        request.setSeverity(Severity.SEV2);

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("API Test Incident"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.incidentNumber").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/incidents - should return 400 for missing fields")
    void createIncidentValidation() throws Exception {
        // Missing all required fields
        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/incidents - should return list")
    void listIncidents() throws Exception {
        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id} - should return 404 for non-existent")
    void getIncidentNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/health - should return UP")
    void healthCheck() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("incident-service"));
    }

    @Test
    @DisplayName("PATCH status - should reject invalid transition OPEN → MITIGATING")
    void invalidStatusTransition() throws Exception {
        // First create an incident
        CreateIncidentRequest request = new CreateIncidentRequest();
        request.setTitle("Transition Test");
        request.setServiceName("test-svc");
        request.setEnvironment("prod");
        request.setSeverity(Severity.SEV3);

        String createResponse = mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract the incident ID
        String id = objectMapper.readTree(createResponse).get("id").asText();

        // Try invalid transition: OPEN → MITIGATING
        mockMvc.perform(patch("/api/v1/incidents/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"MITIGATING\"}"))
                .andExpect(status().isConflict());
    }
}
