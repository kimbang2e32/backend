package com.appad.services;

import com.appad.models.ListeningHistory;
import com.appad.models.PremiumListeningStats;
import com.appad.models.Song;
import com.appad.models.User;
import com.appad.repository.ListeningHistoryRepository;
import com.appad.repository.PremiumListeningStatsRepository;
import com.appad.repository.SongRepository;
import com.appad.repository.UserRepository;
import com.appad.repository.PurchasedSongRepository;
import com.appad.repository.PurchasedAlbumRepository;
import com.appad.repository.ArtistMembershipRepository;
import com.appad.models.ArtistMembership;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoryService {
    private final ListeningHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final PremiumListeningStatsRepository premiumStatsRepository;
    private final PurchasedSongRepository purchasedSongRepository;
    private final PurchasedAlbumRepository purchasedAlbumRepository;
    private final ArtistMembershipRepository artistMembershipRepository;

    @Transactional
    public void recordListen(Integer userId, Integer songId, Integer durationSeconds, Boolean isCompleted, boolean incrementCount) {
        User user = userRepository.findById(userId).orElseThrow();
        Song song = songRepository.findById(songId).orElseThrow();

        // Get artist_id from song
        Integer artistId = (song.getArtist() != null) ? song.getArtist().getArtistId() : null;
        
        // Determine if this is a premium stream
        boolean isPremiumStream = false;
        
        boolean isSongPremium = (song.getIsPremium() != null && song.getIsPremium() == 1);
        boolean isAlbumPremium = (song.getAlbum() != null && song.getAlbum().getIsPremium() != null && song.getAlbum().getIsPremium() == 1);
        
        if (isSongPremium || isAlbumPremium) {
            // Check if user has purchased the song or the album
            boolean hasPurchasedSong = purchasedSongRepository.existsByUserUserIdAndSongSongId(userId, songId);
            boolean hasPurchasedAlbum = (song.getAlbum() != null && 
                purchasedAlbumRepository.existsByUserUserIdAndAlbumAlbumId(userId, song.getAlbum().getAlbumId()));
            
            // Check if user is a member of the artist
            boolean isArtistMember = false;
            if (artistId != null) {
                List<ArtistMembership> memberships = artistMembershipRepository.findByUserUserIdAndArtistArtistId(userId, artistId);
                isArtistMember = memberships.stream().anyMatch(m -> 
                    "active".equalsIgnoreCase(m.getStatus()) && 
                    (m.getExpiryDate() == null || m.getExpiryDate().isAfter(LocalDateTime.now()))
                );
            }

            // It's only a premium stream if they HAVEN'T purchased it, AREN'T an artist member, and they ARE a premium member
            if (!hasPurchasedSong && !hasPurchasedAlbum && !isArtistMember && (user.getIsPremium() != null && user.getIsPremium() == 1)) {
                isPremiumStream = true;
            }
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        
        // Find existing record for today (1 user + 1 song + 1 day = 1 record)
        List<ListeningHistory> existingList = historyRepository.findByUserAndSongAndDate(userId, songId, today);
        
        ListeningHistory history;
        if (!existingList.isEmpty()) {
            // Update existing record
            history = existingList.get(0);
            if (incrementCount) {
                history.setCount(history.getCount() + 1);
            }
            if (durationSeconds != null && durationSeconds > 0) {
                history.setTotalDuration((history.getTotalDuration() != null ? history.getTotalDuration() : 0) + durationSeconds);
            }
            if (Boolean.TRUE.equals(isCompleted)) {
                history.setCompletedCount((history.getCompletedCount() != null ? history.getCompletedCount() : 0) + 1);
            }
            // Update timestamp to bubble to top
            history.setDay(now);
        } else {
            // Create new record
            history = new ListeningHistory();
            history.setUser(user);
            history.setSong(song);
            history.setArtistId(artistId);
            history.setDay(now);
            history.setCount(incrementCount ? 1 : 0);
            history.setTotalDuration(durationSeconds != null && durationSeconds > 0 ? durationSeconds : 0);
            history.setCompletedCount(Boolean.TRUE.equals(isCompleted) ? 1 : 0);
            history.setIsPremiumStream(isPremiumStream);
        }
        historyRepository.save(history);

        // Premium stats (if user is premium)
        if (isPremiumStream) {
            Optional<PremiumListeningStats> statsOpt = premiumStatsRepository.findByUserIdAndSongIdAndListenDate(
                    userId.longValue(), songId.longValue(), today);
            
            if (statsOpt.isPresent()) {
                PremiumListeningStats stats = statsOpt.get();
                if (incrementCount) {
                    Integer currentCount = stats.getCount();
                    stats.setCount((currentCount != null ? currentCount : 0) + 1);
                }
                if (durationSeconds != null && durationSeconds > 0) {
                    stats.setTotalDuration(stats.getTotalDuration() != null ? stats.getTotalDuration() + durationSeconds : durationSeconds);
                }
                premiumStatsRepository.save(stats);
            } else {
                PremiumListeningStats stats = new PremiumListeningStats();
                stats.setUserId(userId.longValue());
                stats.setSongId(songId.longValue());
                if (artistId != null) {
                    stats.setArtistId(artistId.longValue());
                }
                stats.setCount(incrementCount ? 1 : 0);
                premiumStatsRepository.save(stats);
            }
        }
    }

    public List<Map<String, Object>> getHistoryByDay(Integer userId, int limit, int offset) {
        List<ListeningHistory> allHistory = historyRepository.findByUserUserIdOrderByDayDesc(userId);
        
        // Grouping by date (extract date part from 'day' column)
        Map<LocalDate, List<ListeningHistory>> grouped = allHistory.stream()
                .filter(h -> h.getDay() != null)
                .filter(h -> h.getSong() != null)
                .collect(Collectors.groupingBy(
                        h -> h.getDay().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ListeningHistory>> entry : grouped.entrySet()) {
            Map<String, Object> dayMap = new HashMap<>();
            dayMap.put("day", entry.getKey().toString());
            dayMap.put("song_count", entry.getValue().size());
            
            List<Map<String, Object>> songs = entry.getValue().stream().map(h -> {
                Map<String, Object> s = new HashMap<>();
                Song song = h.getSong();
                s.put("song_id", song.getSongId());
                s.put("title", song.getTitle());
                s.put("artist_id", h.getArtistId());
                s.put("artist_name", song.getArtist() != null ? song.getArtist().getName() : "Unknown");
                s.put("cover_url", song.getCoverUrl());
                s.put("file_url", song.getFileUrl());
                s.put("is_premium", song.getIsPremium());
                s.put("count", h.getCount());
                s.put("total_duration", h.getTotalDuration());
                s.put("completed_count", h.getCompletedCount());
                s.put("is_premium_stream", h.getIsPremiumStream());
                return s;
            }).collect(Collectors.toList());
            
            dayMap.put("songs", songs);
            result.add(dayMap);
        }

        // Apply limit/offset
        return result.stream().skip(offset).limit(limit).collect(Collectors.toList());
    }
}
