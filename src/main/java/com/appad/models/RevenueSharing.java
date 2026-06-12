package com.appad.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "revenue_sharing")
@Data
public class RevenueSharing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sharing_id")
    private Integer sharingId;

    @Column(name = "transaction_id")
    private Integer transactionId;

    @Column(name = "purchase_id")
    private Integer purchaseId;

    @Column(name = "album_purchase_id")
    private Integer albumPurchaseId;

    @Column(name = "artist_id")
    private Integer artistId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "song_id")
    private Integer songId;

    @Column(name = "share_type")
    private String shareType;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "artist_share")
    private Double artistShare;

    @Column(name = "artist_percentage")
    private Double artistPercentage = 70.0;

    @Column(name = "platform_share")
    private Double platformShare;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Helper methods to get month/year from createdAt
    @Transient
    public Integer getMonth() {
        return createdAt != null ? createdAt.getMonthValue() : null;
    }
    
    @Transient
    public Integer getYear() {
        return createdAt != null ? createdAt.getYear() : null;
    }
}
