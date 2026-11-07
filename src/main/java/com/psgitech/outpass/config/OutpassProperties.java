package com.psgitech.outpass.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "outpass")
@Getter @Setter
public class OutpassProperties {
    private String collegeName = "PSG Institute of Technology and Applied Research";
    private String baseUrl = "http://localhost:8080";
    private int maxDaysAhead = 2;
    private int maxRevisions = 2;
    private int validityMinutes = 75;
    private int scanEarlyMinutes = 120;
    private String studentDefaultPassword = "test@123";
    private String staffDefaultPassword = "NonStudent@credential";
    private boolean seedTestAccounts = true;
    private boolean demoMode = false;
    private int demoResetMinutes = 30;
    private String googleClientId = "";
    private String googleClientSecret = "";
    private String googleDomain = "psgitech.ac.in";
    private String photoDir = "./photos";
    private String importDir = "./data";
    private boolean mailEnabled = false;
    private String mailFrom = "no-reply@localhost";
    private String adminName = "System Administrator";
    private String adminEmail = "admin@psgitech.local";
    private String adminPassword = "";
}
