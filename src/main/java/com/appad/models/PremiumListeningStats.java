package com.appad.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name = "premium_listening_stats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "song_id", "listen_date"})
})
@Data
public class PremiumListeningStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "song_id")
    private Long songId;

    @Column(name = "artist_id")
    private Long artistId;

    private LocalDate listenDate = LocalDate.now();
    
    private Integer count = 1;
    private Double totalDuration = 0.0;
}
