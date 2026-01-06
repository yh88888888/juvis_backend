package com.juvis.juvis.vendor_worker;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vendor_worker")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorWorker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "worker_id")
    private Long id;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "team_label")
    private String teamLabel; // "1팀"

    @Column(name = "name", nullable = false)
    private String name; // "홍길동"

    @Column(name = "phone")
    private String phone;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
