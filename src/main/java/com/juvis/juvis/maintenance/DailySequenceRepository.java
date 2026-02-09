package com.juvis.juvis.maintenance;

import org.springframework.data.jpa.repository.*;
import java.time.LocalDate;

public interface DailySequenceRepository extends JpaRepository<DailySequence, LocalDate> {

    // 날짜별 seq 원자적 증가
    @Modifying
    @Query(value = """
        INSERT INTO daily_sequence (seq_date, seq)
        VALUES (?1, 1)
        ON DUPLICATE KEY UPDATE seq = LAST_INSERT_ID(seq + 1)
        """, nativeQuery = true)
    void upsertAndIncrement(LocalDate date);

    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    int lastSequence();
}
