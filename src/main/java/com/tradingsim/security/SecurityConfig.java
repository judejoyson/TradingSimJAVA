package com.tradingsim.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

@Configuration
public class SecurityConfig {
    private final boolean requireHttps;

    public SecurityConfig(
            @Value("${tradingsim.security.require-https:false}") boolean requireHttps) {
        this.requireHttps = requireHttps;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfCookieFilter csrfCookieFilter,
            ApiRateLimitFilter rateLimitFilter) throws Exception {
        CookieCsrfTokenRepository csrfRepository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");

        http
                .requiresChannel(channel -> {
                    if (requireHttps) {
                        channel.anyRequest().requiresSecure();
                    }
                })
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/", "/index.html", "/chart.html", "/login.html",
                                "/register.html", "/backtest.html", "/simulator.css", "/home.js",
                                "/backtest.js", "/sample-backtest-data.csv",
                                "/chart.js", "/trade-plan-validation.js",
                                "/login.js", "/register.js",
                                "/api/replay/**", "/api/auth/register",
                                "/api/backtests", "/api/backtests/options",
                                "/api/backtests/csv",
                                "/error", "/favicon.ico")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(login -> login
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/backtest.html", true)
                        .failureUrl("/login.html?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' https://unpkg.com; "
                                        + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                                        + "connect-src 'self'; font-src 'self'; "
                                        + "frame-ancestors 'none'; form-action 'self'; "
                                        + "base-uri 'self'; object-src 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=()"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)))
                .exceptionHandling(errors -> errors
                        .defaultAuthenticationEntryPointFor(
                                (request, response, exception) ->
                                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/")));

        http.addFilterAfter(csrfCookieFilter, CsrfFilter.class);
        http.addFilterAfter(rateLimitFilter, CsrfCookieFilter.class);
        return http.build();
    }
}
