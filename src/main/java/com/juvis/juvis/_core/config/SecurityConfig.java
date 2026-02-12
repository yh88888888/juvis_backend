package com.juvis.juvis._core.config;

import com.juvis.juvis._core.error.Jwt401Handler;
import com.juvis.juvis._core.error.Jwt403Handler;
import com.juvis.juvis._core.filter.JwtAuthorizationFilter;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder encodePwd() {
        return new BCryptPasswordEncoder();
    }

    @Value("${spring.profiles.active:dev}")
    private String profile;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        http.headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin()));

        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.formLogin(form -> form.disable());
        http.httpBasic(basicLogin -> basicLogin.disable());

        http.addFilterBefore(new JwtAuthorizationFilter(), UsernamePasswordAuthenticationFilter.class);

        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(new Jwt401Handler())
                .accessDeniedHandler(new Jwt403Handler()));

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ✅ actuator는 무조건 공개 (헬스체크)
                .requestMatchers("/actuator/**").permitAll()

                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/docs/**").permitAll()

                .requestMatchers("/api/ops/**").hasAnyRole("HQ", "VENDOR")
                .requestMatchers("/api/hq/**").hasRole("HQ")
                .requestMatchers("/api/vendor/**").hasRole("VENDOR")

                .requestMatchers("/api/**").authenticated()

                .anyRequest().permitAll());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        if ("prod".equals(profile)) {
            config.setAllowedOriginPatterns(List.of(
                    "https://xn--2z1bt3um9c9vcbxa47e.com",
                    "https://www.xn--2z1bt3um9c9vcbxa47e.com",
                    "https://api.xn--2z1bt3um9c9vcbxa47e.com"));
        } else {
            config.setAllowedOriginPatterns(List.of(
                    "http://localhost:*",
                    "http://127.0.0.1:*"));
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
