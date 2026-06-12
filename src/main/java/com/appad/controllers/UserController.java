package com.appad.controllers;

import com.appad.models.User;
import com.appad.repository.FollowRepository;
import com.appad.repository.PlaylistRepository;
import com.appad.repository.UserRepository;
import com.appad.services.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final FollowRepository followRepository;
    private final PlaylistRepository playlistRepository;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        Integer userId = (Integer) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(Map.of("success", true, "data", user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> payload) {
        Integer userId = (Integer) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();
        
        if (payload.containsKey("username")) user.setUsername((String) payload.get("username"));
        if (payload.containsKey("full_name")) user.setFullName((String) payload.get("full_name"));
        
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "user", user));
    }

    @PostMapping("/upload-avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
        System.out.println("UserController: Processing upload-avatar request");
        Integer userId = (Integer) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        System.out.println("UserController: Current User ID: " + userId);
        User user = userRepository.findById(userId).orElseThrow();

        if (file != null && !file.isEmpty()) {
            Map uploadResult = cloudinaryService.uploadFile(file, "appad/avatars");
            String avatarUrl = (String) uploadResult.get("secure_url");
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "avatar_url", avatarUrl));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No file uploaded"));
    }

    @GetMapping("/profile/{userId}/stats")
    public ResponseEntity<?> getUserStats(@PathVariable Integer userId) {
        // Count following (users this user follows)
        long following = followRepository.countByUserUserId(userId);
        // Count followers (users following this user) - For artists only, but show 0 for regular users
        long followers = 0; // Regular users don't have followers in this schema
        // Count playlists
        long playlists = playlistRepository.countByUserId(userId);
        
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("following", following);
        stats.put("followers", followers);
        stats.put("playlists", playlists);
        
        return ResponseEntity.ok(Map.of("success", true, "data", stats));
    }

    @PostMapping("/apply-artist")
    public ResponseEntity<?> applyArtist() {
        Integer userId = (Integer) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userId).orElseThrow();
        
        if ("artist".equals(user.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "You are already an artist"));
        }
        
        user.setIsBanned(2); // Pending artist
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "Application submitted successfully"));
    }
}
