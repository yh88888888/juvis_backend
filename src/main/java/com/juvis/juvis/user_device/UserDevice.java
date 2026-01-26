package com.juvis.juvis.user_device;

import java.time.LocalDateTime;
import com.juvis.juvis.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "user_device", uniqueConstraints = @UniqueConstraint(name = "uq_user_device_token", columnNames = "fcm_token"))
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Column(name = "fcm_token", nullable = false, length = 255)
    private String fcmToken;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    public void rebind(User user, String platform) {
        this.user = user;
        touch(platform);
    }

    public static UserDevice of(User user, String platform, String token) {
        UserDevice d = new UserDevice();
        d.user = user;
        d.platform = platform;
        d.fcmToken = token;
        d.isActive = true;
        d.lastSeenAt = LocalDateTime.now();
        return d;
    }

    public void touch(String platform) {
        this.platform = platform;
        this.isActive = true;
        this.lastSeenAt = LocalDateTime.now();
    }
}
