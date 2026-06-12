package com.appad.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "artists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Artist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_id")
    private Integer artistId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "image_url")
    private String imageUrl;

    private String country;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "total_earned")
    private Double totalEarned;

    @Column(name = "total_withdrawn")
    private Double totalWithdrawn;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "bank_account_name")
    private String bankAccountName;

    @Column(name = "membership_price")
    private Double membershipPrice;

    @Column(name = "membership_duration_days")
    private Integer membershipDurationDays;

    @Builder.Default
    @Column(name = "wallet_balance")
    private Double walletBalance = 0.0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (membershipPrice == null) membershipPrice = 0.0;
        if (membershipDurationDays == null) membershipDurationDays = 30;
        if (walletBalance == null) walletBalance = 0.0;
        if (totalEarned == null) totalEarned = 0.0;
        if (totalWithdrawn == null) totalWithdrawn = 0.0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
