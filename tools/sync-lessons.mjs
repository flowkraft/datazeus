#!/usr/bin/env node
/**
 * sync-lessons.mjs — publish DataZeus lesson prose INTO the website (pull, don't copy).
 *
 * datazeus is the SOURCE OF TRUTH. The website (reportburster.com) is a read-only
 * consumer: for each lesson bundle here, copy its `lesson.mdx` to the website's
 * Contentlayer content tree, renaming to the `NN-slug.mdx` the site expects.
 *
 *   datazeus/courses/master-<topic>/<module>/NN-slug/lesson.mdx
 *        →  reportburster.com/content/datazeus-academy/master-<topic>/NN-slug.mdx
 *
 * Contentlayer's `Lesson` type keys off the front-matter (topic/module/order/…) and the
 * file path, so the `<module>/` segment is intentionally dropped from the website path
 * (the site groups by the `module` field, not the folder).
 *
 * Usage:
 *   node tools/sync-lessons.mjs               # sync all lessons
 *   node tools/sync-lessons.mjs --dry-run     # show what would be written
 *   WEBSITE_CONTENT=/abs/path node tools/sync-lessons.mjs   # override target
 *
 * NOTE: lesson.mdx already contains <VideoPlayer videoId="..." />; the mp4 itself is
 * NOT synced here — it's rendered separately in cli-remotion and served from the
 * website's /public/videos (or a CDN). This script moves PROSE only.
 */
import { readdirSync, readFileSync, writeFileSync, mkdirSync, existsSync, statSync } from "node:fs";
import { join, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, "..");
const DRY = process.argv.includes("--dry-run");

// Default target: the sibling website repo. Override with WEBSITE_CONTENT.
const WEBSITE_CONTENT = process.env.WEBSITE_CONTENT
  ? resolve(process.env.WEBSITE_CONTENT)
  : resolve(REPO, "../flowkraft/www/reportburster.com/content/datazeus-academy");

const isDir = (p) => existsSync(p) && statSync(p).isDirectory();
const COURSES = join(REPO, "courses");
const trackDirs = isDir(COURSES)
  ? readdirSync(COURSES).filter((d) => d.startsWith("master-") && isDir(join(COURSES, d)))
  : [];

let count = 0;
for (const track of trackDirs) {
  const trackPath = join(COURSES, track);
  for (const moduleDir of readdirSync(trackPath).filter((d) => isDir(join(trackPath, d)))) {
    const modPath = join(trackPath, moduleDir);
    for (const lessonDir of readdirSync(modPath).filter((d) => isDir(join(modPath, d)))) {
      const src = join(modPath, lessonDir, "lesson.mdx");
      if (!existsSync(src)) continue;
      const destDir = join(WEBSITE_CONTENT, track);          // e.g. .../datazeus-academy/master-sql
      const dest = join(destDir, `${lessonDir}.mdx`);        // e.g. 00-write-your-first-query.mdx
      const body = readFileSync(src, "utf8");
      console.log(`${DRY ? "[dry] " : ""}${track}/${moduleDir}/${lessonDir}/lesson.mdx  →  ${track}/${lessonDir}.mdx`);
      if (!DRY) {
        mkdirSync(destDir, { recursive: true });
        writeFileSync(dest, body, "utf8");
      }
      count++;
    }
  }
}
console.log(`${DRY ? "Would sync" : "Synced"} ${count} lesson(s) → ${WEBSITE_CONTENT}`);
