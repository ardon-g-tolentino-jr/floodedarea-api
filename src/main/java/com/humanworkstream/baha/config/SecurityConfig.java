package com.humanworkstream.baha.config;

import com.humanworkstream.baha.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security. Reads (the map data) are public; writes require a login, and
 * management writes (centers / hotlines / alerts / report resolution / rescue dispatch)
 * require the ADMIN role.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ── Auth — public ──
                .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/google", "/auth/register").permitAll()
                // ── Public reads (anyone can view the flood map / shelters / hotlines / alerts) ──
                .requestMatchers(HttpMethod.GET, "/reports/**", "/centers/**", "/hotlines/**", "/alerts/**").permitAll()
                // ── SSE live activity feed — public read ──
                .requestMatchers(HttpMethod.GET, "/sse/**").permitAll()
                // ── Health & error — public ──
                .requestMatchers("/healthcheck", "/error").permitAll()
                // ── Admin-only management writes ──
                .requestMatchers(HttpMethod.POST,   "/centers/**", "/hotlines/**", "/alerts/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/centers/**", "/hotlines/**", "/alerts/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,  "/centers/**", "/hotlines/**", "/alerts/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/centers/**", "/hotlines/**", "/alerts/**").hasRole("ADMIN")
                // ── Flood reports: PATCH/DELETE are admin (resolve / remove); POST + confirm need login ──
                .requestMatchers(HttpMethod.PATCH,  "/reports/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/reports/**").hasRole("ADMIN")
                // ── Rescue requests: viewing/dispatching the queue is admin; POST /rescue needs login ──
                .requestMatchers(HttpMethod.GET,    "/rescue/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,  "/rescue/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/rescue/**").hasRole("ADMIN")
                // ── Everything else (POST /reports, POST /reports/{id}/confirm, POST /rescue,
                //     GET /auth/me) requires a valid JWT ──
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
