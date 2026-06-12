package com.appad.controllers;

import com.appad.models.Album;
import com.appad.models.Artist;
import com.appad.models.ArtistMembership;
import com.appad.models.Song;
import com.appad.models.User;
import com.appad.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Premium access check controller.
 *
 * Mục tiêu: hành vi tương tự backend NodeJS + React Native:
 * - Endpoint: GET /api/premium/song/{id}/access
 * - Trả về: { success: true, data: { hasAccess, accessType?, reason?, release_date? } }
 *
 * accessType có thể là:
 *  - artist_owner
 *  - purchased
 *  - album_purchased
 *  - artist_membership
 *  - premium
 */
@RestController
@RequestMapping("/api/premium")
@RequiredArgsConstructor
public class PremiumController {

    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;
    private final PurchasedSongRepository purchasedSongRepository;
    private final PurchasedAlbumRepository purchasedAlbumRepository;
    private final ArtistMembershipRepository artistMembershipRepository;

    private Integer getCurrentUserId() {
        return (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping("/song/{id}/access")
    public ResponseEntity<?> checkSongAccess(@PathVariable Integer id) {
        Integer userId = getCurrentUserId();

        Optional<Song> songOpt = songRepository.findById(id);
        if (songOpt.isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("hasAccess", false);
            data.put("reason", "Song not found");
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        }

        Song song = songOpt.get();

        boolean isAlbumPremium = false;
        boolean isAlbumUnreleased = false;
        LocalDateTime albumReleaseDate = null;

        if (song.getAlbumId() != null) {
            Optional<Album> albumOpt = albumRepository.findById(song.getAlbumId());
            if (albumOpt.isPresent()) {
                Album album = albumOpt.get();
                isAlbumPremium = album.getIsPremium() != null && album.getIsPremium() == 1;
                if (album.getReleaseDate() != null && album.getReleaseDate().isAfter(LocalDateTime.now())) {
                    isAlbumUnreleased = true;
                    albumReleaseDate = album.getReleaseDate();
                }
            }
        }

        // 1. Nếu user là artist owner của bài hát -> luôn có quyền
        if (song.getArtistId() != null) {
            Optional<Artist> artistOpt = artistRepository.findById(song.getArtistId());
            if (artistOpt.isPresent()) {
                Artist artist = artistOpt.get();
                if (artist.getUserId() != null && artist.getUserId().equals(userId)) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("hasAccess", true);
                    data.put("accessType", "artist_owner");
                    return ResponseEntity.ok(Map.of("success", true, "data", data));
                }
            }
        }

        // 2. Nếu album chưa phát hành -> không ai (trừ artist) được nghe
        if (isAlbumUnreleased) {
            Map<String, Object> data = new HashMap<>();
            data.put("hasAccess", false);
            data.put("reason", "Album not yet released");
            data.put("release_date", albumReleaseDate);
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        }

        // 3. Nếu bài hát và album đều không premium -> miễn phí cho tất cả
        boolean isSongPremium = song.getIsPremium() != null && song.getIsPremium() == 1;
        if (!isSongPremium && !isAlbumPremium) {
            Map<String, Object> data = new HashMap<>();
            data.put("hasAccess", true);
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        }

        // 4. Kiểm tra đã mua bài hát chưa
        boolean hasSongPurchase = purchasedSongRepository
                .existsByUserUserIdAndSongSongId(userId, song.getSongId());
        if (hasSongPurchase) {
            Map<String, Object> data = new HashMap<>();
            data.put("hasAccess", true);
            data.put("accessType", "purchased");
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        }

        // 5. Kiểm tra đã mua album chứa bài hát chưa
        if (song.getAlbumId() != null) {
            boolean hasAlbumPurchase = purchasedAlbumRepository
                    .existsByUserUserIdAndAlbumAlbumId(userId, song.getAlbumId());
            if (hasAlbumPurchase) {
                Map<String, Object> data = new HashMap<>();
                data.put("hasAccess", true);
                data.put("accessType", "album_purchased");
                return ResponseEntity.ok(Map.of("success", true, "data", data));
            }
        }

        // 6. Kiểm tra membership nghệ sĩ
        if (song.getArtistId() != null) {
            List<ArtistMembership> memberships =
                    artistMembershipRepository.findByUserUserIdAndArtistArtistId(userId, song.getArtistId());

            LocalDateTime now = LocalDateTime.now();
            boolean hasActiveMembership = memberships.stream().anyMatch(m ->
                    "active".equalsIgnoreCase(m.getStatus()) &&
                    (m.getExpiryDate() == null || m.getExpiryDate().isAfter(now))
            );

            if (hasActiveMembership) {
                Map<String, Object> data = new HashMap<>();
                data.put("hasAccess", true);
                data.put("accessType", "artist_membership");
                return ResponseEntity.ok(Map.of("success", true, "data", data));
            }
        }

        // 7. Kiểm tra Premium subscription
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("hasAccess", false);
            data.put("reason", "User not found");
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        }

        User user = userOpt.get();
        boolean isPremiumActive = user.getIsPremium() != null && user.getIsPremium() == 1
                && user.getPremiumExpiry() != null
                && user.getPremiumExpiry().isAfter(LocalDateTime.now());

        if (isPremiumActive) {
            Map<String, Object> data = new HashMap<>();
            data.put("hasAccess", true);
            data.put("accessType", "premium");
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        }

        // 8. Không có quyền truy cập
        Map<String, Object> data = new HashMap<>();
        data.put("hasAccess", false);
        data.put("reason", "Premium subscription, purchase, or artist membership required");
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
}




