package com.example.bff.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;

// CSRF token in cookie (XSRF-TOKEN) readable by JS, sent back in header (X-CSRF-TOKEN)
@Slf4j
@Configuration
public class CsrfConfig {

    @Bean
    public ServerCsrfTokenRepository csrfTokenRepository(BffProperties properties) {
        BffProperties.Session sessionProps = properties.getSession();

        CookieServerCsrfTokenRepository repository =
                CookieServerCsrfTokenRepository.withHttpOnlyFalse();

        repository.setCookieName(sessionProps.getCsrfCookieName());
        repository.setHeaderName(sessionProps.getCsrfHeaderName());
        repository.setParameterName("_csrf");
        repository.setCookiePath("/");

        // Apply security settings matching session cookie
        repository.setCookieCustomizer(cookie -> cookie
                .domain(sessionProps.getCookieDomain())
                .secure(sessionProps.isCookieSecure())
                .sameSite(sessionProps.getCookieSameSite()));

        log.info("CSRF protection configured: cookie={}, header={}, domain={}",
                sessionProps.getCsrfCookieName(),
                sessionProps.getCsrfHeaderName(),
                sessionProps.getCookieDomain());

        return repository;
    }
}
