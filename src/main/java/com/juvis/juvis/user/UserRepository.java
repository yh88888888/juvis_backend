package com.juvis.juvis.user;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.juvis.juvis._core.enums.UserRole;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Repository

public class UserRepository {
    private final EntityManager em;

    public Optional<User> findById(Integer id) {
        return Optional.ofNullable(em.find(User.class, id));
    }

    public Optional<User> findByName(String name) {
        try {
            User userPS = em.createQuery("select u from User u where u.name = :name", User.class)
                    .setParameter("name", name)
                    .getSingleResult();
            return Optional.of(userPS);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByUsername(String username) {
        try {
            User userPS = em.createQuery("select u from User u where u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
            return Optional.of(userPS);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public User save(User user) {
        em.persist(user);
        return user;
    }

    public List<User> findByRole(UserRole role) {
        return em.createQuery("select u from User u where u.role = :role", User.class)
                .setParameter("role", role)
                .getResultList();

    }

public List<User> findBranchUsers() {
    return em.createQuery(
            """
            select u
            from User u
            left join fetch u.branch b
            where u.role = :role
            order by b.id asc, u.id asc
            """,
            User.class
    )
    .setParameter("role", UserRole.BRANCH)
    .getResultList();
}


}