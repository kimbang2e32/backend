package com.appad.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Integer transactionId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false)
    private String type; // Types: 'deposit', 'purchase_song', 'purchase_album', 'premium', 'membership', 'withdrawal'

    @Column(nullable = false)
    private Double amount;

    @Builder.Default
    @Column(nullable = false)
    private String status = "pending"; // 'pending', 'success', 'failed', 'cancelled', 'approved', 'rejected'

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "reference_code")
    private String referenceCode;

    @Column(name = "target_id")
    private Long targetId; // song_id, album_id, artist_id, or null

    // Bank info for withdrawals
    @Column(name = "bank_name")
    private String bankName;
    
    @Column(name = "account_number")
    private String accountNumber;
    
    @Column(name = "account_holder_name")
    private String accountHolderName;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "pending";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
