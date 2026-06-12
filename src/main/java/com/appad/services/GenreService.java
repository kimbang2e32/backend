package com.appad.services;

import com.appad.models.Genre;
import com.appad.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GenreService {
    private final GenreRepository genreRepository;
    private final com.appad.repository.SongRepository songRepository;

    public List<Genre> getAllGenres() {
        List<Genre> genres = genreRepository.findAll();
        for (Genre g : genres) {
            g.setSongCount(songRepository.countByGenreId(g.getGenreId()));
            songRepository.findFirstByGenreIdAndStatusOrderByCreatedAtDesc(g.getGenreId(), 1)
                .ifPresent(s -> g.setCoverUrl(s.getCoverUrl()));
        }
        return genres;
    }

    public Optional<Genre> getGenreById(Integer id) {
        return genreRepository.findById(id).map(g -> {
            g.setSongCount(songRepository.countByGenreId(g.getGenreId()));
            songRepository.findFirstByGenreIdAndStatusOrderByCreatedAtDesc(g.getGenreId(), 1)
                .ifPresent(s -> g.setCoverUrl(s.getCoverUrl()));
            return g;
        });
    }

    public Genre createGenre(Genre genre) {
        return genreRepository.save(genre);
    }

    public Genre updateGenre(Integer id, Genre genreData) {
        Genre genre = genreRepository.findById(id).orElseThrow(() -> new RuntimeException("Genre not found"));
        if (genreData.getName() != null) genre.setName(genreData.getName());
        if (genreData.getDescription() != null) genre.setDescription(genreData.getDescription());
        
        return genreRepository.save(genre);
    }

    public void deleteGenre(Integer id) {
        genreRepository.deleteById(id);
    }
}
