package com.appad.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String title;
    private String message;
    private String type; // e.g., 'new_song', 'deposit_approved', 'new_follower', etc.
    private boolean isRead = false;
    private String data; // JSON string for extra data (song_id, artist_id, etc.)

    private LocalDateTime createdAt = LocalDateTime.now();
}
