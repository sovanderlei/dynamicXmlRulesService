package com.example.dynamicxmlrules;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DynamicXmlRulesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DynamicXmlRulesServiceApplication.class, args);
    }
}
