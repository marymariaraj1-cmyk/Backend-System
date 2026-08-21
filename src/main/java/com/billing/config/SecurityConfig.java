package com.billing.config;

import com.billing.security.JsonAccessDeniedHandler;
import com.billing.security.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CustomUserDetailsService customUserDetailsService) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/*.js", "/*.css", "/favicon.ico").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/me", "/api/i18n/**", "/error").permitAll()
                .requestMatchers("/api/clients/**").hasRole("ADMIN")
                .requestMatchers("/api/flowers/**", "/api/farmers/**", "/api/buyers/**", "/api/opening-balance/**",
                        "/api/sales/**", "/api/sales-edit/**", "/api/multi-sales/**",
                        "/api/farmer-transaction/**", "/api/buyer-transaction/**",
                        "/api/farmer-account-check/**").hasRole("CLIENT")
                .anyRequest().permitAll())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                .accessDeniedHandler(new JsonAccessDeniedHandler()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .userDetailsService(customUserDetailsService)
            .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
