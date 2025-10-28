package com.example.playlist;

import com.example.playlist.model.SongEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PlaylistApp {

    public static void main(String[] args) {
        PlaylistService service = new PlaylistService();
        CommandLineOptions options = CommandLineOptions.parse(args);
        if (options == null) {
            printUsage();
            return;
        }

        Path playlistDir = service.resolvePlaylistDir(options.playlistDir);

        switch (options.command) {
            case LIST:
                runList(service, playlistDir, options);
                break;
            case GUI:
                runGui(service, playlistDir, options);
                break;
            case ADD_ALBUM:
                runAddAlbum(service, playlistDir, options);
                break;
            case HELP:
                printUsage();
                break;
            default:
                System.err.println("Unknown command.");
                printUsage();
                break;
        }
    }

    private static void runList(PlaylistService service, Path playlistDir, CommandLineOptions options) {
        try {
            List<SongEntry> songs = service.loadSongs(playlistDir, options.recursive);
            songs = service.filterSongs(songs, options.albumFilter, options.artistFilter, options.titlePrefix);
            songs = service.sortSongs(songs, options.sortByDuration);
            printSongs(songs);
        } catch (IOException ex) {
            System.err.println("Failed to load playlist: " + ex.getMessage());
        }
    }

    private static void printSongs(List<SongEntry> songs) {
        if (songs.isEmpty()) {
            System.out.println("No songs found.");
            return;
        }
        String header = String.format("%-35s | %-5s | %-35s | %-25s | %-8s", "Album", "#", "Title", "Artist", "Duration");
        System.out.println(header);
        System.out.println("-".repeat(header.length()));
        for (SongEntry song : songs) {
            System.out.printf("%-35s | %-5d | %-35s | %-25s | %-8s%n",
                    song.getAlbumName(), song.getTrackNumber(), song.getTitle(), song.getArtist(), song.getDuration());
        }
        System.out.println();
        System.out.println("Total songs: " + songs.size());
    }

    private static void runAddAlbum(PlaylistService service, Path playlistDir, CommandLineOptions options) {
        String albumName = options.albumName;
        if (albumName == null || albumName.isBlank()) {
            System.err.println("Album name is required for add-album command.");
            return;
        }
        List<String> lines = promptForSongs(service);
        if (lines.isEmpty()) {
            System.err.println("Album must contain at least one song.");
            return;
        }
        try {
            Path file = service.writeAlbum(playlistDir, albumName, lines);
            System.out.println("Album saved: " + file);
        } catch (IOException ex) {
            System.err.println("Failed to save album: " + ex.getMessage());
        }
    }

    private static List<String> promptForSongs(PlaylistService service) {
        System.out.println("Enter song information. Leave the title empty to finish.");
        System.out.println("Durations must use mm:ss or hh:mm:ss format.");
        Scanner scanner = new Scanner(System.in);
        List<String> lines = new ArrayList<>();
        int track = 1;
        while (true) {
            System.out.print("Title for track " + track + " (blank to finish): ");
            String title = scanner.nextLine().trim();
            if (title.isEmpty()) {
                break;
            }
            System.out.print("Artist: ");
            String artist = scanner.nextLine().trim();
            String duration = promptForDuration(scanner, service);
            lines.add(String.join("\t", title, artist, duration));
            track++;
        }
        return lines;
    }

    private static String promptForDuration(Scanner scanner, PlaylistService service) {
        while (true) {
            System.out.print("Duration (mm:ss or hh:mm:ss): ");
            String duration = scanner.nextLine().trim();
            if (service.isParsableDuration(duration)) {
                return duration;
            }
            System.out.println("Invalid duration. Please enter the value again.");
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java com.example.playlist.PlaylistApp <command> [options]\n");
        System.out.println("Commands:");
        System.out.println("  list                 List songs in the playlist\n" +
                "                         --album <text> filter by album name\n" +
                "                         --artist <text> filter by artist name\n" +
                "                         --title-prefix <text> filter by song title prefix\n" +
                "                         --sort duration sort songs by duration\n" +
                "                         --playlist <dir> playlist directory (default: ./playlist)\n" +
                "                         --recursive include subfolders when reading .tsv files");
        System.out.println("  gui                  Launch GUI to browse playlist\n" +
                "                         --playlist <dir> playlist directory (default: ./playlist)\n" +
                "                         --recursive include subfolders when reading .tsv files");
        System.out.println("  add-album            Create a new album interactively\n" +
                "                         --name <album name> album title\n" +
                "                         --playlist <dir> playlist directory (default: ./playlist)\n" +
                "                         durations must be provided as mm:ss or hh:mm:ss");
        System.out.println("  help                 Show this message");
    }

    private enum Command {
        LIST, GUI, ADD_ALBUM, HELP
    }

    private static class CommandLineOptions {
        final Command command;
        final String playlistDir;
        final String albumFilter;
        final String artistFilter;
        final String titlePrefix;
        final boolean sortByDuration;
        final String albumName;
        final boolean recursive;

        private CommandLineOptions(Command command, String playlistDir, String albumFilter, String artistFilter,
                                   String titlePrefix, boolean sortByDuration, String albumName, boolean recursive) {
            this.command = command;
            this.playlistDir = playlistDir;
            this.albumFilter = albumFilter;
            this.artistFilter = artistFilter;
            this.titlePrefix = titlePrefix;
            this.sortByDuration = sortByDuration;
            this.albumName = albumName;
            this.recursive = recursive;
        }

        static CommandLineOptions parse(String[] args) {
            if (args.length == 0) {
                return new CommandLineOptions(Command.HELP, null, null, null, null, false, null, false);
            }
            Command command;
            switch (args[0]) {
                case "list":
                    command = Command.LIST;
                    break;
                case "gui":
                    command = Command.GUI;
                    break;
                case "add-album":
                    command = Command.ADD_ALBUM;
                    break;
                case "help":
                    command = Command.HELP;
                    break;
                default:
                    command = null;
                    break;
            }
            if (command == null) {
                return null;
            }
            String playlistDir = null;
            String albumFilter = null;
            String artistFilter = null;
            String titlePrefix = null;
            boolean sortByDuration = false;
            String albumName = null;
            boolean recursive = false;

            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--playlist":
                        if (i + 1 >= args.length) {
                            System.err.println("Missing value for --playlist");
                            return null;
                        }
                        playlistDir = args[++i];
                        break;
                    case "--album":
                        if (i + 1 >= args.length) {
                            System.err.println("Missing value for --album");
                            return null;
                        }
                        albumFilter = args[++i];
                        break;
                    case "--artist":
                        if (i + 1 >= args.length) {
                            System.err.println("Missing value for --artist");
                            return null;
                        }
                        artistFilter = args[++i];
                        break;
                    case "--title-prefix":
                        if (i + 1 >= args.length) {
                            System.err.println("Missing value for --title-prefix");
                            return null;
                        }
                        titlePrefix = args[++i];
                        break;
                    case "--sort":
                        if (i + 1 >= args.length) {
                            System.err.println("Missing value for --sort");
                            return null;
                        }
                        String value = args[++i];
                        if ("duration".equalsIgnoreCase(value)) {
                            sortByDuration = true;
                        } else {
                            System.err.println("Unknown sort mode: " + value);
                            return null;
                        }
                        break;
                    case "--recursive":
                        recursive = true;
                        break;
                    case "--name":
                        if (i + 1 >= args.length) {
                            System.err.println("Missing value for --name");
                            return null;
                        }
                        albumName = args[++i];
                        break;
                    default:
                        System.err.println("Unknown option: " + arg);
                        return null;
                }
            }
            return new CommandLineOptions(command, playlistDir, albumFilter, artistFilter, titlePrefix, sortByDuration, albumName, recursive);
        }
    }

    private static void runGui(PlaylistService service, Path playlistDir, CommandLineOptions options) {
        try {
            com.example.playlist.PlaylistGui.launch(service, playlistDir, options.recursive);
        } catch (Exception ex) {
            System.err.println("Failed to start GUI: " + ex.getMessage());
        }
    }
}
