package com.juvis.juvis.branch;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class BranchRepository {

    private final EntityManager em;

    public Branch save(Branch branch) {
        em.persist(branch);
        return branch;
    }

    public Optional<Branch> findByBranchName(String branchName) {
        List<Branch> result = em.createQuery(
                "select b from Branch b where b.branchName = :branchName", Branch.class)
                .setParameter("branchName", branchName)
                .getResultList();

        return result.stream().findFirst();
    }
}
