package com.appad.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Data
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @ManyToOne
    @JoinColumn(name = "reporter_id")
    private User user;

    @Column(name = "user_id")
    private Long reportedUserId; // Matches database BIGINT column definition

    @Column(name = "target_id")
    private Long targetId;
    
    @Column(name = "target_type")
    private String targetType; // 'song', 'album', 'comment'
    
    private String reason;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private String status = "pending"; // 'pending', 'resolved', 'dismissed'

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "pending";
    }
}
