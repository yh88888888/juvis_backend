// src/main/java/com/example/domain/user/UserAccount.java
package com.juvis.juvis.user;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.juvis.juvis._core.enums.UserRole;
import com.juvis.juvis.branch.Branch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;

@NoArgsConstructor
@Getter
@Setter
@Table(name = "user_tb")
@Entity
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    // @Column(unique = true)
    // private String email;

    @Column(length = 50, nullable = false, unique = true)
    private String username; // 지점명, 쥬비스, 아이디진정성

    @Column(length = 255, nullable = false)
    private String password;

    @Column(length = 100, nullable = true)
    private String name; // HQ, VENDOR만 해당

    @Column(length = 20, nullable = true)
    private String phone; // HQ, VENDOR만 해당

    @Column(length = 200, nullable = true)
    private String address; // HQ, VENDOR만 해당

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserRole role; // BRANCH, HQ, VENDOR;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder
    public User(Integer id, String username, String password, String name, String phone, String address, UserRole role,
            Branch branch,
            LocalDateTime createdAt, LocalDateTime updatedAt, boolean active) {
        this.id = id;

        this.username = username;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.role = role;
        this.branch = branch;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.active = active;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // 단일 역할 기준
        authorities.add(() -> "ROLE_" + role.name()); // 예: ROLE_HQ, ROLE_BRANCH

        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    // 계정 상태 관련 – 일단 전부 true + active는 isEnabled에 반영
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
