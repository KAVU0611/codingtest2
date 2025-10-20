package com.example.playlist.model;

public class SongEntry {
    private final String albumName;
    private final int trackNumber;
    private final String title;
    private final String artist;
    private final String duration;
    private final int durationSeconds;

    public SongEntry(String albumName, int trackNumber, String title, String artist, String duration, int durationSeconds) {
        this.albumName = albumName;
        this.trackNumber = trackNumber;
        this.title = title;
        this.artist = artist;
        this.duration = duration;
        this.durationSeconds = durationSeconds;
    }

    public String getAlbumName() {
        return albumName;
    }

    public int getTrackNumber() {
        return trackNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getDuration() {
        return duration;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }
}
