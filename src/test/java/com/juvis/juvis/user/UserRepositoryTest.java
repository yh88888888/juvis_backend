package com.juvis.juvis.user;


import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class UserRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("ssar");
        user.setPassword("1234");

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
