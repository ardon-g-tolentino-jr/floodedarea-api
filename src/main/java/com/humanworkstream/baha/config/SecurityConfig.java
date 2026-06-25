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
                // ── Public reads (anyone can view the flood map / shelters / hotlines / alerts / road conditions) ──
                .requestMatchers(HttpMethod.GET, "/reports/**", "/centers/**", "/hotlines/**", "/alerts/**", "/road-status").permitAll()
                // ── SSE live activity feed — public read ──
                .requestMatchers(HttpMethod.GET, "/sse/**").permitAll()
                // ── Health & error — public ──
                .requestMatchers("/healthcheck", "/error").permitAll()
                // ── Visit counter — public read + public increment (no sign-in; fires on page load) ──
                .requestMatchers(HttpMethod.GET,  "/stats/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/stats/visit").permitAll()
                // ── Admin-only management writes ──
                .requestMatchers(HttpMethod.POST,   "/centers/**", "/hotlines/**", "/alerts/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/centers/**", "/hotlines/**", "/alerts/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,  "/centers/**", "/hotlines/**", "/alerts/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/centers/**", "/hotlines/**", "/alerts/**").hasRole("ADMIN")
                // ── Flood reports: PATCH (resolve / reactivate) is admin. DELETE just needs a login;
                //     the service enforces owner-or-admin so a reporter can remove their own report. ──
                .requestMatchers(HttpMethod.PATCH,  "/reports/**").hasRole("ADMIN")
                // ── Rescue requests: any signed-in user can view the queue (powers the
                //     per-district chart); dispatching (PATCH/DELETE) stays admin; POST needs login ──
                .requestMatchers(HttpMethod.GET,    "/rescue/**").authenticated()
                .requestMatchers(HttpMethod.PATCH,  "/rescue/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/rescue/**").hasRole("ADMIN")
                // ── Feedback: submitting + reading accepted features needs a login; the moderation
                //     queue and accept/decline are admin ──
                .requestMatchers(HttpMethod.GET,    "/feedback/accepted").authenticated()
                .requestMatchers(HttpMethod.POST,   "/feedback").authenticated()
                .requestMatchers(HttpMethod.GET,    "/feedback").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,  "/feedback/**").hasRole("ADMIN")
                // ── Everything else (POST /reports, POST /reports/{id}/confirm, POST /rescue,
                //     GET /auth/me) requires a valid JWT ──
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
