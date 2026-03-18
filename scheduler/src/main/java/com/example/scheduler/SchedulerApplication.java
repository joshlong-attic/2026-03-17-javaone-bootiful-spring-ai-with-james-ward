package com.example.scheduler;

import org.antlr.runtime.misc.IntArray;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer.mcpServerOAuth2;

@SpringBootApplication
public class SchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
    }


    @Bean
    Customizer<HttpSecurity> httpSecurityCustomizer(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
                                                    String uri) {
        return http -> http
                .with(mcpServerOAuth2(), mcp -> mcp.authorizationServer(uri));
    }
}

@Component
class DogAdoptionScheduler {

    @McpTool(description = "schedule an appointment ot pick up or adopt a dog from a Pooch Palace location")
    DogAdoptionAppointmentSuggestion suggestion(
            @McpToolParam(description = "the id of the dog") int dogId,
            @McpToolParam(description = "the name of the dog") String dogName) {
        var when = Instant
                .now()
                .plus(3, ChronoUnit.DAYS);
        var s = new DogAdoptionAppointmentSuggestion(when,
                SecurityContextHolder
                        .getContextHolderStrategy()
                        .getContext()
                        .getAuthentication()
                        .getName());
        IO.println("returning suggested time for " + dogName + '/' + dogId + ": " + s);
        return s;
    }
}

record DogAdoptionAppointmentSuggestion(
        Instant when, String user
) {
}