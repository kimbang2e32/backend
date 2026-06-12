package com.appad.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false)
    private String role; // 'user', 'admin', 'artist'

    @Column(name = "is_banned")
    private Integer isBanned; // 0: normal, 1: banned, 2: pending artist

    @Column(name = "is_premium")
    private Integer isPremium;

    @Column(name = "premium_expiry")
    private LocalDateTime premiumExpiry;

    @Column(nullable = false)
    private Double balance;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (role == null) role = "user";
        if (isBanned == null) isBanned = 0;
        if (isPremium == null) isPremium = 0;
        if (balance == null) balance = 0.0;
    }
}
