package com.juvis.juvis.branch;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class BranchRepository {

    private final EntityManager em;

    public Branch save(Branch branch) {
        em.persist(branch);
        return branch;
    }

    public Optional<Branch> findByName(String branchName) {
        try {
            Branch branch = em.createQuery(
                            "select b from Branch b where b.branchName = :name", Branch.class)
                    .setParameter("name", branchName)
                    .getSingleResult();
            return Optional.of(branch);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

