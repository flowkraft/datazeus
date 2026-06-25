# Video — authored here, rendered in cli-remotion

`index.tsx` is a **Remotion composition authored in DataZeus** (source of truth, next to
the lesson's mdx/scripts/cards) but **rendered inside `cli-remotion`** (it imports
cli-remotion's shared utils via `../../utils/…`, so it is drop-in once placed in
cli-remotion's video tree). The rendered `.mp4` is **not** committed — it's produced on
demand and served from the website's `/public/videos` (or a CDN); the blog embeds it with
`<VideoPlayer videoId="master-sql-series1-00-write-your-first-query" />`.

Slug / composition id: **`master-sql-series1-00-write-your-first-query`**.

## Status: already wired in cli-remotion

This composition is registered and previewable today:

```bash
cd ../flowkraft/www/cli-remotion
set REMOTION_RB_ONLY=master-sql-series1-00 && npm start
```

Touchpoints in cli-remotion (mirroring how `0040` is wired):
- `src/videos/rb/master-sql-series1-00-write-your-first-query/index.tsx` — this file, copied in.
- `src/remotion-root.tsx` — `MasterSqlS1E00Composition` registers `FirstQueryVideo` directly;
  duration `FIRST_QUERY_TOTAL_FRAMES` (the Mnemosyne ancient-Greece intro — "Learn SQL ... Become
  a Data God!" — is embedded inside the video, so no wrapper). 1920×1080. The id auto-resolves to
  `master-sql-series1-00-write-your-first-query-Aoede-Sadaltager-<bg>` from the per-scene voiceIds.
- `src/cli.ts` — `videoModelMasterSqlS1E00` is in `VIDEO_REPOSITORY` (so TTS generation finds it).

## Re-syncing after editing here

DataZeus is the source of truth, so after changing this file, copy it back into cli-remotion:

```bash
cp index.tsx \
   ../../../flowkraft/www/cli-remotion/src/videos/rb/master-sql-series1-00-write-your-first-query/index.tsx
```
(Later this becomes a small `sync:videos` script keyed off the slug, like `sync-lessons.mjs`.)

## Generate TTS, then render (mixed voices: Mnemosyne→Aoede, Leo→Sadaltager)

```bash
# flip TTS_AUDIO_ENABLED = true in index.tsx first
npx nps custom.subtitles.generate:mastersql-s1e00:ai-tts
npx nps custom.remotion.render:mastersql-s1e00
# → out/master-sql-series1-00-write-your-first-query.mp4
#   → publish to website /public/videos/<slug>/<slug>.mp4
```

## Preview without audio

`TTS_AUDIO_ENABLED = false` (default) → renders immediately with captions + animation and no
audio dependency. Flip to `true` only after the per-scene TTS mp3s exist.

## Notes

- House style from `0040-sql-joins-doodle`: parchment palette, ink captions, hand-drawn
  head-in-circle avatars (Mnemosyne auburn + diadem, Leo curls), self-drawing headline underline.
- Adds a **typewriter** reveal on the hero query (the "you write it" beat) and a popped-in
  **result** card (the big `4`).
- The SQL shown on the cards mirrors the lesson's `../scripts/*.sql` (`june-orders`, `customers`)
  and the koans — keep them in sync (a future build step should read `scripts/` directly).
