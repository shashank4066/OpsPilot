package com.opspilot.incident;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Incident Service.
 *
 * @SpringBootApplication is a shortcut that combines three annotations:
 * 
 *   1. @Configuration     — This class can define Spring beans (objects managed by Spring)
 *   2. @EnableAutoConfiguration — Spring Boot automatically configures things based on 
 *                                  dependencies (e.g., it sees PostgreSQL driver → configures DataSource)
 *   3. @ComponentScan     — Spring scans this package and sub-packages for classes annotated 
 *                           with @Controller, @Service, @Repository, etc. and registers them
 *
 * When you run this main() method:
 *   1. Spring Boot starts
 *   2. It scans for components in com.opspilot.incident and sub-packages
 *   3. It auto-configures based on dependencies in pom.xml
 *   4. It starts the embedded Tomcat server
 *   5. Your application is ready to receive HTTP requests
 */
@SpringBootApplication
public class IncidentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentServiceApplication.class, args);
    }
}
