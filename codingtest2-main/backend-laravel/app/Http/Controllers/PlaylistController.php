<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Http\JsonResponse;
use App\Services\TsvPlaylistRepository;

class PlaylistController extends Controller
{
    private TsvPlaylistRepository $repo;

    public function __construct()
    {
        $this->repo = new TsvPlaylistRepository(config('playlist.path'));
    }

    public function songs(Request $request): JsonResponse
    {
        $filters = [
            'album' => $request->query('album'),
            'artist' => $request->query('artist'),
            'title_prefix' => $request->query('title_prefix'),
        ];
        $sort = $request->query('sort'); // 'duration' or default

        $songs = $this->repo->listSongs($filters, $sort);
        return response()->json($songs);
    }

    public function createAlbum(Request $request): JsonResponse
    {
        $data = $request->validate([
            'albumName' => 'required|string|max:255',
            'tracks' => 'required|array|min:1',
            'tracks.*.title' => 'required|string',
            'tracks.*.artist' => 'required|string',
            'tracks.*.duration' => ['required','string','regex:/^\d{1,2}:\d{2}$/'],
        ]);

        $path = $this->repo->createAlbum($data['albumName'], $data['tracks']);
        return response()->json(['message' => 'Album created', 'path' => $path], 201);
    }
}

