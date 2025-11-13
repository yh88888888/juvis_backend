// src/main/java/com/example/domain/user/UserAccount.java
package com.juvis.juvis.user;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;

@NoArgsConstructor
@Getter
@Table(name = "users_tb")
@Entity
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 50, nullable = false, unique = true)
    private String username; //지점명, 쥬비스, 아이디진정성

    @Column(length = 255, nullable = false)
    private String password;

    @Column(length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private String roles; // BRANCH, HQ, VENDOR; 

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;


    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder
    public User(Integer id, String email, String username, String password, String roles, String name, 
            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.password = password;
        this.roles = roles;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        String[] roleList = roles.split(",");

        for (String role : roleList) {
            authorities.add(() -> "ROLE_" + role);
        }

        return authorities;
    }


// @Table(name = "user_account", indexes = { @Index(name = "uk_user_username", columnList = "username", unique = true),
//         @Index(name = "uk_user_email", columnList = "email", unique = true),
//         @Index(name = "idx_user_role", columnList = "role"),
//         @Index(name = "idx_user_branch", columnList = "branch_id") })


}
