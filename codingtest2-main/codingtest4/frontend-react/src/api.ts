import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE || 'http://localhost:8000/api';

export const api = axios.create({ baseURL });

export interface Song {
  album: string;
  trackNumber: number;
  title: string;
  artist: string;
  duration: string;
  durationSeconds: number;
}

export interface CreateAlbumPayload {
  albumName: string;
  tracks: { title: string; artist: string; duration: string }[];
}

