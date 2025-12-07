// src/main/java/com/example/domain/branch/Branch.java
package com.juvis.juvis.branch;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

import com.juvis.juvis.user.User;

@Entity
@Table(name = "branch", indexes = { @Index(name = "uk_branch_name", columnList = "branch_name", unique = true) })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    private Long id;

    @Column(name = "branch_name", nullable = false, unique = true, length = 100)
    private String branchName;

    @Column(length = 20)
    private String phone;

    @Column(name = "address_name", length = 200)
    private String addressName;

    @OneToMany(mappedBy = "branch")
    private List<User> users = new ArrayList<>();

    // @OneToMany(mappedBy = "branch")
    // @Builder.Default
    // private List<com.juvis.juvis.user.UserAccount> users = new ArrayList<>();
}
