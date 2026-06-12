package com.appad.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "albums")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Album {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "album_id")
    private Integer albumId;

    @Column(nullable = false)
    private String title;

    @Column(name = "artist_id")
    private Integer artistId;

    @Column(name = "release_date")
    private LocalDateTime releaseDate;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "is_premium")
    private Integer isPremium;

    private Double price;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "artist_id", insertable = false, updatable = false)
    private Artist artist;

    @com.fasterxml.jackson.annotation.JsonProperty("artist_name")
    public String getArtistName() {
        return artist != null ? artist.getName() : null;
    }

    @PrePersist
    protected void onCreate() {
        if (isPremium == null) isPremium = 0;
        if (price == null) price = 0.0;
    }

    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("songCount")
    private Integer songCount;
}
