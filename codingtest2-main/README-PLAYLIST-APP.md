# Playlist Application (Laravel backend + React TS frontend)

This repo contains scaffolding to implement the requested playlist app that reads/writes albums stored as TSV files inside a playlist folder.

What’s included:

- `backend-laravel/`: Laravel-style files (routes, controller, service, config)
- `frontend-react/`: Vite + React + TypeScript UI

You can drop these into a fresh Laravel project and run the React app locally, or move the folders as-is and wire them up.

## Data format (TSV)

- Folder = playlist (contains `.tsv` files)
- File name = album name (e.g., `BestHits.tsv`)
- File body = lines of `title<TAB>artist<TAB>mm:ss`
- Line number = track order (1-based)

Example (see `codingtest2-main/playlist/Sample.tsv` in this repo):

```
Track One\tAlpha Artist\t03:12
Track Two\tBeta Artist\t04:05
```

## Backend (Laravel)

Files provided under `backend-laravel/`:

- `config/playlist.php`: reads `PLAYLIST_PATH` env or defaults to `base_path('playlist')`
- `routes/api.php`: routes
  - `GET /api/songs` with filters `album`, `artist`, `title_prefix`, optional `sort=duration`
  - `POST /api/albums` creates a new album file from JSON payload
- `app/Services/TsvPlaylistRepository.php`: TSV reader/writer
- `app/Http/Controllers/PlaylistController.php`: request handling + validation

Setup steps (in a Laravel app):

1. Create a fresh Laravel project (PHP 8.2+):
   - `composer create-project laravel/laravel playlist-backend`
2. Copy the files from `backend-laravel/` into the matching paths of your Laravel app.
3. Set the playlist folder path in `.env`:
   - `PLAYLIST_PATH="C:\\Users\\<you>\\Downloads\\codingtest\\codingtest\\playlist"`
4. Run API: `php artisan serve` (defaults to `http://127.0.0.1:8000`)

API usage examples:

- List songs: `GET /api/songs?album=Best&artist=Queen&title_prefix=Bo&sort=duration`
- Create album:
  ```json
  POST /api/albums
  {
    "albumName": "New Album",
    "tracks": [
      { "title": "Track A", "artist": "Artist 1", "duration": "03:12" },
      { "title": "Track B", "artist": "Artist 2", "duration": "04:05" }
    ]
  }
  ```

Notes:

- Default sort is album name ASC, then track number ASC.
- `sort=duration` sorts by song duration ascending.
- Album name is sanitized for file safety; existing `.tsv` with same name is rejected.

## Frontend (React + Vite)

Provided under `frontend-react/`:

- `src/App.tsx`: UI for filters, table, and album creation
- `src/api.ts`: Axios client, types, base URL via `VITE_API_BASE`
- `index.html`, `src/main.tsx`, `vite.config.ts`, `tsconfig.json`

Setup:

1. `cd frontend-react`
2. `npm install`
3. (optional) create `.env` with `VITE_API_BASE=http://127.0.0.1:8000/api`
4. `npm run dev` → `http://localhost:5173`

Ensure CORS allows the frontend origin. In a standard Laravel 10/11 app, `config/cors.php` is present; set:

- `paths` includes `api/*`
- `allowed_origins` includes `http://localhost:5173`

## Adapting to your data folder

If your playlist exists at:

`C:\\Users\\kavu1\\Downloads\\codingtest\\codingtest\\playlist`

set this in the Laravel `.env` as `PLAYLIST_PATH` so the backend reads/writes the same folder.

## Validation and assumptions

- Duration format is `mm:ss` (e.g., `04:05`)
- Filters are case-insensitive; title filter uses "starts with"
- Empty lines in TSV are ignored
- The app never mutates existing TSV order; track number is line index (1-based)

