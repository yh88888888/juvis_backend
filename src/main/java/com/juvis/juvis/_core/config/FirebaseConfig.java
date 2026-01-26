package com.juvis.juvis._core.config;

import java.io.InputStream;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    @Value("${firebase.service-account:}")
    private Resource serviceAccount;

    @PostConstruct
    public void init() {
        try {
            if (serviceAccount == null || !serviceAccount.exists()) {
                log.warn("🔥 Firebase init SKIP: service-account not found (property set but file missing).");
                return;
            }

            log.info("🔥 Firebase init start: {}", serviceAccount);

            try (InputStream in = serviceAccount.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(in))
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                    log.info("✅ Firebase initialized successfully.");
                } else {
                    log.info("ℹ️ Firebase already initialized. apps={}", FirebaseApp.getApps().size());
                }
            }
        } catch (Exception e) {
            log.error("❌ Firebase init FAILED", e);
            throw new IllegalStateException("🔥 Firebase 초기화 실패", e);
        }
    }
}