package com.example.playlist;

import com.example.playlist.model.SongEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PlaylistService {

    public List<SongEntry> loadSongs(Path playlistDir) throws IOException {
        return loadSongs(playlistDir, false);
    }

    public List<SongEntry> loadSongs(Path playlistDir, boolean recursive) throws IOException {
        if (playlistDir == null) {
            throw new IllegalArgumentException("playlistDir must not be null");
        }
        if (!Files.exists(playlistDir)) {
            throw new IOException("Playlist directory does not exist: " + playlistDir);
        }
        if (!Files.isDirectory(playlistDir)) {
            throw new IOException("Playlist path is not a directory: " + playlistDir);
        }

        List<Path> albumFiles;
        if (recursive) {
            try (Stream<Path> stream = Files.walk(playlistDir)) {
                albumFiles = stream
                        .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tsv"))
                        .sorted()
                        .collect(Collectors.toList());
            }
        } else {
            try (Stream<Path> stream = Files.list(playlistDir)) {
                albumFiles = stream
                        .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tsv"))
                        .sorted()
                        .collect(Collectors.toList());
            }
        }

        List<SongEntry> songs = new ArrayList<>();
        for (Path albumFile : albumFiles) {
            songs.addAll(readAlbum(albumFile));
        }
        return songs;
    }

    private List<SongEntry> readAlbum(Path albumFile) throws IOException {
        List<SongEntry> songs = new ArrayList<>();
        String albumName = stripExtension(albumFile.getFileName().toString());
        try (BufferedReader reader = Files.newBufferedReader(albumFile, StandardCharsets.UTF_8)) {
            String line;
            int trackNumber = 1;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t");
                if (parts.length < 3) {
                    throw new IOException("Invalid line in album " + albumFile + ": " + line);
                }
                String title = parts[0].trim();
                String artist = parts[1].trim();
                String durationText = parts[2].trim();
                int durationSeconds = parseDuration(durationText);
                songs.add(new SongEntry(albumName, trackNumber, title, artist, durationText, durationSeconds));
                trackNumber++;
            }
        }
        return songs;
    }

    private String stripExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0) {
            return filename;
        }
        return filename.substring(0, dotIndex);
    }

    private int parseDuration(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        String[] parts = text.split(":");
        try {
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                if (minutes < 0 || seconds < 0 || seconds >= 60) {
                    return -1;
                }
                return minutes * 60 + seconds;
            } else if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                if (hours < 0 || minutes < 0 || minutes >= 60 || seconds < 0 || seconds >= 60) {
                    return -1;
                }
                return hours * 3600 + minutes * 60 + seconds;
            }
        } catch (NumberFormatException ex) {
            return -1;
        }
        return -1;
    }

    public boolean isParsableDuration(String text) {
        return parseDuration(text) >= 0;
    }

    public Path resolvePlaylistDir(String directory) {
        if (directory == null || directory.isBlank()) {
            return Paths.get("playlist");
        }
        return Paths.get(directory);
    }

    public List<SongEntry> filterSongs(List<SongEntry> songs, String albumFilter, String artistFilter, String titlePrefix) {
        String normalizedAlbum = normalize(albumFilter);
        String normalizedArtist = normalize(artistFilter);
        String normalizedPrefix = normalize(titlePrefix);

        return songs.stream()
                .filter(song -> normalizedAlbum == null || normalize(song.getAlbumName()).contains(normalizedAlbum))
                .filter(song -> normalizedArtist == null || normalize(song.getArtist()).contains(normalizedArtist))
                .filter(song -> {
                    if (normalizedPrefix == null) {
                        return true;
                    }
                    String title = normalize(song.getTitle());
                    return title != null && title.startsWith(normalizedPrefix);
                })
                .collect(Collectors.toList());
    }

    public List<SongEntry> sortSongs(List<SongEntry> songs, boolean sortByDuration) {
        Comparator<SongEntry> comparator;
        if (sortByDuration) {
            comparator = Comparator
                    .comparingInt((SongEntry song) -> song.getDurationSeconds() >= 0 ? song.getDurationSeconds() : Integer.MAX_VALUE)
                    .thenComparing(SongEntry::getAlbumName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(SongEntry::getTrackNumber);
        } else {
            comparator = Comparator.comparing(SongEntry::getAlbumName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(SongEntry::getTrackNumber);
        }
        return songs.stream().sorted(comparator).collect(Collectors.toList());
    }

    private String normalize(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    public Path writeAlbum(Path playlistDir, String albumName, List<String> lines) throws IOException {
        Objects.requireNonNull(playlistDir, "playlistDir");
        Objects.requireNonNull(albumName, "albumName");
        Objects.requireNonNull(lines, "lines");

        if (!Files.exists(playlistDir)) {
            Files.createDirectories(playlistDir);
        }
        if (!Files.isDirectory(playlistDir)) {
            throw new IOException("Playlist path is not a directory: " + playlistDir);
        }

        String safeFileName = sanitizeFileName(albumName) + ".tsv";
        Path file = playlistDir.resolve(safeFileName);
        if (Files.exists(file)) {
            throw new IOException("Album already exists: " + file.getFileName());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
