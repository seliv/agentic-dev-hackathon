package com.chatapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;

@Configuration
public class SessionConfig {

    @Bean
    public SessionRepositoryCustomizer<JdbcIndexedSessionRepository> postgreSqlSessionRepositoryCustomizer() {
        return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
    }

}
