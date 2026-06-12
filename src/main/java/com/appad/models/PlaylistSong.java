package com.appad.models;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "playlist_songs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(PlaylistSong.PlaylistSongId.class)
public class PlaylistSong {

    @Id
    @Column(name = "playlist_id", nullable = false)
    private Integer playlistId;

    @Id
    @Column(name = "song_id", nullable = false)
    private Integer songId;

    @Column(name = "`order`")
    private Integer orderIndex;

    // Composite Primary Key class
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaylistSongId implements Serializable {
        private Integer playlistId;
        private Integer songId;
    }
}
