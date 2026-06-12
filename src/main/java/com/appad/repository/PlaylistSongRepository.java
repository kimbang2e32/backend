package com.appad.repository;

import com.appad.models.PlaylistSong;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, PlaylistSong.PlaylistSongId> {
    List<PlaylistSong> findByPlaylistId(Integer playlistId);
    List<PlaylistSong> findByPlaylistIdOrderByOrderIndexAsc(Integer playlistId);
    boolean existsByPlaylistIdAndSongId(Integer playlistId, Integer songId);
    void deleteByPlaylistIdAndSongId(Integer playlistId, Integer songId);
    int countByPlaylistId(Integer playlistId);
}
