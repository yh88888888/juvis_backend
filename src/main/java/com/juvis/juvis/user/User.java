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

@Builder
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

    @Column(length = 50, nullable = false, unique = true)
    private String username;

    @Column(length = 255, nullable = false)
    private String password;

    @Column(length = 100)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    // ✅ 로그인 실패/잠금
    @Builder.Default
    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount = 0;

    @Builder.Default
    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    // ✅ Builder는 생성자 1곳만 두는 걸 추천
    @Builder
    public User(
            Integer id,
            String username,
            String password,
            String name,
            String phone,
            String address,
            UserRole role,
            Branch branch,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean active,
            boolean mustChangePassword,
            int loginFailCount,
            boolean accountLocked
    ) {
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
        this.mustChangePassword = mustChangePassword;
        this.loginFailCount = loginFailCount;
        this.accountLocked = accountLocked;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(() -> "ROLE_" + role.name());
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // ✅ 잠금 반영 (중요)
    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
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
