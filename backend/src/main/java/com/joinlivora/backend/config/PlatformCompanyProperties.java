package com.joinlivora.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "platform.company")
@Getter
@Setter
public class PlatformCompanyProperties {
    private String name = "Livora Platform";
    private String address = "123 Tech Avenue, Brussels, Belgium";
    private String vatNumber = "BE0123456789";
    private String email = "billing@joinlivora.com";
    private String website = "https://joinlivora.com";
}
