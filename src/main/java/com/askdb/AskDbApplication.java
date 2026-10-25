package com.askdb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AskDbApplication {

    public static void main(String[] args) {
        SpringApplication.run(AskDbApplication.class, args);
    }
}
