// src/main/java/com/example/domain/branch/Branch.java
package com.juvis.juvis.branch;


import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

import com.juvis.juvis._core.BaseEntity;

@Entity @Table(name = "branch",
        indexes = { @Index(name = "uk_branch_name", columnList = "branch_name", unique = true) })
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder
public class Branch extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    private Long id;

    @Column(name="branch_name", length=100, nullable=false)
    private String name;

    @Column(name="manager_name", length=100) 
    private String managerName;

    @Column(length=20) 
    private String phone;

    // @OneToMany(mappedBy = "branch")
    // @Builder.Default
    // private List<com.juvis.juvis.user.UserAccount> users = new ArrayList<>();
}
