// ============================
// SecurityConfig.java
// ============================
package com.juvis.juvis._core.config;

import com.juvis.juvis._core.error.Jwt401Handler;
import com.juvis.juvis._core.error.Jwt403Handler;
import com.juvis.juvis._core.filter.JwtAuthorizationFilter;

import java.util.List;

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

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // ✅ CORS
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        // 1) iframe 허용 (H2-console 같은 거 쓸 때)
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));

        // 2) csrf OFF
        http.csrf(csrf -> csrf.disable());

        // 3) STATELESS
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 4) formLogin OFF
        http.formLogin(form -> form.disable());

        // 5) httpBasic OFF
        http.httpBasic(basic -> basic.disable());

        // 6) JWT Filter
        http.addFilterBefore(new JwtAuthorizationFilter(), UsernamePasswordAuthenticationFilter.class);

        // 7) 예외처리 핸들러
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(new Jwt401Handler())
                .accessDeniedHandler(new Jwt403Handler()));

        // 8) 인가 룰
        http.authorizeHttpRequests(auth -> auth
                // ✅ preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ✅ actuator는 무조건 공개 (헬스체크)
                .requestMatchers("/actuator/**").permitAll()

                // ✅ 공개
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/docs/**").permitAll()

                // ✅ OPS (웹 HQ + VENDOR)
                .requestMatchers("/api/ops/**").hasAnyRole("HQ", "VENDOR")

                // ✅ 역할별
                .requestMatchers("/api/hq/**").hasRole("HQ")
                .requestMatchers("/api/vendor/**").hasRole("VENDOR")

                // ✅ 나머지 api는 로그인 필요
                .requestMatchers("/api/**").authenticated()

                // 그 외는 일단 허용(필요하면 tighten)
                .anyRequest().permitAll()
        );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // ✅ Flutter Web dev server / 로컬 호출
        // ✅ 운영 도메인도 필요하면 여기에 추가 (https 포함)
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "https://*"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        // 응답 헤더 노출
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));

        // ✅ Authorization 헤더 포함 요청 가능
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
