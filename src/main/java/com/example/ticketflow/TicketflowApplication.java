package com.example.ticketflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class TicketflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketflowApplication.class, args);
    }

}
