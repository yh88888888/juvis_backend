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

    // ✅ HQ용: 전체 지점 목록 (드롭다운)
    public List<Branch> findAllOrderByName() {
        return em.createQuery(
                "select b from Branch b order by b.branchName asc",
                Branch.class
        ).getResultList();
    }

    // ✅ id로 찾기 (HQ 요청 생성 시 필수)
    public Optional<Branch> findById(Long id) {
        return Optional.ofNullable(em.find(Branch.class, id));
    }
}
