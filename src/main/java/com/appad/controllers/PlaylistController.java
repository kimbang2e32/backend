package com.appad.controllers;

import com.appad.models.Playlist;
import com.appad.models.PlaylistSong;
import com.appad.repository.PlaylistRepository;
import com.appad.repository.PlaylistSongRepository;
import com.appad.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController {
    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final SongRepository songRepository;
    private final com.appad.services.SongService songService;
    private final com.appad.services.CloudinaryService cloudinaryService;
    private final com.appad.utils.PlaylistShareUtils playlistShareUtils;

    private Integer getCurrentUserId() {
        return (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping("/my-playlists")
    public ResponseEntity<?> getMyPlaylists() {
        Integer userId = getCurrentUserId();
        var playlists = playlistRepository.findByUserId(userId);
        var data = playlists.stream().map(p -> {
             java.util.Map<String, Object> map = new java.util.HashMap<>();
             map.put("playlist_id", p.getPlaylistId());
             map.put("name", p.getName());
             map.put("description", p.getDescription());
             
             // Get cover_url, fallback to first song's cover if null
             String coverUrl = p.getCoverUrl();
             if (coverUrl == null || coverUrl.isEmpty()) {
                 var links = playlistSongRepository.findByPlaylistId(p.getPlaylistId());
                 if (!links.isEmpty()) {
                     var firstSong = songRepository.findById(links.get(0).getSongId());
                     if (firstSong.isPresent()) {
                         coverUrl = firstSong.get().getCoverUrl();
                     }
                 }
             }
             map.put("cover_url", coverUrl);
             
             int songCount = playlistSongRepository.countByPlaylistId(p.getPlaylistId());
             map.put("playlist_song_count", songCount);
             map.put("song_count", songCount);
             return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @PostMapping
    public ResponseEntity<?> createPlaylist(@RequestBody Map<String, String> payload) {
        Integer userId = getCurrentUserId();
        Playlist playlist = Playlist.builder()
                .userId(userId)
                .name(payload.get("name"))
                .description(payload.get("description"))
                .build();
        return ResponseEntity.ok(Map.of("success", true, "data", playlistRepository.save(playlist)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPlaylistById(@PathVariable Integer id) {
        return playlistRepository.findById(id)
                .map(p -> ResponseEntity.ok(Map.of("success", true, "data", p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/songs")
    public ResponseEntity<?> getPlaylistSongs(@PathVariable Integer id) {
        // Lấy songs theo thứ tự đã sắp xếp
        var links = playlistSongRepository.findByPlaylistIdOrderByOrderIndexAsc(id);
        var songIds = links.stream()
                           .map(PlaylistSong::getSongId)
                           .collect(Collectors.toList());
        
        // Lấy songs và giữ nguyên thứ tự
        var songsMap = songRepository.findAllById(songIds).stream()
            .collect(Collectors.toMap(s -> s.getSongId(), s -> s));
        var songs = songIds.stream()
            .map(songsMap::get)
            .filter(s -> s != null)
            .collect(Collectors.toList());
            
        songService.populateAccessInfo(songs, getCurrentUserId());
        return ResponseEntity.ok(Map.of("success", true, "data", songs));
    }

    @PostMapping("/{playlistId}/songs")
    public ResponseEntity<?> addSongToPlaylist(@PathVariable Integer playlistId, @RequestBody Map<String, Long> payload) {
        Integer songId = payload.get("song_id").intValue();
        if (playlistSongRepository.existsByPlaylistIdAndSongId(playlistId, songId)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Song already in playlist"));
        }
        
        // Lấy order index lớn nhất hiện tại và +1
        var existingSongs = playlistSongRepository.findByPlaylistIdOrderByOrderIndexAsc(playlistId);
        int nextOrder = 0;
        if (!existingSongs.isEmpty()) {
            var lastSong = existingSongs.get(existingSongs.size() - 1);
            nextOrder = (lastSong.getOrderIndex() != null ? lastSong.getOrderIndex() : 0) + 1;
        }
        
        playlistSongRepository.save(PlaylistSong.builder()
            .playlistId(playlistId)
            .songId(songId)
            .orderIndex(nextOrder)
            .build());
        return ResponseEntity.ok(Map.of("success", true, "message", "Song added to playlist"));
    }

    @PutMapping("/{playlistId}/reorder")
    public ResponseEntity<?> reorderPlaylistSongs(@PathVariable Integer playlistId, @RequestBody Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Integer> songIds = ((java.util.List<?>) payload.get("song_ids"))
                .stream()
                .map(obj -> obj instanceof Number ? ((Number) obj).intValue() : Integer.parseInt(obj.toString()))
                .collect(Collectors.toList());
            
            // Cập nhật order cho từng song
            for (int i = 0; i < songIds.size(); i++) {
                Integer songId = songIds.get(i);
                var id = new PlaylistSong.PlaylistSongId(playlistId, songId);
                var optionalPs = playlistSongRepository.findById(id);
                if (optionalPs.isPresent()) {
                    var ps = optionalPs.get();
                    ps.setOrderIndex(i);
                    playlistSongRepository.save(ps);
                }
            }
            
            return ResponseEntity.ok(Map.of("success", true, "message", "Playlist order updated"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to reorder: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{playlistId}/songs/{songId}")
    public ResponseEntity<?> removeSongFromPlaylist(@PathVariable Integer playlistId, @PathVariable Integer songId) {
        var id = new PlaylistSong.PlaylistSongId(playlistId, songId);
        if (playlistSongRepository.existsById(id)) {
            playlistSongRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Song removed from playlist"));
        } else {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Song or Playlist not found"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlaylist(@PathVariable Integer id) {
        playlistRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Playlist deleted"));
    }

    @PutMapping(value = "/{playlistId}/cover", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updatePlaylistCover(
            @PathVariable Integer playlistId, 
            @RequestParam("cover") org.springframework.web.multipart.MultipartFile cover) {
        try {
            System.out.println("PlaylistController: Updating cover for playlist " + playlistId);
            if (cover != null) {
                System.out.println("File received: " + cover.getOriginalFilename() + ", size=" + cover.getSize());
            } else {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No file uploaded"));
            }
            
            var optionalPlaylist = playlistRepository.findById(playlistId);
            if (optionalPlaylist.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Playlist playlist = optionalPlaylist.get();
            
            // Verify ownership
            Integer userId = getCurrentUserId();
            if (!playlist.getUserId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
            }
            
            // Upload to Cloudinary
            Map uploadResult = cloudinaryService.uploadFile(cover, "playlists");
            String coverUrl = (String) uploadResult.get("secure_url");
            
            // Update playlist cover_url
            playlist.setCoverUrl(coverUrl);
            playlistRepository.save(playlist);
            
            return ResponseEntity.ok(Map.of(
                "success", true, 
                "message", "Cover updated",
                "data", Map.of("cover_url", coverUrl)
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to update cover: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/share-code")
    public ResponseEntity<?> getShareCode(@PathVariable Integer id) {
        var playlistOpt = playlistRepository.findById(id);
        if (playlistOpt.isEmpty()) return ResponseEntity.notFound().build();
        Playlist p = playlistOpt.get();
        
        var links = playlistSongRepository.findByPlaylistIdOrderByOrderIndexAsc(id);
        String songIds = links.stream()
                .map(l -> String.valueOf(l.getSongId()))
                .collect(Collectors.joining(","));
        
        String rawCode = p.getName() + "||" + songIds;
        String encryptedCode = playlistShareUtils.encrypt(rawCode);
        
        return ResponseEntity.ok(Map.of("success", true, "data", encryptedCode));
    }

    @GetMapping("/share/{code}")
    public ResponseEntity<?> getPlaylistByShareCode(@PathVariable String code) {
        try {
            String decoded = playlistShareUtils.decrypt(code);
            if (decoded == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid share code"));
            
            String[] parts = decoded.split("\\|\\|");
            if (parts.length < 2) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid share code"));
            
            String name = parts[0];
            String[] songIdArray = parts[1].split(",");
            java.util.List<Integer> songIds = java.util.Arrays.stream(songIdArray)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
            
            var songsMap = songRepository.findAllById(songIds).stream()
                .collect(Collectors.toMap(s -> s.getSongId(), s -> s));
            
            var songs = songIds.stream()
                .map(songsMap::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

            // Get user ID safely
            Integer userId = null;
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Integer) {
                userId = (Integer) auth.getPrincipal();
            }
            
            songService.populateAccessInfo(songs, userId);
            
            return ResponseEntity.ok(Map.of(
                "success", true, 
                "data", Map.of(
                    "name", name,
                    "songs", songs
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid share code: " + e.getMessage()));
        }
    }

    @PostMapping("/import")
    public ResponseEntity<?> importPlaylist(@RequestBody Map<String, String> payload) {
        String code = payload.get("code");
        if (code == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Code is required"));
        
        try {
            String decoded = playlistShareUtils.decrypt(code);
            if (decoded == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid share code"));
            
            String[] parts = decoded.split("\\|\\|");
            if (parts.length < 2) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid share code"));
            
            String name = parts[0];
            String[] songIdArray = parts[1].split(",");
            
            Integer userId = getCurrentUserId();
            
            // 1. Create new playlist
            Playlist newPlaylist = Playlist.builder()
                    .userId(userId)
                    .name(name + " (Imported)")
                    .description("Imported from shared snapshot")
                    .build();
            newPlaylist = playlistRepository.save(newPlaylist);
            
            // 2. Add songs to it
            for (int i = 0; i < songIdArray.length; i++) {
                try {
                    Integer songId = Integer.parseInt(songIdArray[i]);
                    playlistSongRepository.save(PlaylistSong.builder()
                            .playlistId(newPlaylist.getPlaylistId())
                            .songId(songId)
                            .orderIndex(i)
                            .build());
                } catch (Exception e) {
                    // Skip invalid song IDs
                }
            }
            
            return ResponseEntity.ok(Map.of("success", true, "message", "Playlist imported successfully", "data", newPlaylist));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Failed to import: " + e.getMessage()));
        }
    }
}
