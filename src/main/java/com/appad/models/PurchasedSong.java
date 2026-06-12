package com.appad.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchased_songs")
@Data
public class PurchasedSong {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id")
    private Integer purchaseId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "song_id")
    private Song song;

    @Column(name = "purchase_date")
    private LocalDateTime purchaseDate;

    @Column(name = "price_paid", nullable = false)
    private Double pricePaid;

    @Column(name = "purchased_at")
    private LocalDateTime purchasedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        purchasedAt = now;
        if (purchaseDate == null) {
            purchaseDate = now;
        }
    }
}
