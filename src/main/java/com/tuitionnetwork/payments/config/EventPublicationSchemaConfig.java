package com.tuitionnetwork.payments.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class EventPublicationSchemaConfig {

    @Bean
    public ApplicationRunner initializeEventPublicationSchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE event_publication ALTER COLUMN serialized_event VARCHAR(65535)");
            } catch (Exception ignored) {
                // Table might not exist yet or column type already configured
            }
        };
    }
}
