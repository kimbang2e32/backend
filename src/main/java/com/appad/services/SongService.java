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

    public List<Song> getPersonalFavorites(Integer userId, int limit, int offset) {
        if (userId == null) {
            return new java.util.ArrayList<>();
        }

        List<com.appad.models.ListeningHistory> recentHistory = 
                listeningHistoryRepository.findTop100ByUserUserIdOrderByDayDesc(userId);

        if (recentHistory.isEmpty()) {
            return new java.util.ArrayList<>();
        }

        // Gom nhóm theo bài hát và tính tổng thời gian nghe (totalDuration)
        java.util.Map<Song, Integer> songDurationMap = recentHistory.stream()
                .filter(h -> h.getSong() != null && h.getSong().getStatus() == 1)
                .collect(java.util.stream.Collectors.groupingBy(
                        com.appad.models.ListeningHistory::getSong,
                        java.util.stream.Collectors.summingInt(h -> h.getTotalDuration() != null ? h.getTotalDuration() : 0)
                ));

        // Sắp xếp các bài hát theo tổng thời gian nghe giảm dần
        List<Song> sortedSongs = songDurationMap.entrySet().stream()
                .sorted(java.util.Map.Entry.<Song, Integer>comparingByValue().reversed())
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());

        int size = sortedSongs.size();
        if (offset >= size) {
            return new java.util.ArrayList<>();
        }
        int toIndex = Math.min(offset + limit, size);
        return sortedSongs.subList(offset, toIndex);
    }

    public List<Song> getRecommendedSongs(Integer userId, int limit, int offset) {
        if (userId == null) {
            return getLatestSongs(limit, offset);
        }

        // 1. Lấy toàn bộ lịch sử nghe nhạc gần đây để xác định nhạc tủ
        List<com.appad.models.ListeningHistory> recentHistory = 
                listeningHistoryRepository.findTop100ByUserUserIdOrderByDayDesc(userId);

        if (recentHistory.isEmpty()) {
            return getLatestSongs(limit, offset);
        }

        // Gom nhóm theo bài hát và tính tổng thời gian nghe để sắp xếp nhạc tủ
        java.util.Map<Song, Integer> songDurationMap = recentHistory.stream()
                .filter(h -> h.getSong() != null && h.getSong().getStatus() == 1)
                .collect(java.util.stream.Collectors.groupingBy(
                        com.appad.models.ListeningHistory::getSong,
                        java.util.stream.Collectors.summingInt(h -> h.getTotalDuration() != null ? h.getTotalDuration() : 0)
                ));

        List<Song> favoriteSongs = songDurationMap.entrySet().stream()
                .sorted(java.util.Map.Entry.<Song, Integer>comparingByValue().reversed())
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());

        if (favoriteSongs.isEmpty()) {
            return getLatestSongs(limit, offset);
        }

        // 2. Lấy top 5 bài hát nghe nhiều nhất của nhạc tủ làm cơ sở
        int topN = Math.min(5, favoriteSongs.size());
        List<Song> topFavoriteSongs = favoriteSongs.subList(0, topN);

        // 3. Lấy ra danh sách các genreId và artistId của các bài top này
        java.util.Set<Integer> topGenreIds = topFavoriteSongs.stream()
                .map(Song::getGenreId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Set<Integer> topArtistIds = topFavoriteSongs.stream()
                .map(Song::getArtistId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        if (topGenreIds.isEmpty() && topArtistIds.isEmpty()) {
            return getLatestSongs(limit, offset);
        }

        // 4. Lấy tập hợp ID bài hát nhạc tủ để loại trừ
        java.util.Set<Integer> favoriteSongIds = favoriteSongs.stream()
                .map(Song::getSongId)
                .collect(java.util.stream.Collectors.toSet());

        // 5. Query các bài hát tương tự (trùng genre hoặc artist), loại trừ nhạc tủ
        java.util.Collection<Integer> genresParam = topGenreIds.isEmpty() ? java.util.List.of(-1) : topGenreIds;
        java.util.Collection<Integer> artistsParam = topArtistIds.isEmpty() ? java.util.List.of(-1) : topArtistIds;
        java.util.Collection<Integer> excludeParam = favoriteSongIds.isEmpty() ? java.util.List.of(-1) : favoriteSongIds;

        org.springframework.data.domain.Pageable pageable = PageRequest.of(offset / limit, limit, 
                org.springframework.data.domain.Sort.by("listenCount").descending());

        return songRepository.findRecommendedSongsCustom(genresParam, artistsParam, excludeParam, 1, pageable);
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
