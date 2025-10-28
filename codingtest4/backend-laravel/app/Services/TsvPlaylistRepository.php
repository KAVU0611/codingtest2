<?php

namespace App\Services;

use DirectoryIterator;

class TsvPlaylistRepository
{
    private string $playlistPath;

    public function __construct(string $playlistPath)
    {
        $this->playlistPath = rtrim($playlistPath, DIRECTORY_SEPARATOR);
    }

    public function listSongs(array $filters = [], ?string $sort = null): array
    {
        $albums = $this->listAlbumFiles();
        $songs = [];

        foreach ($albums as $albumFile => $albumName) {
            $albumSongs = $this->readAlbum($albumFile, $albumName);
            $songs = array_merge($songs, $albumSongs);
        }

        $songs = $this->applyFilters($songs, $filters);

        if ($sort === 'duration') {
            usort($songs, function ($a, $b) {
                return $a['durationSeconds'] <=> $b['durationSeconds'];
            });
        } else {
            usort($songs, function ($a, $b) {
                if ($a['album'] === $b['album']) {
                    return $a['trackNumber'] <=> $b['trackNumber'];
                }
                return strcasecmp($a['album'], $b['album']);
            });
        }

        return $songs;
    }

    public function createAlbum(string $albumName, array $tracks): string
    {
        $safeName = $this->sanitizeAlbumName($albumName);
        if ($safeName === '') {
            throw new \InvalidArgumentException('Invalid album name.');
        }
        $filePath = $this->playlistPath . DIRECTORY_SEPARATOR . $safeName . '.tsv';
        if (file_exists($filePath)) {
            throw new \RuntimeException('Album already exists.');
        }

        $lines = [];
        foreach ($tracks as $track) {
            $title = $this->requireString($track, 'title');
            $artist = $this->requireString($track, 'artist');
            $duration = $this->requireString($track, 'duration');
            if (!$this->isValidDuration($duration)) {
                throw new \InvalidArgumentException('Invalid duration format (mm:ss) for track: ' . $title);
            }
            $lines[] = implode("\t", [$title, $artist, $duration]);
        }

        if (!is_dir($this->playlistPath)) {
            if (!mkdir($this->playlistPath, 0777, true) && !is_dir($this->playlistPath)) {
                throw new \RuntimeException('Failed to create playlist directory.');
            }
        }

        $data = implode(PHP_EOL, $lines) . PHP_EOL;
        if (file_put_contents($filePath, $data) === false) {
            throw new \RuntimeException('Failed to write album file.');
        }
        return $filePath;
    }

    private function listAlbumFiles(): array
    {
        $result = [];
        if (!is_dir($this->playlistPath)) {
            return $result;
        }
        foreach (new DirectoryIterator($this->playlistPath) as $fileInfo) {
            if ($fileInfo->isDot() || !$fileInfo->isFile()) {
                continue;
            }
            if (strtolower($fileInfo->getExtension()) === 'tsv') {
                $filename = $fileInfo->getFilename();
                $album = substr($filename, 0, -4); // strip .tsv
                $result[$fileInfo->getPathname()] = $album;
            }
        }
        return $result;
    }

    private function readAlbum(string $filePath, string $albumName): array
    {
        $lines = @file($filePath, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        if ($lines === false) {
            return [];
        }
        $songs = [];
        $track = 1;
        foreach ($lines as $line) {
            $parts = explode("\t", $line);
            if (count($parts) < 3) {
                continue;
            }
            [$title, $artist, $duration] = $parts;
            $songs[] = [
                'album' => $albumName,
                'trackNumber' => $track++,
                'title' => trim($title),
                'artist' => trim($artist),
                'duration' => trim($duration),
                'durationSeconds' => $this->durationToSeconds(trim($duration)),
            ];
        }
        return $songs;
    }

    private function applyFilters(array $songs, array $filters): array
    {
        $albumFilter = isset($filters['album']) ? mb_strtolower(trim((string)$filters['album'])) : null;
        $artistFilter = isset($filters['artist']) ? mb_strtolower(trim((string)$filters['artist'])) : null;
        $titlePrefix = isset($filters['title_prefix']) ? mb_strtolower(trim((string)$filters['title_prefix'])) : null;

        return array_values(array_filter($songs, function ($s) use ($albumFilter, $artistFilter, $titlePrefix) {
            if ($albumFilter && mb_strpos(mb_strtolower($s['album']), $albumFilter) === false) {
                return false;
            }
            if ($artistFilter && mb_strpos(mb_strtolower($s['artist']), $artistFilter) === false) {
                return false;
            }
            if ($titlePrefix && mb_strpos(mb_strtolower($s['title']), $titlePrefix) !== 0) {
                return false;
            }
            return true;
        }));
    }

    private function sanitizeAlbumName(string $name): string
    {
        $name = trim($name);
        // allow letters, numbers, spaces, dash, underscore, and full-width Japanese common chars
        // Replace disallowed characters with underscore
        $name = preg_replace('/[^\p{L}\p{N}\s\-_]/u', '_', $name);
        $name = preg_replace('/\s+/', ' ', $name);
        return trim($name);
    }

    private function durationToSeconds(string $duration): int
    {
        if (!$this->isValidDuration($duration)) {
            return 0;
        }
        [$m, $s] = array_map('intval', explode(':', $duration));
        return $m * 60 + $s;
    }

    private function isValidDuration(string $duration): bool
    {
        return preg_match('/^\d{1,2}:\d{2}$/', $duration) === 1;
    }

    private function requireString(array $data, string $key): string
    {
        if (!isset($data[$key]) || !is_string($data[$key]) || trim($data[$key]) === '') {
            throw new \InvalidArgumentException("Missing or invalid field: {$key}");
        }
        return trim($data[$key]);
    }
}

