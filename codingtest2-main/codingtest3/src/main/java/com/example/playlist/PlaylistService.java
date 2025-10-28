package com.example.playlist;

import com.example.playlist.model.SongEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PlaylistService {

    public List<SongEntry> loadSongs(Path playlistDir) throws IOException {
        return loadSongs(playlistDir, false);
    }

    public List<SongEntry> loadSongs(Path playlistDir, boolean recursive) throws IOException {
        if (playlistDir == null) throw new IllegalArgumentException("playlistDir must not be null");
        if (!Files.exists(playlistDir)) throw new IOException("Playlist directory does not exist: " + playlistDir);
        if (!Files.isDirectory(playlistDir)) throw new IOException("Playlist path is not a directory: " + playlistDir);

        List<Path> albumFiles;
        if (recursive) {
            try (Stream<Path> stream = Files.walk(playlistDir)) {
                albumFiles = stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tsv"))
                        .sorted().collect(Collectors.toList());
            }
        } else {
            try (Stream<Path> stream = Files.list(playlistDir)) {
                albumFiles = stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tsv"))
                        .sorted().collect(Collectors.toList());
            }
        }

        List<SongEntry> songs = new ArrayList<>();
        for (Path albumFile : albumFiles) songs.addAll(readAlbum(albumFile));
        return songs;
    }

    private List<SongEntry> readAlbum(Path albumFile) throws IOException {
        List<SongEntry> songs = new ArrayList<>();
        String albumName = stripExtension(albumFile.getFileName().toString());
        try (BufferedReader reader = Files.newBufferedReader(albumFile, StandardCharsets.UTF_8)) {
            String line; int track = 1;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\t");
                if (parts.length < 3) throw new IOException("Invalid line in album " + albumFile + ": " + line);
                String title = parts[0].trim();
                String artist = parts[1].trim();
                String durationText = parts[2].trim();
                int durationSeconds = parseDuration(durationText);
                songs.add(new SongEntry(albumName, track, title, artist, durationText, durationSeconds));
                track++;
            }
        }
        return songs;
    }

    private String stripExtension(String filename) {
        int i = filename.lastIndexOf('.');
        return i <= 0 ? filename : filename.substring(0, i);
    }

    private int parseDuration(String text) {
        if (text == null || text.isBlank()) return -1;
        String[] parts = text.split(":");
        try {
            if (parts.length == 2) {
                int m = Integer.parseInt(parts[0]);
                int s = Integer.parseInt(parts[1]);
                if (m < 0 || s < 0 || s >= 60) return -1;
                return m * 60 + s;
            } else if (parts.length == 3) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int s = Integer.parseInt(parts[2]);
                if (h < 0 || m < 0 || m >= 60 || s < 0 || s >= 60) return -1;
                return h * 3600 + m * 60 + s;
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    public boolean isParsableDuration(String text) { return parseDuration(text) >= 0; }

    public Path resolvePlaylistDir(String directory) {
        if (directory == null || directory.isBlank()) return Paths.get("playlist");
        return Paths.get(directory);
    }

    public List<SongEntry> filterSongs(List<SongEntry> songs, String albumFilter, String artistFilter, String titlePrefix) {
        String na = normalize(albumFilter), nr = normalize(artistFilter), np = normalize(titlePrefix);
        return songs.stream()
                .filter(s -> na == null || normalize(s.getAlbumName()).contains(na))
                .filter(s -> nr == null || normalize(s.getArtist()).contains(nr))
                .filter(s -> {
                    if (np == null) return true;
                    String t = normalize(s.getTitle());
                    return t != null && t.startsWith(np);
                })
                .collect(Collectors.toList());
    }

    public List<SongEntry> sortSongs(List<SongEntry> songs, boolean sortByDuration) {
        Comparator<SongEntry> cmp;
        if (sortByDuration) {
            cmp = Comparator
                    .comparingInt((SongEntry s) -> s.getDurationSeconds() >= 0 ? s.getDurationSeconds() : Integer.MAX_VALUE)
                    .thenComparing(SongEntry::getAlbumName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(SongEntry::getTrackNumber);
        } else {
            cmp = Comparator.comparing(SongEntry::getAlbumName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(SongEntry::getTrackNumber);
        }
        return songs.stream().sorted(cmp).collect(Collectors.toList());
    }

    private String normalize(String t) {
        if (t == null) return null;
        String x = t.trim();
        return x.isEmpty() ? null : x.toLowerCase(Locale.ROOT);
    }

    public Path writeAlbum(Path playlistDir, String albumName, List<String> lines) throws IOException {
        Objects.requireNonNull(playlistDir, "playlistDir");
        Objects.requireNonNull(albumName, "albumName");
        Objects.requireNonNull(lines, "lines");
        if (!Files.exists(playlistDir)) Files.createDirectories(playlistDir);
        if (!Files.isDirectory(playlistDir)) throw new IOException("Playlist path is not a directory: " + playlistDir);
        String safe = sanitizeFileName(albumName) + ".tsv";
        Path file = playlistDir.resolve(safe);
        if (Files.exists(file)) throw new IOException("Album already exists: " + file.getFileName());
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
