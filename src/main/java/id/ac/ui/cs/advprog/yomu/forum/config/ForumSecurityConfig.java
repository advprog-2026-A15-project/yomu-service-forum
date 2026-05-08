package id.ac.ui.cs.advprog.yomu.forum.config;

import id.ac.ui.cs.advprog.yomu.shared.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration untuk service-forum.
 * Mengaktifkan JWT authentication dan method-level security.
 * 
 * Policies:
 * - GET /api/forum/comments: Publik (semua bisa baca)
 * - GET /api/forum/comments/tree: Publik (semua bisa baca)
 * - POST /api/forum/comments: Authenticated (semua yang login bisa buat)
 * - PUT /api/forum/comments/{id}: Authenticated + Custom authorization (hanya author atau admin)
 * - DELETE /api/forum/comments/{id}: Authenticated + Custom authorization (hanya author atau admin)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class ForumSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.GET, "/api/forum/comments/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/forum/comments/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/forum/comments/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/forum/comments/**").authenticated()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
