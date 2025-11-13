package com.juvis.juvis._core.config;

import com.juvis.juvis.storage.StorageProps;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
/**
 * 환경변수 기반 S3 관련 설정
 * - Region/credentials: AWS SDK v2의 기본 Provider Chain에서 자동 탐색
 */
@Configuration
@EnableConfigurationProperties(StorageProps.class)
public class S3Config {

    @Bean
    public S3Presigner s3Presigner() {
        // DefaultCredentialsProvider + DefaultAwsRegionProviderChain 사용
        return S3Presigner.create();
    }
}