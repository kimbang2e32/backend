package com.appad.controllers;

import com.appad.models.Song;
import com.appad.models.Artist;
import com.appad.models.Genre;
import com.appad.repository.SongRepository;
import com.appad.repository.ArtistRepository;
import com.appad.repository.GenreRepository;
import com.appad.services.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/studio")
@RequiredArgsConstructor
public class ArtistStudioController {
    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final GenreRepository genreRepository;
    private final CloudinaryService cloudinaryService;
    private final com.appad.repository.AlbumRepository albumRepository;
    private final com.appad.repository.FollowRepository followRepository;
    private final com.appad.repository.UserRepository userRepository;
    private final com.appad.services.NotificationService notificationService;

    @PostMapping("/album")
    public ResponseEntity<?> createAlbum(
            @RequestParam("title") String title,
            @RequestParam("artistId") Integer artistId,
            @RequestParam("price") Double price,
            @RequestParam("isPremium") Integer isPremium,
            @RequestParam(value = "releaseDate", required = false) String releaseDateStr,
            @RequestParam("coverFile") MultipartFile coverFile) {
        
        Artist artist = artistRepository.findById(artistId).orElseThrow();
        Map uploadResult = cloudinaryService.uploadFile(coverFile, "appad/albums");
        String coverUrl = (String) uploadResult.get("secure_url");
        
        com.appad.models.Album album = new com.appad.models.Album();
        album.setTitle(title);
        album.setArtistId(artist.getArtistId());
        album.setCoverUrl(coverUrl);
        album.setPrice(price);
        album.setIsPremium(isPremium);
        
        if (releaseDateStr != null && !releaseDateStr.isEmpty()) {
            try {
                album.setReleaseDate(java.time.LocalDateTime.parse(releaseDateStr.replace(" ", "T")));
            } catch (Exception e) {
                album.setReleaseDate(java.time.LocalDateTime.now());
            }
        } else {
            album.setReleaseDate(java.time.LocalDateTime.now());
        }
        
        albumRepository.save(album);
        
        // Notify followers
        var followers = followRepository.findByArtistArtistId(artist.getArtistId());
        for (var follow : followers) {
             notificationService.createNotification(follow.getUser(),
                     "Album mới",
                     artist.getName() + " vừa ra mắt album \"" + album.getTitle() + "\"",
                     "new_album",
                     Map.of("albumId", album.getAlbumId(), "artistId", artist.getArtistId()));
        }
        
        return ResponseEntity.ok(Map.of("success", true, "album", album));
    }

    @PutMapping("/album/{albumId}")
    public ResponseEntity<?> updateAlbum(
            @PathVariable Integer albumId,
            @RequestParam("title") String title,
            @RequestParam("price") Double price,
            @RequestParam("isPremium") Integer isPremium,
            @RequestParam(value = "releaseDate", required = false) String releaseDateStr,
            @RequestParam(value = "coverFile", required = false) MultipartFile coverFile) {

        com.appad.models.Album album = albumRepository.findById(albumId).orElseThrow();
        album.setTitle(title);
        album.setPrice(price);
        album.setIsPremium(isPremium);
        
        if (releaseDateStr != null && !releaseDateStr.isEmpty()) {
            try {
                album.setReleaseDate(java.time.LocalDateTime.parse(releaseDateStr.replace(" ", "T")));
            } catch (Exception e) {}
        }

        if (coverFile != null && !coverFile.isEmpty()) {
            Map uploadResult = cloudinaryService.uploadFile(coverFile, "appad/albums");
            String coverUrl = (String) uploadResult.get("secure_url");
            album.setCoverUrl(coverUrl);
        }

        albumRepository.save(album);
        return ResponseEntity.ok(Map.of("success", true, "album", album));
    }

    @DeleteMapping("/album/{albumId}")
    public ResponseEntity<?> deleteAlbum(@PathVariable Integer albumId) {
        com.appad.models.Album album = albumRepository.findById(albumId).orElseThrow();
        // Option: delete all songs in album or just set albumId to null
        // For simplicity, just delete the album
        albumRepository.delete(album);
        return ResponseEntity.ok(Map.of("success", true, "message", "Album deleted successfully"));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadSong(
            @RequestParam("title") String title,
            @RequestParam("artistId") Integer artistId,
            @RequestParam("genreId") Integer genreId,
            @RequestParam(value = "albumId", required = false) Integer albumId,
            @RequestParam("price") Double price,
            @RequestParam("isPremium") Integer isPremium,
            @RequestParam("lyrics") String lyrics,
            @RequestParam(value = "status", required = false, defaultValue = "1") Integer status,
            @RequestParam(value = "releaseDate", required = false) String releaseDateStr,
            @RequestParam("musicFile") MultipartFile musicFile,
            @RequestParam("coverFile") MultipartFile coverFile) {

        Artist artist = artistRepository.findById(artistId).orElseThrow();
        Genre genre = genreRepository.findById(genreId).orElseThrow();

        // Upload to Cloudinary
        Map musicUploadResult = cloudinaryService.uploadFile(musicFile, "appad/music");
        Map coverUploadResult = cloudinaryService.uploadFile(coverFile, "appad/covers");
        
        String musicUrl = (String) musicUploadResult.get("secure_url");
        String coverUrl = (String) coverUploadResult.get("secure_url");
        Double duration = 0.0;
        if (musicUploadResult.get("duration") != null) {
            duration = Double.valueOf(musicUploadResult.get("duration").toString());
        }

        Song song = new Song();
        song.setTitle(title);
        song.setArtistId(artist.getArtistId()); 
        song.setGenreId(genre.getGenreId());
        if (albumId != null) song.setAlbumId(albumId);
        song.setPrice(price);
        // Safety: If price > 0, always set as premium
        if (price > 0) isPremium = 1;
        song.setIsPremium(isPremium);
        song.setLyrics(lyrics);
        song.setFileUrl(musicUrl);
        song.setCoverUrl(coverUrl);
        song.setDuration(duration.intValue());
        song.setStatus(status);
        
        if (releaseDateStr != null && !releaseDateStr.isEmpty()) {
            try {
                // Try parsing ISO format or specific formats if needed. 
                // Simple approach: append :00 if missing seconds/time 
                // Or better: use java.time.LocalDateTime.parse with formatter if strict
                // Assuming standard "2024-01-01T12:00:00" or similar from frontend
                song.setReleaseDate(java.time.LocalDateTime.parse(releaseDateStr.replace(" ", "T")));
            } catch (Exception e) {
                song.setReleaseDate(java.time.LocalDateTime.now());
            }
        } else {
            song.setReleaseDate(java.time.LocalDateTime.now());
        }
        
        songRepository.save(song); 

        // Notify followers
        var followers = followRepository.findByArtistArtistId(artist.getArtistId());
        for (var follow : followers) {
             notificationService.createNotification(follow.getUser(),
                     "Bài hát mới",
                     artist.getName() + " vừa ra mắt bài hát \"" + song.getTitle() + "\"",
                     "new_song",
                     Map.of("songId", song.getSongId(), "artistId", artist.getArtistId()));
        }

        return ResponseEntity.ok(Map.of("success", true, "song", song)); 
    }

    @PutMapping("/{songId}")
    public ResponseEntity<?> updateSong(
            @PathVariable Integer songId,
            @RequestParam("title") String title,
            @RequestParam("price") Double price,
            @RequestParam(value = "albumId", required = false) Integer albumId,
            @RequestParam("isPremium") Integer isPremium,
            @RequestParam("lyrics") String lyrics,
            @RequestParam("genreId") Integer genreId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "releaseDate", required = false) String releaseDateStr,
            @RequestParam(value = "coverFile", required = false) MultipartFile coverFile,
            @RequestParam(value = "musicFile", required = false) MultipartFile musicFile) {

        System.out.println("ArtistStudioController: Updating song with ID: " + songId);
        Song song = songRepository.findById(songId).orElseThrow();
        song.setTitle(title);
        song.setPrice(price);
        // Safety: If price > 0, always set as premium
        if (price > 0) isPremium = 1;
        song.setIsPremium(isPremium);
        song.setLyrics(lyrics);
        song.setGenreId(genreId);
        if (albumId != null) song.setAlbumId(albumId);
        else song.setAlbumId(null); 
        
        if (status != null) song.setStatus(status);
        
        if (releaseDateStr != null && !releaseDateStr.isEmpty()) {
             try {
                song.setReleaseDate(java.time.LocalDateTime.parse(releaseDateStr.replace(" ", "T")));
            } catch (Exception e) {}
        }

        if (coverFile != null && !coverFile.isEmpty()) {
            Map uploadResult = cloudinaryService.uploadFile(coverFile, "appad/covers");
            String coverUrl = (String) uploadResult.get("secure_url");
            song.setCoverUrl(coverUrl);
        }

        if (musicFile != null && !musicFile.isEmpty()) {
            Map musicUploadResult = cloudinaryService.uploadFile(musicFile, "appad/music");
            String musicUrl = (String) musicUploadResult.get("secure_url");
            song.setFileUrl(musicUrl);
            if (musicUploadResult.get("duration") != null) {
                Double duration = Double.valueOf(musicUploadResult.get("duration").toString());
                song.setDuration(duration.intValue());
            }
        }

        songRepository.save(song);
        return ResponseEntity.ok(Map.of("success", true, "song", song));
    }

    @DeleteMapping("/{songId}")
    public ResponseEntity<?> deleteSong(@PathVariable Integer songId) {
        Song song = songRepository.findById(songId).orElseThrow();
        
        // Xóa file trên Cloudinary (nếu cần thiết, cần lấy publicId chuẩn xác)
        // cloudinaryService.deleteFile(...);
        
        songRepository.delete(song);
        return ResponseEntity.ok(Map.of("success", true, "message", "Deleted successfully"));
    }
}
