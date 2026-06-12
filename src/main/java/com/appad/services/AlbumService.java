package com.appad.services;

import com.appad.models.Album;
import com.appad.repository.AlbumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AlbumService {
    private final AlbumRepository albumRepository;
    private final com.appad.repository.SongRepository songRepository;

    public List<Album> getAllAlbums() {
        return albumRepository.findAll();
    }

    public Optional<Album> getAlbumById(Integer id) {
        return albumRepository.findById(id);
    }

    public List<Album> getAlbumsByArtist(Integer artistId) {
        List<Album> albums = albumRepository.findByArtistId(artistId);
        for (Album album : albums) {
            album.setSongCount(songRepository.countByAlbumId(album.getAlbumId()));
        }
        return albums;
    }

    public Album createAlbum(Album album) {
        return albumRepository.save(album);
    }

    public Album updateAlbum(Integer id, Album albumData) {
        Album album = albumRepository.findById(id).orElseThrow(() -> new RuntimeException("Album not found"));
        if (albumData.getTitle() != null) album.setTitle(albumData.getTitle());
        if (albumData.getReleaseDate() != null) album.setReleaseDate(albumData.getReleaseDate());
        if (albumData.getCoverUrl() != null) album.setCoverUrl(albumData.getCoverUrl());
        if (albumData.getIsPremium() != null) album.setIsPremium(albumData.getIsPremium());
        if (albumData.getPrice() != null) album.setPrice(albumData.getPrice());
        
        return albumRepository.save(album);
    }

    public void deleteAlbum(Integer id) {
        albumRepository.deleteById(id);
    }
}
