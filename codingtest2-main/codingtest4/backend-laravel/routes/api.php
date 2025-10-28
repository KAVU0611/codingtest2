<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\PlaylistController;

Route::get('/songs', [PlaylistController::class, 'songs']);
Route::post('/albums', [PlaylistController::class, 'createAlbum']);

