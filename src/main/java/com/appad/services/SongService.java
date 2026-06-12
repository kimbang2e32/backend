package com.appad.services;

import com.appad.models.Song;
import com.appad.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SongService {
    private final SongRepository songRepository;
    private final com.appad.repository.PurchasedSongRepository purchasedSongRepository;
    private final com.appad.repository.PurchasedAlbumRepository purchasedAlbumRepository;
    private final com.appad.repository.AlbumRepository albumRepository;
    private final com.appad.repository.ArtistMembershipRepository artistMembershipRepository;
    private final com.appad.repository.ArtistRepository artistRepository;

    private final com.appad.repository.FavoriteRepository favoriteRepository;
    private final com.appad.repository.ListeningHistoryRepository listeningHistoryRepository;

    public void populateAccessInfo(List<Song> songs, Integer userId) {
        if (userId == null || songs == null || songs.isEmpty()) return;

        // Fetch user data in bulk to avoid N+1
        var purchasedSongs = purchasedSongRepository.findByUserUserId(userId);
        var purchasedAlbums = purchasedAlbumRepository.findByUserUserId(userId);
        
        java.util.Set<Integer> boughtSongIds = purchasedSongs.stream()
                .map(ps -> ps.getSong().getSongId())
                .collect(java.util.stream.Collectors.toSet());
                
        java.util.Set<Integer> boughtAlbumIds = purchasedAlbums.stream()
                .map(pa -> pa.getAlbum().getAlbumId())
                .collect(java.util.stream.Collectors.toSet());

        // Find the artistId of the current user
        Integer currentUserArtistId = artistRepository.findByUserId(userId)
                .map(com.appad.models.Artist::getArtistId)
                .orElse(null);

        // Fetch all active memberships for user to avoid N+1
        java.util.Set<Integer> memberArtistIds = artistMembershipRepository.findByUserId(userId)
                .stream()
                .filter(m -> "active".equals(m.getStatus()))
                .map(com.appad.models.ArtistMembership::getArtistId)
                .collect(java.util.stream.Collectors.toSet());

        for (Song song : songs) {
            song.setBought(boughtSongIds.contains(song.getSongId()));
            song.setAlbumBought(song.getAlbumId() != null && boughtAlbumIds.contains(song.getAlbumId()));
            song.setIsArtistOwner(currentUserArtistId != null && currentUserArtistId.equals(song.getArtistId()));
            
            // Check artist membership
            if (song.getArtistId() != null) {
                song.setArtistMember(memberArtistIds.contains(song.getArtistId()));
            }
        }
    }

    public void populateAccessInfo(Song song, Integer userId) {
        if (song != null) populateAccessInfo(java.util.List.of(song), userId);
    }

    public List<Song> getAllSongs(int limit, int offset) {
        PageRequest pageRequest = PageRequest.of(offset / limit, limit);
        Page<Song> songPage = songRepository.findAllByStatus(1, pageRequest);
        return songPage.getContent();
    }

    public List<Song> getLatestSongs(int limit, int offset) {
        return songRepository.findAllByStatusOrderByReleaseDateDesc(1, PageRequest.of(offset / limit, limit));
    }

    public List<Song> getTrendingSongs(int limit, int offset) {
        return songRepository.findAllByStatusOrderByListenCountDesc(1, PageRequest.of(offset / limit, limit));
    }

    public List<Song> getRecommendedSongs(Integer userId, int limit, int offset) {
        if (userId == null) {
            return getLatestSongs(limit, offset);
        }

        // 1. Get user preferences (genres from favorites and recent history)
        var favoriteSongIds = favoriteRepository.findByUserId(userId).stream()
                .map(com.appad.models.Favorite::getSongId)
                .collect(java.util.stream.Collectors.toList());
        
        var recentHistory = listeningHistoryRepository.findByUserUserIdOrderByDayDesc(userId);
        var recentSongIds = recentHistory.stream()
                .limit(20)
                .map(h -> h.getSong().getSongId())
                .collect(java.util.stream.Collectors.toList());
        
        java.util.Set<Integer> preferredSongIds = new java.util.HashSet<>(favoriteSongIds);
        preferredSongIds.addAll(recentSongIds);

        java.util.Set<Integer> preferredGenreIds = new java.util.HashSet<>();
        if (!preferredSongIds.isEmpty()) {
            // Fetch the genres of these songs
            songRepository.findAllById(preferredSongIds).forEach(s -> {
                if (s.getGenreId() != null) preferredGenreIds.add(s.getGenreId());
            });
        }

        if (!preferredGenreIds.isEmpty()) {
            // 2. Query songs with matching genres, ordered by popularity
            List<Integer> genreIds = new java.util.ArrayList<>(preferredGenreIds);
            return songRepository.findByGenreIdInAndStatusOrderByListenCountDesc(genreIds, 1, PageRequest.of(offset / limit, limit));
        }

        // Fallback: Latest songs
        return getLatestSongs(limit, offset);
    }

    public Optional<Song> getSongById(Integer id) {
        return songRepository.findById(id);
    }

    public List<Song> searchSongs(String query, int limit) {
        // FIX: Just search by title to avoid potential SQL syntax errors with empty IN clause
        return songRepository.findByTitleContainingIgnoreCaseAndStatus(query, 1, PageRequest.of(0, limit));
    }

    public List<Song> getSongsByArtist(Integer artistId, Integer userId) {
        // Find if userId belongs to this artist
        boolean isOwner = false;
        if (userId != null) {
            isOwner = artistRepository.findByUserId(userId)
                    .map(a -> a.getArtistId().equals(artistId))
                    .orElse(false);
        }

        if (isOwner) {
            return songRepository.findByArtistId(artistId);
        }
        return songRepository.findByArtistIdAndStatus(artistId, 1);
    }

    public List<Song> getSongsByAlbum(Integer albumId, Integer userId) {
        // For albums, if the requester owns the artist of the album, they see all
        boolean isOwner = false;
        if (userId != null) {
            var album = albumRepository.findById(albumId);
            if (album.isPresent()) {
                Integer albumArtistId = album.get().getArtistId();
                isOwner = artistRepository.findByUserId(userId)
                        .map(a -> a.getArtistId().equals(albumArtistId))
                        .orElse(false);
            }
        }

        if (isOwner) {
            return songRepository.findByAlbumId(albumId);
        }
        return songRepository.findByAlbumIdAndStatus(albumId, 1);
    }

    public List<Song> getSongsByGenre(Integer genreId) {
        return songRepository.findByGenreIdAndStatus(genreId, 1);
    }

    public List<Song> searchAllSongs(String query, int limit, int offset, String sort) {
        org.springframework.data.domain.Sort sortOrder;
        switch (sort) {
            case "oldest":
                sortOrder = org.springframework.data.domain.Sort.by("releaseDate").ascending();
                break;
            case "name_asc":
                sortOrder = org.springframework.data.domain.Sort.by("title").ascending();
                break;
            case "name_desc":
                sortOrder = org.springframework.data.domain.Sort.by("title").descending();
                break;
            case "listens_asc":
                sortOrder = org.springframework.data.domain.Sort.by("listenCount").ascending();
                break;
            case "listens_desc":
                sortOrder = org.springframework.data.domain.Sort.by("listenCount").descending();
                break;
            case "rating_asc":
                sortOrder = org.springframework.data.domain.Sort.by("averageRating").ascending();
                break;
            case "rating_desc":
                sortOrder = org.springframework.data.domain.Sort.by("averageRating").descending();
                break;
            default: // newest
                sortOrder = org.springframework.data.domain.Sort.by("releaseDate").descending();
                break;
        }

        org.springframework.data.domain.Pageable pageable = PageRequest.of(offset / limit, limit, sortOrder);
        
        if (query == null || query.trim().isEmpty()) {
            return songRepository.findAllByStatus(1, pageable).getContent();
        } else {
            return songRepository.findByTitleContainingIgnoreCaseAndStatus(query, 1, pageable);
        }
    }

    public Song createSong(Song song) {
        return songRepository.save(song);
    }

    public Song updateSong(Integer id, Song songData) {
        Song song = songRepository.findById(id).orElseThrow(() -> new RuntimeException("Song not found"));
        // Update fields
        if (songData.getTitle() != null) song.setTitle(songData.getTitle());
        if (songData.getFileUrl() != null) song.setFileUrl(songData.getFileUrl());
        if (songData.getCoverUrl() != null) song.setCoverUrl(songData.getCoverUrl());
        if (songData.getLyrics() != null) song.setLyrics(songData.getLyrics());
        if (songData.getIsPremium() != null) song.setIsPremium(songData.getIsPremium());
        if (songData.getPrice() != null) song.setPrice(songData.getPrice());
        if (songData.getStatus() != null) song.setStatus(songData.getStatus());
        
        return songRepository.save(song);
    }

    public void deleteSong(Integer id) {
        songRepository.deleteById(id);
    }

    public void incrementListenCount(Integer id) {
        Song song = songRepository.findById(id).orElseThrow(() -> new RuntimeException("Song not found"));
        song.setListenCount(song.getListenCount() + 1);
        songRepository.save(song);
    }
}
