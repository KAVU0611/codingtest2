package com.example.playlist;

import com.example.playlist.model.SongEntry;
import com.sun.net.httpserver.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class WebServer {
    public static void main(String[] args) throws Exception {
        int port = getPort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        PlaylistService service = new PlaylistService();
        server.createContext("/", new RootHandler());
        server.createContext("/api/songs", new SongsHandler(service));
        server.setExecutor(null);
        System.out.println("Listening on port " + port);
        server.start();
    }

    private static int getPort() {
        String s = System.getenv("PORT");
        if (s == null || s.isBlank()) return 8080;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 8080; }
    }

    private static class RootHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex,405,"Method Not Allowed","text/plain; charset=utf-8"); return; }
            send(ex,200, INDEX_HTML, "text/html; charset=utf-8");
        }
    }

    private static class SongsHandler implements HttpHandler {
        private final PlaylistService service; SongsHandler(PlaylistService s){this.service=s;}
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex,405,jsonError("Method Not Allowed"),"application/json; charset=utf-8"); return; }
            Map<String,String> q = parseQuery(ex.getRequestURI());
            Path dir = service.resolvePlaylistDir(q.get("playlist"));
            boolean recursive = parseBool(q.get("recursive"));
            boolean sortDuration = "duration".equalsIgnoreCase(nullToEmpty(q.get("sort")));
            try {
                List<SongEntry> songs = service.loadSongs(dir, recursive);
                songs = service.filterSongs(songs, q.get("album"), q.get("artist"), q.get("titlePrefix"));
                songs = service.sortSongs(songs, sortDuration);
                send(ex,200,toJson(songs),"application/json; charset=utf-8");
            } catch (IOException ex1) {
                send(ex,500,jsonError(ex1.getMessage()),"application/json; charset=utf-8");
            }
        }
    }

    private static Map<String,String> parseQuery(URI uri){ Map<String,String> m=new LinkedHashMap<>(); String raw=uri.getRawQuery(); if(raw==null||raw.isEmpty()) return m; for(String pair: raw.split("&")){int i=pair.indexOf('='); String k=i>=0?pair.substring(0,i):pair; String v=i>=0?pair.substring(i+1):""; m.put(urlDecode(k), urlDecode(v));} return m; }
    private static String urlDecode(String s){ return URLDecoder.decode(s, StandardCharsets.UTF_8); }
    private static boolean parseBool(String s){ if(s==null) return false; switch(s.toLowerCase()){case "1":case "true":case "yes":case "on":return true; default:return false;} }
    private static String nullToEmpty(String s){ return s==null?"":s; }

    private static void send(HttpExchange ex, int status, String body, String type) throws IOException {
        Headers h = ex.getResponseHeaders(); h.set("Content-Type", type); byte[] bytes = body.getBytes(StandardCharsets.UTF_8); ex.sendResponseHeaders(status, bytes.length); try(OutputStream os=ex.getResponseBody()){ os.write(bytes);} }
    private static String jsonError(String m){ String msg = m==null?"":m; return "{\"error\":\""+jsonEscape(msg)+"\"}"; }
    private static String jsonEscape(String s){ StringBuilder sb=new StringBuilder(s.length()+16); for(int i=0;i<s.length();i++){ char c=s.charAt(i); switch(c){ case '"':sb.append("\\\"");break; case '\\':sb.append("\\\\");break; case '\b':sb.append("\\b");break; case '\f':sb.append("\\f");break; case '\n':sb.append("\\n");break; case '\r':sb.append("\\r");break; case '\t':sb.append("\\t");break; default: if(c<0x20) sb.append(String.format("\\u%04x",(int)c)); else sb.append(c);} } return sb.toString(); }
    private static String toJson(List<SongEntry> xs){ StringBuilder sb=new StringBuilder(); sb.append('['); for(int i=0;i<xs.size();i++){ SongEntry s=xs.get(i); if(i>0) sb.append(','); sb.append('{')
                .append("\"albumName\":\"").append(jsonEscape(s.getAlbumName())).append('\"')
                .append(',').append("\"trackNumber\":").append(s.getTrackNumber())
                .append(',').append("\"title\":\"").append(jsonEscape(s.getTitle())).append('\"')
                .append(',').append("\"artist\":\"").append(jsonEscape(s.getArtist())).append('\"')
                .append(',').append("\"duration\":\"").append(jsonEscape(s.getDuration())).append('\"')
                .append('}'); }
            sb.append(']'); return sb.toString(); }

    private static final String INDEX_HTML = "" +
            "<!doctype html>\n" +
            "<html lang=\"ja\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<title>Playlist</title>" +
            "<style>body{font-family:sans-serif;margin:20px} table{border-collapse:collapse;width:100%} th,td{border:1px solid #ddd;padding:6px} th{background:#f5f5f5;text-align:left} input[type=text]{width:180px} .row{margin:6px 0}</style>" +
            "</head><body>" +
            "<h2>Playlist</h2>" +
            "<div class=\"row\">" +
            "<label>Playlist dir: <input id=\"playlist\" type=\"text\" value=\"playlist\"></label>\n" +
            "<label><input id=\"recursive\" type=\"checkbox\"> Recursive</label>\n" +
            "<button id=\"reload\">Reload</button>\n" +
            "</div>" +
            "<div class=\"row\">" +
            "<label>Album: <input id=\"album\" type=\"text\"></label>\n" +
            "<label>Artist: <input id=\"artist\" type=\"text\"></label>\n" +
            "<label>Title prefix: <input id=\"titlePrefix\" type=\"text\"></label>\n" +
            "<label><input id=\"sortDuration\" type=\"checkbox\"> Sort by duration</label>\n" +
            "</div>" +
            "<div class=\"row\" id=\"status\"></div>" +
            "<table><thead><tr><th>Album</th><th>#</th><th>Title</th><th>Artist</th><th>Duration</th></tr></thead><tbody id=\"tbody\"></tbody></table>" +
            "<script>\n" +
            "async function load(){\n" +
            "  const q = new URLSearchParams();\n" +
            "  const playlist = document.getElementById('playlist').value; if(playlist) q.set('playlist', playlist);\n" +
            "  if(document.getElementById('recursive').checked) q.set('recursive','true');\n" +
            "  const album=document.getElementById('album').value; if(album) q.set('album', album);\n" +
            "  const artist=document.getElementById('artist').value; if(artist) q.set('artist', artist);\n" +
            "  const titlePrefix=document.getElementById('titlePrefix').value; if(titlePrefix) q.set('titlePrefix', titlePrefix);\n" +
            "  if(document.getElementById('sortDuration').checked) q.set('sort','duration');\n" +
            "  const res = await fetch('/api/songs?'+q.toString());\n" +
            "  const status = document.getElementById('status');\n" +
            "  if(!res.ok){ const t=await res.text(); status.textContent='Error: '+t; return; }\n" +
            "  const data = await res.json();\n" +
            "  status.textContent = 'Total songs: '+data.length;\n" +
            "  const tbody = document.getElementById('tbody'); tbody.innerHTML='';\n" +
            "  for(const s of data){ const tr=document.createElement('tr'); tr.innerHTML = `<td>${s.albumName}</td><td>${s.trackNumber}</td><td>${s.title}</td><td>${s.artist}</td><td>${s.duration}</td>`; tbody.appendChild(tr);}\n" +
            "}\n" +
            "document.getElementById('reload').addEventListener('click', load);\n" +
            "load();\n" +
            "</script>" +
            "</body></html>";
}
