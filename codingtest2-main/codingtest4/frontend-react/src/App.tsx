import React, { useEffect, useMemo, useState } from 'react';
import { api, Song, CreateAlbumPayload } from './api';

type SortMode = 'album' | 'duration';

const App: React.FC = () => {
  const [songs, setSongs] = useState<Song[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [album, setAlbum] = useState('');
  const [artist, setArtist] = useState('');
  const [titlePrefix, setTitlePrefix] = useState('');
  const [sort, setSort] = useState<SortMode>('album');

  const queryParams = useMemo(() => {
    const p: Record<string, string> = {};
    if (album.trim()) p.album = album.trim();
    if (artist.trim()) p.artist = artist.trim();
    if (titlePrefix.trim()) p.title_prefix = titlePrefix.trim();
    if (sort === 'duration') p.sort = 'duration';
    return p;
  }, [album, artist, titlePrefix, sort]);

  const fetchSongs = async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get<Song[]>('/songs', { params: queryParams });
      setSongs(data);
    } catch (e: any) {
      setError(e?.message || 'Failed to fetch songs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSongs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(queryParams)]);

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', margin: '16px' }}>
      <h1>Playlist</h1>
      <section style={{ display: 'grid', gap: 8, gridTemplateColumns: 'repeat(5, minmax(0, 1fr))', alignItems: 'end' }}>
        <div>
          <label>Album</label>
          <input value={album} onChange={(e) => setAlbum(e.target.value)} placeholder="contains..." style={{ width: '100%' }} />
        </div>
        <div>
          <label>Artist</label>
          <input value={artist} onChange={(e) => setArtist(e.target.value)} placeholder="contains..." style={{ width: '100%' }} />
        </div>
        <div>
          <label>Title starts with</label>
          <input value={titlePrefix} onChange={(e) => setTitlePrefix(e.target.value)} placeholder="prefix" style={{ width: '100%' }} />
        </div>
        <div>
          <label>Sort</label>
          <select value={sort} onChange={(e) => setSort(e.target.value as SortMode)} style={{ width: '100%' }}>
            <option value="album">Album, Track</option>
            <option value="duration">Duration</option>
          </select>
        </div>
        <div>
          <button onClick={fetchSongs} disabled={loading} style={{ width: '100%' }}>{loading ? 'Loading...' : 'Refresh'}</button>
        </div>
      </section>

      {error && <p style={{ color: 'red' }}>{error}</p>}

      <section style={{ marginTop: 16 }}>
        <table style={{ borderCollapse: 'collapse', width: '100%' }}>
          <thead>
            <tr>
              <th style={th}>Album</th>
              <th style={th}>#</th>
              <th style={th}>Title</th>
              <th style={th}>Artist</th>
              <th style={th}>Duration</th>
            </tr>
          </thead>
          <tbody>
            {songs.map((s, i) => (
              <tr key={i}>
                <td style={td}>{s.album}</td>
                <td style={{ ...td, textAlign: 'right' }}>{s.trackNumber}</td>
                <td style={td}>{s.title}</td>
                <td style={td}>{s.artist}</td>
                <td style={td}>{s.duration}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <AddAlbum onCreated={fetchSongs} />
    </div>
  );
};

const th: React.CSSProperties = { borderBottom: '1px solid #ddd', textAlign: 'left', padding: '8px' };
const td: React.CSSProperties = { borderBottom: '1px solid #f0f0f0', padding: '8px' };

const AddAlbum: React.FC<{ onCreated: () => void }> = ({ onCreated }) => {
  const [albumName, setAlbumName] = useState('');
  const [tracks, setTracks] = useState<{ title: string; artist: string; duration: string }[]>([
    { title: '', artist: '', duration: '' },
  ]);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const addRow = () => setTracks((t) => [...t, { title: '', artist: '', duration: '' }]);
  const removeRow = (idx: number) => setTracks((t) => t.filter((_, i) => i !== idx));
  const update = (idx: number, key: 'title' | 'artist' | 'duration', value: string) =>
    setTracks((t) => t.map((row, i) => (i === idx ? { ...row, [key]: value } : row)));

  const submit = async () => {
    setSubmitting(true);
    setMessage(null);
    try {
      const cleaned = tracks.filter((t) => t.title && t.artist && t.duration);
      const payload: CreateAlbumPayload = { albumName: albumName.trim(), tracks: cleaned };
      await api.post('/albums', payload);
      setMessage('Album created');
      setAlbumName('');
      setTracks([{ title: '', artist: '', duration: '' }]);
      onCreated();
    } catch (e: any) {
      setMessage(e?.response?.data?.message || e?.message || 'Failed to create album');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section style={{ marginTop: 24 }}>
      <h2>New Album</h2>
      <div style={{ marginBottom: 8 }}>
        <label>Album name</label>
        <input value={albumName} onChange={(e) => setAlbumName(e.target.value)} style={{ marginLeft: 8, minWidth: 280 }} />
      </div>
      <table style={{ borderCollapse: 'collapse', width: '100%', marginBottom: 8 }}>
        <thead>
          <tr>
            <th style={th}>Title</th>
            <th style={th}>Artist</th>
            <th style={th}>Duration (mm:ss)</th>
            <th style={th}></th>
          </tr>
        </thead>
        <tbody>
          {tracks.map((row, idx) => (
            <tr key={idx}>
              <td style={td}><input value={row.title} onChange={(e) => update(idx, 'title', e.target.value)} style={{ width: '100%' }} /></td>
              <td style={td}><input value={row.artist} onChange={(e) => update(idx, 'artist', e.target.value)} style={{ width: '100%' }} /></td>
              <td style={td}><input value={row.duration} onChange={(e) => update(idx, 'duration', e.target.value)} placeholder="03:12" style={{ width: '100%' }} /></td>
              <td style={td}><button onClick={() => removeRow(idx)} disabled={tracks.length <= 1}>Remove</button></td>
            </tr>
          ))}
        </tbody>
      </table>
      <div style={{ display: 'flex', gap: 8 }}>
        <button onClick={addRow}>Add Track</button>
        <button onClick={submit} disabled={submitting || !albumName.trim()}>Save Album</button>
      </div>
      {message && <p style={{ marginTop: 8 }}>{message}</p>}
    </section>
  );
};

export default App;

