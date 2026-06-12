package com.appad.controllers;

import com.appad.repository.AlbumRepository;
import com.appad.repository.ArtistRepository;
import com.appad.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {
    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;

    @GetMapping
    public ResponseEntity<?> search(@RequestParam String query) {
        Map<String, Object> results = new HashMap<>();
        
        // Tìm bài hát (theo tiêu đề hoặc lời bài hát, chỉ hiện bài hát đang active)
        results.put("songs", songRepository.findByTitleContainingIgnoreCaseAndStatus(query, 1, org.springframework.data.domain.PageRequest.of(0, 50)));

        // Tìm nghệ sĩ
        java.util.List<com.appad.models.Artist> artists = artistRepository.findByNameContainingIgnoreCase(query);
        java.util.List<Map<String, Object>> artistResults = new java.util.ArrayList<>();
        for (com.appad.models.Artist a : artists) {
            Map<String, Object> artistMap = new java.util.HashMap<>();
            artistMap.put("artistId", a.getArtistId());
            artistMap.put("name", a.getName());
            artistMap.put("bio", a.getBio());
            artistMap.put("imageUrl", a.getImageUrl());
            artistMap.put("songCount", songRepository.findByArtistIdAndStatus(a.getArtistId(), 1).size());
            artistResults.add(artistMap);
        }
        results.put("artists", artistResults);

        // Tìm album (cũng nên lọc album active nếu có status, hiện tại giả định là hiển thị nếu chứa tên)
        results.put("albums", albumRepository.findByTitleContainingIgnoreCase(query));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", results);
        
        return ResponseEntity.ok(response);
    }
}
