// Simple TSV → JSON bundler for playlist data
// Reads all .tsv files under ./playlist and writes ./public/songs.json
// Format per line: Title<TAB>Artist<TAB>mm:ss or hh:mm:ss

const fs = require('fs');
const path = require('path');

const ROOT = process.cwd();
const PLAYLIST_DIR = path.join(ROOT, 'playlist');
const OUT_DIR = path.join(ROOT, 'public');
const OUT_FILE = path.join(OUT_DIR, 'songs.json');

function walk(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(p));
    else if (entry.isFile() && entry.name.toLowerCase().endsWith('.tsv')) out.push(p);
  }
  return out.sort();
}

function parseDuration(text) {
  if (!text) return -1;
  const parts = text.split(':').map((s) => s.trim());
  try {
    if (parts.length === 2) {
      const m = parseInt(parts[0], 10);
      const s = parseInt(parts[1], 10);
      if (m < 0 || s < 0 || s >= 60 || Number.isNaN(m) || Number.isNaN(s)) return -1;
      return m * 60 + s;
    }
    if (parts.length === 3) {
      const h = parseInt(parts[0], 10);
      const m = parseInt(parts[1], 10);
      const s = parseInt(parts[2], 10);
      if (h < 0 || m < 0 || m >= 60 || s < 0 || s >= 60 || [h, m, s].some(Number.isNaN)) return -1;
      return h * 3600 + m * 60 + s;
    }
  } catch (_) {
    return -1;
  }
  return -1;
}

function stripExt(filename) {
  const i = filename.lastIndexOf('.');
  return i <= 0 ? filename : filename.substring(0, i);
}

function readAlbum(file) {
  const albumName = stripExt(path.basename(file));
  const content = fs.readFileSync(file, 'utf8');
  const lines = content.split(/\r?\n/);
  const songs = [];
  let track = 1;
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    const parts = line.split('\t');
    if (parts.length < 3) {
      // Skip invalid lines in static build but continue
      continue;
    }
    const title = (parts[0] || '').trim();
    const artist = (parts[1] || '').trim();
    const duration = (parts[2] || '').trim();
    const durationSeconds = parseDuration(duration);
    songs.push({ albumName, trackNumber: track, title, artist, duration, durationSeconds });
    track++;
  }
  return songs;
}

function main() {
  if (!fs.existsSync(PLAYLIST_DIR)) {
    console.error('playlist directory not found:', PLAYLIST_DIR);
    process.exit(1);
  }
  const files = walk(PLAYLIST_DIR);
  const all = [];
  for (const f of files) all.push(...readAlbum(f));
  if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(OUT_FILE, JSON.stringify(all), 'utf8');
  console.log(`Wrote ${all.length} songs to ${path.relative(ROOT, OUT_FILE)}`);
}

if (require.main === module) main();

