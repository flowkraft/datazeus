# Video — authored and rendered in cli-remotion (not here)

The Remotion composition for this lesson's video **no longer lives in datazeus**. The
single source of truth is the private `cli-remotion` project:

```
flowkraft/www/cli-remotion/src/videos/rb/learnsql-series1-00-write-your-first-query/index.tsx
```

It's registered in `cli-remotion/src/remotion-root.tsx` (composition
`learnsql-series1-00-write-your-first-query`) and in `VIDEO_REPOSITORY` in
`cli-remotion/src/cli.ts` (so TTS generation finds it). Render + YouTube/website
publishing all happen there.

datazeus owns the *learning* artifacts only — `lesson.mdx`, `scripts/*.sql`,
`cards/cards.yaml`, and the koans/tests. The rendered `.mp4` is embedded in the
lesson via `<VideoPlayer videoId="learnsql-series1-00-write-your-first-query" />`.

> History note: this composition used to be duplicated here and in cli-remotion, and
> the two drifted. They were reconciled (cli-remotion was the newer superset) and this
> copy was removed so there is exactly one source of truth.
