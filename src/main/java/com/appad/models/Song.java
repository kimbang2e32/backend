package com.appad.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "songs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Song {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "song_id")
    private Integer songId;

    @Column(nullable = false)
    private String title;

    @Column(name = "artist_id")
    private Integer artistId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "artist_id", insertable = false, updatable = false)
    private Artist artist;

    @Column(name = "album_id")
    private Integer albumId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "album_id", insertable = false, updatable = false)
    private Album album;

    @JsonProperty("artist_name")
    public String getArtistName() {
        return artist != null ? artist.getName() : null;
    }

    @JsonProperty("album_title")
    public String getAlbumTitle() {
        return album != null ? album.getTitle() : null;
    }

    @JsonProperty("is_album_premium")
    public Integer getIsAlbumPremium() {
        return album != null ? album.getIsPremium() : 0;
    }

    @JsonProperty("album_price")
    public Double getAlbumPrice() {
        return album != null ? album.getPrice() : 0.0;
    }

    @Column(name = "genre_id")
    private Integer genreId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "genre_id", insertable = false, updatable = false)
    private Genre genre;

    @JsonProperty("genre_name")
    public String getGenreName() {
        return genre != null ? genre.getName() : null;
    }

    @Column(nullable = false)
    private Integer duration;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "release_date")
    private LocalDateTime releaseDate;

    @Column(columnDefinition = "TEXT")
    private String lyrics;

    @Column(name = "is_premium")
    private Integer isPremium;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private Integer status; // 0: Hidden, 1: Active

    @Column(name = "listen_count")
    private Long listenCount;

    @Column(name = "average_rating")
    private Double averageRating;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isPremium == null) isPremium = 0;
        if (price == null) price = 0.0;
        if (status == null) status = 1;
        if (listenCount == null) listenCount = 0L;
        if (averageRating == null) averageRating = 0.0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Transient
    private Boolean bought;

    @Transient
    private Boolean albumBought;

    @Transient
    private Boolean artistMember;

    @Transient
    private Boolean isArtistOwner;
}
