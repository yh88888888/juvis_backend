package com.juvis.juvis._core.util;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class PasswordEncodeRunner {

    @Bean
    CommandLineRunner passwordRunner(BCryptPasswordEncoder encoder) {
        return args -> {
            String encoded = encoder.encode("585858");
            System.out.println("BCrypt(585858) = " + encoded);
        };
    }
}
