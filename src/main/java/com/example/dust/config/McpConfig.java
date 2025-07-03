package com.example.dust.config;

import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.dust.service.DustService;

@Configuration
public class McpConfig {

    @Bean
    public MethodToolCallbackProvider toolProvider(DustService dustService) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(dustService)
            .build();
    }
}
