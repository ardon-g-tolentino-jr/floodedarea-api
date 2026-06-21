package com.humanworkstream.baha.security;

import com.humanworkstream.baha.enumeration.UserRole;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the {@code Authorization: Bearer <jwt>} header, validates it, and populates the
 * SecurityContext with a {@link UserPrincipal} plus a {@code ROLE_*} authority so method/URL
 * authorization (e.g. {@code hasRole("ADMIN")}) works. No header → anonymous request.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isValid(token)) {
                Claims claims = jwtUtil.parse(token);
                UserRole role = parseRole(claims.get("role", String.class));
                UserPrincipal principal = new UserPrincipal(
                        claims.getSubject(),
                        claims.get("userId", Integer.class),
                        role);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private static UserRole parseRole(String raw) {
        try {
            return raw == null ? UserRole.CITIZEN : UserRole.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return UserRole.CITIZEN;
        }
    }
}
