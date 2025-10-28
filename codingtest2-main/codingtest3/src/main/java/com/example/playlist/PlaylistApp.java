package com.example.playlist;

import com.example.playlist.model.SongEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class PlaylistApp {

    public static void main(String[] args) {
        PlaylistService service = new PlaylistService();
        CommandLineOptions options = CommandLineOptions.parse(args);
        if (options == null) { printUsage(); return; }
        Path playlistDir = service.resolvePlaylistDir(options.playlistDir);
        switch (options.command) {
            case LIST: runList(service, playlistDir, options); break;
            case ADD_ALBUM: runAddAlbum(service, playlistDir, options); break;
            case HELP: default: printUsage(); break;
        }
    }

    private static void runList(PlaylistService service, Path playlistDir, CommandLineOptions opt) {
        try {
            List<SongEntry> songs = service.loadSongs(playlistDir, opt.recursive);
            songs = service.filterSongs(songs, opt.albumFilter, opt.artistFilter, opt.titlePrefix);
            songs = service.sortSongs(songs, opt.sortByDuration);
            printSongs(songs);
        } catch (IOException ex) {
            System.err.println("Failed to load playlist: " + ex.getMessage());
        }
    }

    private static void printSongs(List<SongEntry> songs) {
        if (songs.isEmpty()) { System.out.println("No songs found."); return; }
        String header = String.format("%-35s | %-5s | %-35s | %-25s | %-8s", "Album", "#", "Title", "Artist", "Duration");
        System.out.println(header);
        System.out.println("-".repeat(header.length()));
        for (SongEntry s : songs) {
            System.out.printf("%-35s | %-5d | %-35s | %-25s | %-8s%n",
                    s.getAlbumName(), s.getTrackNumber(), s.getTitle(), s.getArtist(), s.getDuration());
        }
        System.out.println();
        System.out.println("Total songs: " + songs.size());
    }

    private static void runAddAlbum(PlaylistService service, Path playlistDir, CommandLineOptions opt) {
        String albumName = opt.albumName;
        if (albumName == null || albumName.isBlank()) { System.err.println("Album name is required for add-album."); return; }
        List<String> lines = promptForSongs(service);
        if (lines.isEmpty()) { System.err.println("Album must contain at least one song."); return; }
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
        Scanner sc = new Scanner(System.in);
        List<String> lines = new ArrayList<>();
        int track = 1;
        while (true) {
            System.out.print("Title for track " + track + " (blank to finish): ");
            String title = sc.nextLine().trim();
            if (title.isEmpty()) break;
            System.out.print("Artist: ");
            String artist = sc.nextLine().trim();
            String duration = promptForDuration(sc, service);
            lines.add(String.join("\t", title, artist, duration));
            track++;
        }
        return lines;
    }

    private static String promptForDuration(Scanner sc, PlaylistService service) {
        while (true) {
            System.out.print("Duration (mm:ss or hh:mm:ss): ");
            String d = sc.nextLine().trim();
            if (service.isParsableDuration(d)) return d;
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
        System.out.println("  add-album            Create a new album interactively\n" +
                "                         --name <album name> album title\n" +
                "                         --playlist <dir> playlist directory (default: ./playlist)\n" +
                "                         durations must be provided as mm:ss or hh:mm:ss");
        System.out.println("  help                 Show this message");
    }

    private enum Command { LIST, ADD_ALBUM, HELP }

    private static class CommandLineOptions {
        final Command command;
        final String playlistDir;
        final String albumFilter;
        final String artistFilter;
        final String titlePrefix;
        final boolean sortByDuration;
        final String albumName;
        final boolean recursive;

        private CommandLineOptions(Command c, String p, String a, String r, String t, boolean s, String n, boolean rec) {
            this.command=c; this.playlistDir=p; this.albumFilter=a; this.artistFilter=r; this.titlePrefix=t; this.sortByDuration=s; this.albumName=n; this.recursive=rec;
        }

        static CommandLineOptions parse(String[] args) {
            if (args.length == 0) return new CommandLineOptions(Command.HELP, null, null, null, null, false, null, false);
            Command cmd;
            switch (args[0]) {
                case "list": cmd = Command.LIST; break;
                case "add-album": cmd = Command.ADD_ALBUM; break;
                case "help": cmd = Command.HELP; break;
                default: return null;
            }
            String playlistDir=null, albumFilter=null, artistFilter=null, titlePrefix=null, albumName=null; boolean sort=false, rec=false;
            for (int i=1;i<args.length;i++) {
                String arg=args[i];
                switch (arg) {
                    case "--playlist": if (++i>=args.length) return null; playlistDir=args[i]; break;
                    case "--album": if (++i>=args.length) return null; albumFilter=args[i]; break;
                    case "--artist": if (++i>=args.length) return null; artistFilter=args[i]; break;
                    case "--title-prefix": if (++i>=args.length) return null; titlePrefix=args[i]; break;
                    case "--sort": if (++i>=args.length) return null; sort = "duration".equalsIgnoreCase(args[i]); break;
                    case "--recursive": rec = true; break;
                    case "--name": if (++i>=args.length) return null; albumName=args[i]; break;
                    default: return null;
                }
            }
            return new CommandLineOptions(cmd, playlistDir, albumFilter, artistFilter, titlePrefix, sort, albumName, rec);
        }
    }
}
