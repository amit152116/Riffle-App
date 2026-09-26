| Feature                                | What it does                                                                   | Reuse                           |
| -------------------------------------- | ------------------------------------------------------------------------------ | ------------------------------- |
| **A. Duplicate detection**             | Same recording, transcodes, trims, variants                                    | Chromaprint                     |
| **B. Smart collections**               | Genre/mood/style/era/activity-based collections                                | Embeddings + metadata           |
| **C. Smart shuffle**                   | Next-track recommendations                                                     | Embeddings + history            |
| **D. Metadata cleanup**                | Detect/fix inconsistent artist, album, title, dates, etc.                      | MusicBrainz/AcoustID            |
| **E. Library health**                  | Find missing metadata, corrupt files, bad encodes, unusual bitrate/sample rate | ffmpeg + tags                   |
| **F. Audio quality analysis**          | Detect clipping, silence, low volume, suspicious transcodes                    | ffmpeg + DSP                    |
| **G. Version/edition browser**         | Group original, remaster, live, remix, radio edit, etc.                        | Fingerprints + MusicBrainz      |
| **H. Album completeness**              | Detect missing tracks from albums/releases                                     | MusicBrainz                     |
| **I. Similarity search**               | "Find songs that sound like this"                                              | Embeddings                      |
| **J. Natural-language library search** | "Find energetic Hindi songs from the 2010s"                                    | Metadata + embeddings           |
| **K. Listening analytics**             | Artists, genres, decades, listening time, repeats                              | Play history                    |
| **L. Playlist generation**             | Generate playlists based on mood/activity/constraints                          | C + B                           |
| **M. Library synchronization**         | Keep multiple music folders/devices synchronized                               | Library identity layer          |
| **N. Backup/change tracking**          | Detect moved/deleted/modified files                                            | Content identity                |
| **O. Artwork management**              | Find missing/wrong album artwork                                               | MusicBrainz + Cover Art Archive |
| **P. MusicBrainz identity graph**      | Artist → recording → release → release group                                   | MusicBrainz                     |
| **Q. Duplicate-aware statistics**      | Don't count 5 copies of the same recording as 5 songs                          | A + identity layer              |
