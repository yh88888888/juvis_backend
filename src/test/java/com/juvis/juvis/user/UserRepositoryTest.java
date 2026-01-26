package com.juvis.juvis.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.juvis.juvis._core.enums.UserRole;

@DataJpaTest
@Import(UserRepository.class) // ✅ 핵심: @DataJpaTest에서 커스텀 @Repository 빈을 직접 올림
class UserRepositoryTest {

    @Autowired EntityManager em;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("ssar");
        user.setPassword("1234");
        user.setRole(UserRole.BRANCH); // ✅ role 컬럼 nullable=false라서 반드시 세팅 필요

        em.persist(user);
        em.flush();
        em.clear();
    }

    @Test
    void findByUsername_test() {
        Optional<User> userOP = userRepository.findByUsername("ssar");

        assertThat(userOP).isPresent();
        assertThat(userOP.get().getUsername()).isEqualTo("ssar");
    }
}
