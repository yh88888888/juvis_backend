package com.juvis.juvis.user_device;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Repository
public class UserDeviceRepository {

    private final EntityManager em;

    public Optional<UserDevice> findByToken(String token) {
        try {
            return Optional.of(
                    em.createQuery("select d from UserDevice d where d.fcmToken = :t", UserDevice.class)
                            .setParameter("t", token)
                            .getSingleResult());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public UserDevice save(UserDevice d) {
        em.persist(d);
        return d;
    }

    public UserDevice update(UserDevice d) {
        return em.merge(d);
    }
}
