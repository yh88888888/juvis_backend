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

    // 시큐리티 컨텍스트 홀더에 세션 저장할 때 사용하는 클래스
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // ✅ CORS 먼저 켜기 (웹에서 localhost:63020 -> 8080 호출 허용)
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        // 1. iframe 허용 -> mysql로 전환하면 삭제
        http.headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin()));

        // 2. csrf 비활성화 -> html 사용 안할꺼니까!!
        http.csrf(csrf -> csrf.disable());

        // 3. 세션 비활성화 (STATELESS) -> 키 전달 안해주고, 집에갈때 락카를 비워버린다.
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 4. 폼 로그인 비활성화 (UsernamePasswordAuthenticationFilter 발동을 막기)
        http.formLogin(form -> form.disable());

        // 5. HTTP Basic 인증 비활성화 (BasicAuthenticationFilter 발동을 막기)
        http.httpBasic(basicLogin -> basicLogin.disable());

        // 6. 커스텀 필터 작창 (인가 필터) -> 로그인 컨트롤러에서 직접하기
        http.addFilterBefore(new JwtAuthorizationFilter(), UsernamePasswordAuthenticationFilter.class);

        // 7. 예외처리 핸들러 등록 ((1)인증,인가가 완료되면 어떻게? (2)예외가 발생하면 어떻게?)
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(new Jwt401Handler())
                .accessDeniedHandler(new Jwt403Handler()));

        // /s/api/** : 인증 필요, /s/api/admin/** : ADMIN 권한 필요
        http.authorizeHttpRequests(authorize -> authorize

                // ✅ 프리플라이트(OPTIONS)는 무조건 허용 (웹에서 Failed to fetch 방지 핵심)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ✅ 공개
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/docs/**").permitAll()

                // ✅ HQ / VENDOR 권한
                .requestMatchers("/api/hq/**").hasRole("HQ")
                .requestMatchers("/api/vendor/**").hasRole("VENDOR")

                // ✅ 나머지 /api는 로그인(토큰) 필요
                .requestMatchers("/api/**").authenticated()

                // 그 외는 필요하면 열어두고, 아니면 막아도 됨
                .anyRequest().permitAll());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // ✅ flutter web dev server origin
        config.setAllowedOrigins(List.of(
                "http://localhost:63020",
                "http://127.0.0.1:63020"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}