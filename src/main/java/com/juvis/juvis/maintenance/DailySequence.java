package com.juvis.juvis.maintenance;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "daily_sequence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySequence {

    @Id
    @Column(name = "seq_date")
    private LocalDate seqDate;

    @Column(name = "seq", nullable = false)
    private int seq;
}
