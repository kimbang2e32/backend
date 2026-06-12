package com.appad.controllers;

import com.appad.models.Follow;
import com.appad.models.User;
import com.appad.models.Artist;
import com.appad.repository.FollowRepository;
import com.appad.repository.UserRepository;
import com.appad.repository.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final com.appad.repository.SongRepository songRepository;
    private final com.appad.services.NotificationService notificationService;

    @PostMapping("/toggle")
    @Transactional
    public ResponseEntity<?> toggleFollow(@RequestBody Map<String, Long> payload) {
        Long userIdLong = payload.get("userId");
        Long artistIdLong = payload.get("artistId");
        
        Integer userId = userIdLong.intValue();
        Integer artistId = artistIdLong.intValue();

        if (followRepository.existsByUserUserIdAndArtistArtistId(userId, artistId)) {
            followRepository.deleteByUserUserIdAndArtistArtistId(userId, artistId);
            return ResponseEntity.ok(Map.of("success", true, "following", false));
        } else {
            User user = userRepository.findById(userId).orElseThrow();
            Artist artist = artistRepository.findById(artistId).orElseThrow();
            
            Follow follow = new Follow();
            follow.setUser(user);
            follow.setArtist(artist);
            followRepository.save(follow);
            
            // Notify Artist
            if (artist.getUserId() != null) {
                userRepository.findById(artist.getUserId()).ifPresent(artistUser -> {
                    notificationService.createNotification(artistUser,
                            "Người theo dõi mới",
                            user.getUsername() + " đã bắt đầu theo dõi bạn.",
                            "new_follower",
                            Map.of("followerId", userId, "followerName", user.getUsername()));
                });
            }

            return ResponseEntity.ok(Map.of("success", true, "following", true));
        }
    }

    @GetMapping("/check")
    public ResponseEntity<?> checkFollow(@RequestParam Long userId, @RequestParam Long artistId) {
        Integer userIdInt = userId.intValue();
        Integer artistIdInt = artistId.intValue();
        boolean following = followRepository.existsByUserUserIdAndArtistArtistId(userIdInt, artistIdInt);
        return ResponseEntity.ok(Map.of("following", following));
    }

    @GetMapping("/my-artists")
    public ResponseEntity<?> getMyFollowedArtists(@RequestParam Long userId) {
        Integer userIdInt = userId.intValue();
        var follows = followRepository.findByUserUserId(userIdInt);
        
        java.util.List<Map<String, Object>> artistResults = new java.util.ArrayList<>();
        for (Follow f : follows) {
            Artist a = f.getArtist();
            Map<String, Object> artistMap = new java.util.HashMap<>();
            artistMap.put("artistId", a.getArtistId());
            artistMap.put("name", a.getName());
            artistMap.put("bio", a.getBio());
            artistMap.put("imageUrl", a.getImageUrl());
            artistMap.put("songCount", songRepository.countByArtistId(a.getArtistId()));
            artistResults.add(artistMap);
        }
        
        return ResponseEntity.ok(Map.of("success", true, "data", artistResults));
    }
}
