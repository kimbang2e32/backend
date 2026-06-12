package com.appad.controllers;

import com.appad.models.Comment;
import com.appad.models.User;
import com.appad.repository.CommentRepository;
import com.appad.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final com.appad.repository.SongRepository songRepository;
    private final com.appad.repository.ArtistRepository artistRepository;
    private final com.appad.services.NotificationService notificationService;

    @GetMapping("/{type}/{targetId}")
    public ResponseEntity<?> getComments(@PathVariable String type, @PathVariable Long targetId) {
        if (!"song".equalsIgnoreCase(type)) {
            return ResponseEntity.ok(Map.of("success", true, "data", Map.of("comments", List.of(), "rating_stats", Map.of())));
        }

        List<Comment> comments = commentRepository.findBySongIdOrderByCreatedAtDesc(targetId.intValue());
        
        // Calculate rating stats
        int total = 0;
        int[] stars = new int[6]; // index 1-5
        double sum = 0;
        
        for (Comment c : comments) {
            if (c.getRating() != null && c.getRating() >= 1 && c.getRating() <= 5) {
                total++;
                stars[c.getRating()]++;
                sum += c.getRating();
            }
        }
        
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("average_rating", total > 0 ? sum / total : 0.0);
        stats.put("total_ratings", total);
        stats.put("one_star", stars[1]);
        stats.put("two_star", stars[2]);
        stats.put("three_star", stars[3]);
        stats.put("four_star", stars[4]);
        stats.put("five_star", stars[5]);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
            "comments", comments,
            "rating_stats", stats
        ));
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/artist/{artistId}")
    public ResponseEntity<?> getArtistReviews(@PathVariable Integer artistId) {
        List<Comment> comments = commentRepository.findByArtistId(artistId);
        
        // Enhance comment with song title for artist dashboard
        List<java.util.Map<String, Object>> enhanced = new java.util.ArrayList<>();
        for (Comment c : comments) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("commentId", c.getCommentId());
            map.put("user", c.getUser());
            map.put("songId", c.getSongId());
            map.put("content", c.getContent());
            map.put("rating", c.getRating());
            map.put("createdAt", c.getCreatedAt());
            
            // Add song title
            songRepository.findById(c.getSongId()).ifPresent(song -> {
                map.put("songTitle", song.getTitle());
            });
            
            enhanced.add(map);
        }

        return ResponseEntity.ok(Map.of("success", true, "data", enhanced));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllReviews() {
        List<Comment> comments = commentRepository.findAll();
        
        List<java.util.Map<String, Object>> enhanced = new java.util.ArrayList<>();
        for (Comment c : comments) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("commentId", c.getCommentId());
            map.put("user", c.getUser());
            map.put("songId", c.getSongId());
            map.put("content", c.getContent());
            map.put("rating", c.getRating());
            map.put("createdAt", c.getCreatedAt());
            
            songRepository.findById(c.getSongId()).ifPresent(song -> {
                map.put("songTitle", song.getTitle());
            });
            
            enhanced.add(map);
        }

        return ResponseEntity.ok(Map.of("success", true, "data", enhanced));
    }

    @PostMapping
    public ResponseEntity<?> addComment(@RequestBody Map<String, Object> payload,
                                       @org.springframework.security.core.annotation.AuthenticationPrincipal Integer authUserId) {
        Integer userId = authUserId;
        if (userId == null && payload.containsKey("userId")) {
             userId = Double.valueOf(payload.get("userId").toString()).intValue();
        }
        
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "User not authenticated"));
        }

        Integer targetId = Double.valueOf(payload.get("targetId").toString()).intValue();
        String type = payload.get("type").toString();
        String content = payload.get("content").toString();
        Integer rating = payload.containsKey("rating") ? Double.valueOf(payload.get("rating").toString()).intValue() : null;

        if (!"song".equalsIgnoreCase(type)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Only song comments are supported"));
        }

        User user = userRepository.findById(userId).orElseThrow();
        
        // One user per song constraint: Update if exists
        java.util.Optional<Comment> existing = commentRepository.findBySongIdAndUserUserId(targetId, userId);
        Comment comment;
        if (existing.isPresent()) {
            comment = existing.get();
        } else {
            comment = new Comment();
            comment.setUser(user);
            comment.setSongId(targetId);
        }
        
        comment.setContent(content);
        comment.setRating(rating);
        comment.setCreatedAt(java.time.LocalDateTime.now()); // Update timestamp to newest edit
        
        commentRepository.save(comment);

        // Update target average rating
        Double avg = commentRepository.getAverageRating(targetId);
        songRepository.findById(targetId).ifPresent(song -> {
            song.setAverageRating(avg != null ? avg : 0.0);
            songRepository.save(song);

            // Notify Artist only on new comment
            if (existing.isEmpty() && song.getArtistId() != null) {
                artistRepository.findById(song.getArtistId()).ifPresent(artist -> {
                    if (artist.getUserId() != null) {
                        userRepository.findById(artist.getUserId()).ifPresent(artistUser -> {
                            notificationService.createNotification(artistUser,
                                    "Bình luận mới",
                                    user.getUsername() + " đã bình luận về bài hát \"" + song.getTitle() + "\"",
                                    "new_comment",
                                    Map.of("songId", targetId, "commentId", comment.getCommentId()));
                        });
                    }
                });
            }
        });

        return ResponseEntity.ok(Map.of("success", true, "comment", comment));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId,
                                          @org.springframework.security.core.annotation.AuthenticationPrincipal Integer authUserId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow();
        
        // Security check: Only owner or if sysadmin (simplified for now as owner check)
        if (authUserId != null && !authUserId.equals(comment.getUser().getUserId())) {
             return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized to delete this comment"));
        }

        Integer songId = comment.getSongId();
        commentRepository.delete(comment);

        // Update target average rating
        if (songId != null) {
            Double avg = commentRepository.getAverageRating(songId);
            songRepository.findById(songId).ifPresent(song -> {
                song.setAverageRating(avg != null ? avg : 0.0);
                songRepository.save(song);
            });
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Comment deleted"));
    }
}
