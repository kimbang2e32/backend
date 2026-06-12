package com.appad.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "listening_history")
@Data
public class ListeningHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "song_id")
    private Song song;

    @Column(name = "artist_id")
    private Integer artistId;

    @Column(name = "day")
    private LocalDateTime day;

    @Column(name = "count")
    private Integer count = 1;

    @Column(name = "total_duration")
    private Integer totalDuration = 0;

    @Column(name = "completed_count")
    private Integer completedCount = 0;

    @Column(name = "is_premium_stream")
    private Boolean isPremiumStream = false;
}
