package com.appad.repository;

import com.appad.models.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SongRepository extends JpaRepository<Song, Integer> {
    List<Song> findByArtistId(Integer artistId);
    List<Song> findByAlbumId(Integer albumId);
    List<Song> findByGenreId(Integer genreId);
    List<Song> findByArtistIdAndStatus(Integer artistId, Integer status);
    List<Song> findByAlbumIdAndStatus(Integer albumId, Integer status);
    List<Song> findByGenreIdAndStatus(Integer genreId, Integer status);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"artist", "album", "genre"})
    List<Song> findByTitleContainingIgnoreCaseAndStatus(String title, Integer status, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"artist", "album", "genre"})
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Song s WHERE (LOWER(s.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(s.lyrics) LIKE LOWER(CONCAT('%', :query, '%'))) AND s.status = :status")
    List<Song> searchByTitleOrLyrics(@org.springframework.data.repository.query.Param("query") String query, @org.springframework.data.repository.query.Param("status") Integer status, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"artist", "album", "genre"})
    List<Song> findAllByStatusOrderByReleaseDateDesc(Integer status, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"artist", "album", "genre"})
    List<Song> findAllByStatusOrderByListenCountDesc(Integer status, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"artist", "album", "genre"})
    org.springframework.data.domain.Page<Song> findAllByStatus(Integer status, org.springframework.data.domain.Pageable pageable);
    
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"artist", "album", "genre"})
    List<Song> findByGenreIdInAndStatus(List<Integer> genreIds, Integer status, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"artist", "album", "genre"})
    List<Song> findByGenreIdInAndStatusOrderByListenCountDesc(List<Integer> genreIds, Integer status, org.springframework.data.domain.Pageable pageable);

    int countByAlbumId(Integer albumId);
    long countByArtistId(Integer artistId);
    long countByGenreId(Integer genreId);
    java.util.Optional<Song> findFirstByGenreIdAndStatusOrderByCreatedAtDesc(Integer genreId, Integer status);
    
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(s.listenCount), 0) FROM Song s WHERE s.artistId = :artistId")
    long sumListenCountByArtistId(@org.springframework.data.repository.query.Param("artistId") Integer artistId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"artist", "album", "genre"})
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Song s WHERE s.status = :status " +
            "AND (s.genreId IN :genreIds OR s.artistId IN :artistIds) " +
            "ORDER BY (CASE WHEN s.songId IN :excludeSongIds THEN 1 ELSE 0 END) ASC, s.listenCount DESC")
    List<Song> findRecommendedSongsCustom(
            @org.springframework.data.repository.query.Param("genreIds") java.util.Collection<Integer> genreIds,
            @org.springframework.data.repository.query.Param("artistIds") java.util.Collection<Integer> artistIds,
            @org.springframework.data.repository.query.Param("excludeSongIds") java.util.Collection<Integer> excludeSongIds,
            @org.springframework.data.repository.query.Param("status") Integer status,
            org.springframework.data.domain.Pageable pageable);
}
