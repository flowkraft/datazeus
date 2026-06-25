import {
  Sequence,
  Audio,
  AbsoluteFill,
  staticFile,
  interpolate,
  useCurrentFrame,
} from "remotion";
import React from "react";
import { SceneModel, VideoModel } from "../../utils/video-models";
import {
  getAssetOutputFileName,
  getVoiceInfoDetails,
  useVideoDuration,
} from "../../utils/utilities-browser";
import { VIDEO_CONFIG } from "../../utils/video-config";
import { AncientGreeceIntro, ANCIENT_GREECE_FRAMES } from "../../_reusables/AncientGreeceIntro";
import { YouTubeOutro, YOUTUBE_FRAMES } from "../../_reusables/YouTubeOutro";
import { CharacterHead } from "../../_reusables/CharacterHead";
import { WriteOn } from "../../_reusables/WriteOn";

/* ============================================================
   mastersql-series1-00-write-your-first-query
   "Stop Watching SQL Tutorials — Write Your First Query in 60 Seconds"
   DataZeus · Master SQL · Series 1 (Fundamentals) · Episode 00.

   THE PHILOSOPHY this episode sets up: one real practice session beats watching
   twenty videos — you learn SQL by WRITING queries yourself, on real, production-
   like data. Lessons give the minimum theory, then you practise via a thought-
   process loop: ask in plain words -> write -> predict -> run -> compare -> repeat.
   We write standard ANSI SQL so it transfers to any enterprise DB. You practise on
   two engines with the SAME Northwind data — the DuckDB CLI for convenience, and
   PostgreSQL + CloudBeaver when you need more — and it ends by installing the
   DuckDB CLI. The worked example is "orders shipped in June" (= 4).

   House style = the 0040 / boilerplate parchment doodle: Mnemosyne (teacher, voice
   Aoede) + Leo (learner, voice Sadaltager) as hand-drawn head-in-circle avatars,
   ink captions, a "code card", and a popped-in result. Intro = Mnemosyne ancient-
   Greece ("Learn SQL ... Become a Data God!"); outro = Subscribe (DataPallas YouTube).

   DATA NOTE (publish-gate): "June = 4 orders" must match the verified Spock spec /
   the generated Northwind before render.
   ============================================================ */

const INK = "#264653";
const INK_SOFT = "#8b7355";
const ACCENT = "#b66a4a";
const PAPER = "#fdf6e3";
const CARD = "#fffdf6";
const KW = "#2f6db0";
const STR = "#3f7a4f";
const HAND = "'Segoe Print', 'Bradley Hand', 'Comic Sans MS', cursive";
const MONO = "'Cascadia Code', 'Consolas', 'SF Mono', monospace";

const TTS_AUDIO_ENABLED = true; // TTS generated (ElevenLabs v3, Liam+Bella) — scenes audio-fit to narration
const LEO_VOICE_ID = "Liam";       // ElevenLabs (was Gemini "Sadaltager")
const MNEMOSYNE_VOICE_ID = "Bella"; // ElevenLabs (was Gemini "Aoede")
const NARRATOR_VOICE_ID = MNEMOSYNE_VOICE_ID;

type Speaker = "leo" | "mnemosyne";

const FPS = VIDEO_CONFIG.render.defaultFramerate;
export const OUTRO_FRAMES = YOUTUBE_FRAMES; // Subscribe Outro (DataPallas YouTube) — 9s

const SUMMARIZED_CONTEXT =
  "A bright, high-energy first SQL lesson: Mnemosyne — goddess of memory and learning — and Leo, an eager young mortal, spark off each other as she teaches him to learn SQL by WRITING real queries on real Northwind data, not by watching videos. She shows him the thinking 'loop', he runs his first query in CloudBeaver on PostgreSQL (answer: 4 orders in June), then she introduces koans on DuckDB and an optional DuckDB CLI. The tone is upbeat, warm and motivating throughout — two enthusiastic voices feeding each other's energy, never flat or solemn.";

interface ResultSet {
  columns: string[];
  rows: (string | number)[][];
}

interface Scene {
  id: string;
  text?: string;
  headline?: string;
  mnemosyne?: string; // expression: talking | pointing-right | smiling | proud | excited | questioning | surprised | laughing | shrugging
  leo?: string; // expression: questioning | surprised | excited | confused | thinking | smiling | proud | talking
  speaker?: Speaker;
  sql?: string[];
  typed?: boolean; // type the SQL in key-by-key (the "you write it" beat)
  runLabel?: string; // the code-card affordance, e.g. "▶ run in the DuckDB CLI"
  result?: ResultSet; // popped-in query result
  thought?: string; // the LISTENER's inner monologue — one line, visual only (never spoken)
  thoughtLines?: string[]; // a MULTI-line inner monologue — each line writes in, staggered
  reasoning?: string[]; // static derivation lines shown ABOVE the SQL card (plain, no handwriting effect)
  sticker?: string; // a slapped-on, rotated badge that pops in (playful call-to-action)
  stamp?: string; // a rubber-stamp "slammed" across the middle of the slide (e.g. PRACTICE IS KING)
  steps?: string[]; // a numbered list rendered as the slide's central content (e.g. the loop)
  cta?: string[]; // a no-dialogue "your turn" call-to-action slide: no avatars, no caption, no TTS
  ctaSql?: string[]; // a code card shown between the first cta line and the rest (the query to type)
  exchange?: { who: Speaker; line: string }[]; // a short two-turn back-and-forth shown at the bottom
  units?: number;
  seconds?: number; // FIXED scene length in seconds (overrides word-count pacing) — e.g. a timed terminal
  terminalSteps?: { label?: string; lines: string[] }[]; // a terminal that CYCLES these screens, 5s each
  editor?: boolean; // render the Notepad++-style koan-file mock (file tree + code) as the slide's content
  koansRun?: boolean; // render the "path to enlightenment" koans run-output card (red→green)
  centerLines?: string[]; // a big centred statement in the middle; a line that is just ">" renders as the accent pivot
}

const SCENES: Scene[] = [
  {
    id: "warm-up",
    headline: "READY, LEO?",
    thought: "Just what I need — to practise SQL myself.",
    exchange: [
      { who: "mnemosyne", line: "Hi Leo! Ready to actually **write** SQL — not just watch slides about it? Here's how we work here: just a bit of theory, then a lot of doing. You'll be querying real data in minutes." },
      { who: "leo", line: "Yes, Mnemosyne — I'm ready. Let's go!" },
    ],
    speaker: "mnemosyne", // narration voice = Mnemosyne (Bella); Leo's reply is the on-screen exchange
    mnemosyne: "smiling",
    leo: "excited",
  },
  {
    id: "watch-trap",
    headline: "DO YOURSELF",
    stamp: "Practice is king",
    exchange: [
      { who: "mnemosyne", line: "Leo — one real practice session beats watching **twenty** polished videos. Honestly? You'll watch all twenty… and still freeze the moment you have to write a query yourself." },
      { who: "leo", line: "…Ouch. That's literally been me." },
    ],
    speaker: "mnemosyne", // Mnemosyne voiced (Bella); Leo's reply is the on-screen exchange
    mnemosyne: "talking",
    leo: "thinking",
  },
  {
    id: "our-deal",
    headline: "OUR DEAL",
    stamp: "Practice is king",
    exchange: [
      { who: "mnemosyne", line: "So here's our deal, Leo: you don't watch me — **you** come up with the queries and write them yourself, on real, production-like data." },
      { who: "leo", line: "Deal — I want to actually write SQL, not just watch." },
    ],
    speaker: "mnemosyne", // Mnemosyne voiced (Bella); Leo's reply is the on-screen exchange
    mnemosyne: "pointing-right",
    leo: "thinking",
  },
  {
    id: "just-enough",
    headline: "JUST ENOUGH THEORY",
    text: "Every lesson opens with the **least** theory possible — just enough for you to write something. You practise. Then we fill in the details and a few examples. Never theory for its own sake.",
    thought: "This is just perfect!",
    mnemosyne: "smiling",
  },
  {
    id: "leo-in",
    headline: "LET'S DO IT",
    text: "Less talking, more doing — I'm in. So can I actually write my first query now?",
    thought: "Good — Leo really is keen to practise more.",
    speaker: "leo",
    leo: "excited",
    mnemosyne: "smiling",
  },
  {
    id: "the-loop",
    headline: "PLAIN LANGUAGE → WRITE SQL → GUESS OUTPUT → RUN & COMPARE",
    steps: [
      "Ask your data question in plain language",
      "Ask the same question — but in SQL (SQL reads almost like English, just with stricter rules)",
      "Before you run it, try guessing the result — yes, guessing!",
      "Run it and compare with what your guess was",
    ],
    text: "Yes, Leo — you'll write your first query in a second. But first, let me tell you about the SQL-thinking process — the 'loop'. Repeat these steps until it's second nature. That loop **is** the skill.",
    mnemosyne: "pointing-right",
    units: 44,
  },
  {
    id: "lets-practice",
    headline: "LET'S PRACTISE THE SQL THINKING LOOP",
    text: "OK, Mnemosyne — it makes sense, and somehow I get it. But let's practise now! Can we switch roles for a moment? I'll give the requirement, and **you** run the loop — so I can watch how it's done. Here it is: **\"How many orders did we get in June 2024?\"** That's the plain-language step 1 you mentioned — so how would you run the whole loop on it?",
    thought: "OK — I'll do it once myself, so Leo can watch how it's done.",
    speaker: "leo",
    leo: "talking",
    mnemosyne: "smiling",
    units: 48,
  },
  {
    id: "lets-practice-2",
    headline: "LET'S PRACTISE THE SQL THINKING LOOP",
    text: "Sure, Leo — let's switch for a moment: I'll run the loop, you watch. Here's the SQL query. It might feel strange that I just came up with it — but trust me: practise a bit and you'll do the same. Writing SQL will soon feel as natural as speaking English. Now — before executing it, I'll guess the result: **less than 10**, since business is slow over summer. Actually, Leo — you should run this one: type the query yourself in CloudBeaver, run it, and tell me if I guessed right!",
    reasoning: [
      "Ok… it's a question, so it's a SELECT.",
      "He said \"orders\" → the \"Orders\" table.",
      "\"How many\" → count(*).",
      "Only \"in June\" → so I add a WHERE.",
    ],
    sql: [
      "SELECT count(*)",
      'FROM "Orders"',
      "WHERE \"OrderDate\" >= DATE '2024-06-01'",
      "  AND \"OrderDate\" <  DATE '2024-07-01';",
    ],
    mnemosyne: "talking",
    units: 52,
  },
  {
    id: "hands-on",
    headline: "STOP WATCHING — HANDS-ON TIME",
    cta: [
      "👉 Your turn, Leo.",
      "Type this query in **CloudBeaver**, run it, and get the result.",
      "Don't just watch — **do it.**",
      "▶ Continue once you've run it.",
    ],
    ctaSql: [
      "SELECT count(*)",
      'FROM "Orders"',
      "WHERE \"OrderDate\" >= DATE '2024-06-01'",
      "  AND \"OrderDate\" <  DATE '2024-07-01';",
    ],
    units: 30,
  },
  {
    id: "leo-reports",
    headline: "THE RESULT IS IN",
    text: "Well, Mnemosyne, you know your data — I ran the query, and it came back **4 orders**.",
    thoughtLines: [
      "I like him.",
      "If he keeps this up,",
      "soon he'll write SQL as well as I do.",
    ],
    speaker: "leo",
    leo: "excited",
    mnemosyne: "smiling",
    units: 34,
  },
  {
    id: "mnemosyne-explains",
    headline: "AN EDUCATED GUESS",
    text: "Yes, Leo — I know our data, and I know how the business works, so I made an **educated** guess. And every time you do this **thinking** to reach your (educated) guess, you understand your data a little better — soon you'll have a **feel** for it.",
    thoughtLines: [
      "Someday I'll know my own data",
      "as well as she knows Northwind.",
    ],
    speaker: "mnemosyne",
    mnemosyne: "talking",
    leo: "smiling",
    units: 50,
  },
  {
    id: "boring-theory",
    headline: "THE BORING THEORY",
    text: "Leo, now that you've executed your first SQL query, let me give you a little more background about our setup and where we go from here. You already saw **PostgreSQL** pre-populated with the **Northwind** sample data, and used **CloudBeaver** to connect and run your query…",
    thought: "No… not the boring theory now 😴",
    speaker: "mnemosyne",
    mnemosyne: "talking",
    leo: "thinking",
    units: 40,
  },
  {
    id: "ansi",
    headline: "WHY POSTGRESQL?",
    text: "Why PostgreSQL? It starts fast, and it's a light database server with solid **ANSI SQL** support. ANSI SQL is SQL written the **standard** way — it works across every enterprise database, so what you learn here transfers no matter which one you meet at work. And PostgreSQL itself is **growing fast** in the enterprise.",
    thought: "So I'm not locked to one database. Nice.",
    mnemosyne: "proud",
  },
  {
    id: "koans-duckdb",
    headline: "KOANS & DUCKDB",
    text: "Now, let me introduce two more companions, Leo: the **koans** and **DuckDB**. Koans are **hands-on practice exercises**. Each one is a tiny fill-in-the-blank: replace the blank to make a failing test pass. Every fix unlocks the next, step by step to 'enlightenment'. You practise SQL by **doing**.\n**DuckDB** is a database that's just a file. Think SQLite — but with far better **PostgreSQL** compatibility. The koans run on the pre-populated `northwind.duckdb`. They're completely **self-contained**: no database server needed.",
    thought: "What are koans? What is DuckDB?",
    mnemosyne: "talking",
    leo: "questioning",
    units: 46,
  },
  {
    id: "do-koans",
    headline: "STOP WATCHING — HANDS-ON TIME",
    sticker: "Do your koans!",
    cta: [
      "👉 Your turn, Leo.",
      "Fill the blank, run it, watch red turn green.",
      "Reading is nice but the **koans** are where it sticks.",
      "▶ Walk the path to enlightenment.",
    ],
    ctaSql: [
      "cd c:/DataPallas/db/datazeus",
      ".\\koans.bat mastersql series1 _00",
    ],
    runLabel: "▶ in your terminal — koans.bat <course> <series> <episode>",
    units: 34,
  },
  {
    id: "koans-file",
    headline: "THE KOAN FILE",
    editor: true,
    text: "Here's the koan file, Leo — each **___** is a blank you fill in. Replace it, run, and watch red turn green.",
    units: 42,
  },
  {
    id: "koans-run",
    headline: "RUN IT — RED, THEN GREEN",
    koansRun: true,
    text: "Run it and the koans walk you one step at a time: a **green** win, the next one **red** with a hint — fix, rerun, repeat to enlightenment.",
    units: 44,
  },
  {
    id: "compatible",
    headline: "DUCKDB & POSTGRESQL (ANSI) COMPATIBILITY",
    text: "Leo, did you run your koans? Are you enlightened now? 😊 Here's the beautiful part: both **DuckDB** (the engine your koans run on) and **PostgreSQL** come **pre-populated with the same Northwind** — identical schema, identical data. DuckDB's SQL was even **forked from PostgreSQL**, so the **same query gives the same result** in either one. That's portability in action: the koans run on DuckDB, CloudBeaver talks to PostgreSQL — same SQL, same answers.",
    thoughtLines: [
      "Same Northwind data in both —",
      "so I can run the same SQL queries on either.",
    ],
    mnemosyne: "smiling",
    units: 46,
  },
  {
    id: "duck-or-cb",
    headline: "DUCKDB and POSTGRESQL",
    text: "So why both, Leo? **DuckDB** gives you a smooth, frictionless experience while doing the koans — it's just a file, with no server to start, so nothing gets in your way. **PostgreSQL** is the real database you reach for when you want to explore the data **visually** — you open **CloudBeaver**, which connects to it by default. Same schema, same data, same answers: DuckDB for effortless practice, PostgreSQL for real-world exploration.",
    thoughtLines: [
      "DuckDB is just a file on disk…",
      "same portability benefits as SQLite!",
    ],
    mnemosyne: "talking",
    units: 48,
  },
  {
    id: "lets-go",
    headline: "LET'S GO",
    sticker: "That's all, folks!",
    exchange: [
      { who: "mnemosyne", line: "That's it, Leo — you're ready. In the next lessons, you'll write even more real queries against **Northwind**." },
      { who: "leo", line: "Just what I need, Mnemosyne — to practise writing my own queries as much as possible." },
    ],
    speaker: "mnemosyne", // narration voice = Mnemosyne (Bella); Leo's reply is the on-screen exchange
    mnemosyne: "excited",
    leo: "excited",
  },
];

/* ---- per-scene duration, the 0020-learn2 way: a scene lasts as long as its
   narration when TTS audio exists; otherwise a word-count FALLBACK. DIALOGUE
   scenes scale by spoken words; VISUAL scenes (code card / terminal / editor /
   run-output / steps / cta) get a glance window + on-screen reading time. The
   intro + outro bookends are added on top, and the TOTAL is the dynamic sum —
   computed via useVideoDuration in FirstQueryVideo and in remotion-root. ---- */
const wc = (t: string) => t.replace(/\*\*/g, "").trim().split(/\s+/).filter(Boolean).length;
const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));

const FRAMES_PER_WORD = 13;         // readable dialogue pace (≈ words/sec)
const DIALOGUE_FLOOR_WORDS = 8;     // shortest dialogue ≈ 3.5s
const DIALOGUE_CAP_WORDS = 30;      // longest dialogue ≈ 13s
const SCREEN_BASE_FRAMES = 66;      // ~2.2s glance at the visual
const CAPTION_FRAMES_PER_WORD = 10; // ~3 words/sec reading pace
const SCREEN_FLOOR_FRAMES = 150;    // 5s minimum
const SCREEN_CAP_FRAMES = 420;      // 14s maximum

// A scene is "visual" (gets a glance window + reading time) when it shows a card,
// terminal, editor, run-output, numbered steps or a CTA — i.e. there's a screenshot.
const isVisual = (s: Scene) =>
  !!(s.steps || s.sql || s.terminalSteps || s.editor || s.koansRun || s.cta || s.ctaSql);
const screenWords = (s: Scene) =>
  wc([s.headline, s.text, ...(s.cta ?? []), ...(s.ctaSql ?? []), ...(s.steps ?? [])].filter(Boolean).join(" "));

const sceneDurationFrames = (s: Scene): number => {
  if (s.seconds) return Math.round(s.seconds * FPS); // fixed (e.g. the cycling terminal)
  if (isVisual(s))
    return clamp(SCREEN_BASE_FRAMES + screenWords(s) * CAPTION_FRAMES_PER_WORD, SCREEN_FLOOR_FRAMES, SCREEN_CAP_FRAMES);
  const words = s.exchange ? wc(s.exchange.map((t) => t.line).join(" ")) : wc(s.text ?? "");
  return clamp(words, DIALOGUE_FLOOR_WORDS, DIALOGUE_CAP_WORDS) * FRAMES_PER_WORD;
};

export const INTRO_FRAMES = ANCIENT_GREECE_FRAMES; // Mnemosyne ancient-Greece intro (9s)
// remotion-root sets the composition length to these bookends + the audio-fit total.
export const FIRST_QUERY_BOOKEND_FRAMES = INTRO_FRAMES + OUTRO_FRAMES;

const MNEMO_TTS =
  "Narrate as Mnemosyne — goddess of memory and learning, the warm mentor of DataZeus. Her charisma is an energetic, WARM TEACHER: encouraging, lively and engaged, with easy conversational warmth and a smile in her voice. She has gentle forward momentum and genuine enthusiasm, but she's speaking one-to-one with Leo — never loud, shouty, shrill or over-excited, never an announcer. Her energy comes from warmth and engagement, not from volume.";
const LEO_TTS =
  "Narrate as Leo — a young mortal who is a bright, energetic optimist. Eager, quick and lively; he leans into every reveal with genuine excitement and contagious hope. Even his questions are voiced with forward-leaning energy, never flat or anxious. Keep him buoyant and fast-moving.";

/* ElevenLabs v3 'how to say it' directions — ONE cohesive [emotion] tag per replica,
   then the spoken line (the IDEA of the slide's text, paraphrased for natural speech:
   no ** / `code` marks, "Uau"→"Wow", ___→"blank", S-Q-L / C-L-I spelled out). Only
   scenes whose character actually speaks appear here; titles, SQL, terminal commands
   and file content are never narrated. Both voices stay positive and energetic. */
const stripMarks = (t: string) => t.replace(/\*\*/g, "").replace(/`/g, "").trim();
const DIRECTED: Record<string, string> = {
  "warm-up": "[warm and welcoming, gently upbeat] Hi, Leo. Ready to actually write S-Q-L — not just watch slides about it? Here's how we work here: just a bit of theory, then a whole lot of doing. You'll be querying real data in minutes.",
  "watch-trap": "[warm but candid, energizing] Leo — one real practice session beats watching twenty polished videos. Honestly? You'll watch all twenty, and still freeze the moment you have to write a query yourself.",
  "our-deal": "[warm and direct, encouraging] So here's our deal, Leo: you don't watch me. You come up with the queries and write them yourself — on real, production-like data.",
  "just-enough": "[warm and reassuring, lively] Every lesson opens with the least theory possible — just enough for you to write something. You practise. Then we fill in the details and a few examples. Never theory for its own sake.",
  "leo-in": "[eager and playful, buzzing to start] Less talking, more doing — I'm in! So, can I actually write my first query now?",
  "the-loop": "[warm and guiding, clear] Yes, Leo — you'll write your first query in a second. But first, let me tell you about the S-Q-L thinking process — the loop. Repeat these steps until it's second nature; that loop is the skill. Here they are. One — ask your data question in plain language. Two — ask the same question, but in S-Q-L. S-Q-L reads almost like English, just with stricter rules. Three — before you run it, try guessing the result; yes, guessing! Four — run it, and compare with your guess.",
  "lets-practice": "[curious and eager, leaning forward] Okay, Mnemosyne — it makes sense, I think I get it. But let's practise now! Can we switch roles for a moment? I'll give the requirement, and you run the loop — so I can watch how it's done. Here it is: how many orders did we get in June twenty-twenty-four? That's the plain-language step one — so how would you run the whole loop on it?",
  "lets-practice-2": "[warm, confident and inviting] Sure, Leo — let's switch for a moment: I'll run the loop, you watch. Here's the S-Q-L query. It might feel strange that I just came up with it — but trust me, practise a little and you'll do the same; soon S-Q-L feels as natural as speaking English. Now, before executing it, I'll guess: fewer than ten, since business is slow over summer. Actually, Leo — you should run this one: type the query yourself in CloudBeaver, run it, and tell me if I guessed right!",
  "leo-reports": "[wry and quietly impressed, a half-smile] Well, Mnemosyne, you know your data — I ran the query, and it came back four orders.",
  "mnemosyne-explains": "[warm, wise and encouraging, with spark] Yes, Leo — I know our data, and I know how the business works, so I made an educated guess. And every time you think your way to a guess, you understand your data a little better. Soon, you'll have a feel for it.",
  "boring-theory": "[warm and brisk, making the setup sound fun] Leo, now that you've executed your first S-Q-L query, let me give you a little more background about our setup, and where we go next. You already saw PostgreSQL pre-populated with the Northwind sample data, and used CloudBeaver to connect and run your query.",
  "ansi": "[warm, brisk and confident, lively] Why PostgreSQL? It starts fast, and it's a light database server with rock-solid ANSI S-Q-L support. ANSI S-Q-L is S-Q-L written the standard way — it works across every enterprise database, so what you learn here transfers no matter which one you meet at work. And PostgreSQL itself is growing fast in the enterprise.",
  "koans-duckdb": "[warm and inviting, gently delighted] Now, let me introduce two more companions, Leo: the koans, and DuckDB. Koans are hands-on practice exercises. Each one is a tiny fill-in-the-blank: you replace the blank to make a failing test pass. Every fix unlocks the next, step by step toward enlightenment. You practise S-Q-L by doing. And DuckDB? It's a database that's just a file. Think SQLite — but with far better PostgreSQL compatibility. The koans run on the ready-made northwind-dot-duckdb. They're completely self-contained: no database server needed.",
  "koans-file": "[warm, energetic and involved] Here's the koan file, Leo — each blank is one you fill in. Replace it, run it, and watch red turn green.",
  "koans-run": "[warm, energetic and involved] Run it, and the koans walk you one step at a time: a green win, then the next one red with a hint. Fix it, rerun, and repeat — all the way to enlightenment.",
  "compatible": "[warm, energetic and involved] Leo, did you run your koans? Are you enlightened now? Here's the beautiful part: both DuckDB — the engine your koans run on — and PostgreSQL come pre-populated with the same Northwind: identical schema, identical data. DuckDB's S-Q-L was even forked from PostgreSQL, so the same query gives the same result in either one. That's portability in action: the koans run on DuckDB, CloudBeaver talks to PostgreSQL — same S-Q-L, same answers.",
  "duck-or-cb": "[warm and conversational, easy-going] So why both, Leo? DuckDB gives you a smooth, frictionless experience while doing the koans — it's just a file, with no server to start, so nothing gets in your way. PostgreSQL is the real database you reach for when you want to explore your data visually — you open CloudBeaver, which connects to it by default. Same schema, same data, same answers: DuckDB for effortless practice, PostgreSQL for real-world exploration.",
  "lets-go": "[warm, proud and encouraging] That's it, Leo — you're ready. In the lessons ahead, you'll write even more real queries against Northwind.",
};
// Leo's voiced reply for each two-turn exchange — played on the SAME slide right
// after Mnemosyne (a separate Liam clip; the on-screen exchange already shows the line).
const LEO_REPLY: Record<string, string> = {
  "warm-up": "[warm, positive and confident] Yes, Mnemosyne — I'm ready. Let's go.",
  "watch-trap": "[a rueful, knowing chuckle] Ouch. That's literally been me.",
  "our-deal": "[warm, eager and decisive] Deal — I want to actually write S-Q-L, not just watch.",
  "lets-go": "[warm, upbeat and motivated] Just what I need, Mnemosyne — to practise writing my own queries as much as possible.",
};
const defaultTag = (s: Scene) => (s.speaker === "leo" ? "[bright, eager and upbeat]" : "[warm and encouraging]");
const directedFor = (s: Scene): string =>
  DIRECTED[s.id] ?? (s.text ? `${defaultTag(s)} ${stripMarks(s.text)}` : "");

const pad2 = (n: number) => String(n).padStart(2, "0");
function buildVideoModel(id: string): VideoModel {
  const baseScenes = SCENES.map((s, i) => {
    const speaker: Speaker = s.speaker ?? "mnemosyne";
    const directed = directedFor(s); // "" for non-spoken slides (CTA / your-turn) → silent
    return {
      id: `${pad2(i + 1)}${s.id}`,
      textSsml: directed,
      speakAloud: !!directed.trim(),
      sceneDuration: sceneDurationFrames(s),
      voiceId: speaker === "leo" ? LEO_VOICE_ID : MNEMOSYNE_VOICE_ID,
      aiTts: {
        position: `This is scene ${i + 1} of ${SCENES.length}.`,
        summarizedContext: SUMMARIZED_CONTEXT,
        instructions: speaker === "leo" ? LEO_TTS : MNEMO_TTS,
        // Per-scene ElevenLabs v3 pacing: Mnemosyne (Bella) brisk at 1.1×; Leo at 1.0×.
        speed: speaker === "leo" ? undefined : 1.1,
      },
    };
  });
  // Audio-only companion clips: an exchange's final Leo reply, voiced by Leo (Liam),
  // played on the SAME slide right after Mnemosyne. APPENDED at the end so the visual
  // scenes' ids/indices never shift; FirstQueryVideo renders only the visual scenes.
  const leoScenes: typeof baseScenes = [];
  SCENES.forEach((s) => {
    const t = s.exchange?.[s.exchange.length - 1];
    if (s.exchange && t?.who === "leo") {
      const k = baseScenes.length + leoScenes.length;
      leoScenes.push({
        id: `${pad2(k + 1)}${s.id}-leo`,
        textSsml: LEO_REPLY[s.id] ?? `[bright, eager and upbeat] ${stripMarks(t.line)}`,
        speakAloud: true,
        sceneDuration: clamp(wc(t.line), DIALOGUE_FLOOR_WORDS, DIALOGUE_CAP_WORDS) * FRAMES_PER_WORD,
        voiceId: LEO_VOICE_ID,
        aiTts: {
          position: `Leo's reply on the ${s.id} slide.`,
          summarizedContext: SUMMARIZED_CONTEXT,
          instructions: LEO_TTS,
          speed: undefined,
        },
      });
    }
  });
  const model = new VideoModel({ id, scenes: [...baseScenes, ...leoScenes] });
  model.audio.tts.scenePaddingMs = 250; // breathing room after each line — matches the experiment videos (was 150, felt clipped)
  return model;
}
export const videoModelMasterSqlS1E00 = buildVideoModel(
  "mastersql-series1-00-write-your-first-query",
);

const fadeIn = (frame: number, delay: number, dur = 12, to = 1) =>
  interpolate(frame, [delay, delay + dur], [0, to], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

/* ---- minimal SQL syntax colouring ---- */
const KEYWORDS = new Set(["SELECT", "FROM", "AS", "LIMIT", "WHERE", "AND", "OR", "NOT", "DATE", "COUNT"]);
const hi = (line: string, keyPrefix: string) =>
  (line.match(/"[^"]*"|'[^']*'|\w+|[^\w]+/g) || []).map((t, i) => {
    if (t.startsWith('"')) return <span key={`${keyPrefix}-${i}`} style={{ color: INK }}>{t}</span>;
    if (t.startsWith("'")) return <span key={`${keyPrefix}-${i}`} style={{ color: STR }}>{t}</span>;
    if (/^\w+$/.test(t) && KEYWORDS.has(t.toUpperCase())) return <span key={`${keyPrefix}-${i}`} style={{ color: KW, fontWeight: 700 }}>{t}</span>;
    return <span key={`${keyPrefix}-${i}`} style={{ color: INK_SOFT }}>{t}</span>;
  });

/* ---- the CloudBeaver "code card": types the SQL in when `typed` ---- */
const TYPE_START = 12;
const TYPE_CPS = 1.7; // characters per frame
const QueryCard: React.FC<{ sql: string[]; frame: number; typed?: boolean; compact?: boolean; label?: string; topPx?: number }> = ({ sql, frame, typed, compact, label, topPx }) => {
  const full = sql.join("\n");
  const visibleCount = typed ? clamp(Math.floor((frame - TYPE_START) * TYPE_CPS), 0, full.length) : full.length;
  const typing = typed && visibleCount < full.length;
  const cursorOn = typing && Math.floor(frame / 6) % 2 === 0;
  const shown = typed ? full.slice(0, visibleCount) : full;
  const lines = shown.length ? shown.split("\n") : [""];
  const fontSize = compact ? 28 : 34;
  return (
    <div style={{ position: "absolute", left: 320, right: 320, top: topPx ?? (compact ? 170 : 320), opacity: fadeIn(frame, 4, 12), background: CARD, border: `3px solid ${INK}`, borderRadius: 14, boxShadow: "7px 7px 0 rgba(38,70,83,0.12)", padding: "24px 30px", transform: "rotate(-0.5deg)" }}>
      <div style={{ fontFamily: HAND, fontSize: 20, fontWeight: 700, color: ACCENT, marginBottom: 10 }}>{label ?? "▶ run in CloudBeaver"}</div>
      <pre style={{ margin: 0, fontFamily: MONO, fontSize, lineHeight: 1.45, whiteSpace: "pre", minHeight: fontSize * 1.45 }}>
        {lines.map((l, i) => (
          <div key={i}>
            {hi(l, `l${i}`)}
            {i === lines.length - 1 && cursorOn && <span style={{ color: INK }}>▏</span>}
          </div>
        ))}
      </pre>
    </div>
  );
};

/* ---- DuckDB-CLI line colouring: orange prompt, blue SQL keywords, dim banner —
   evokes the real terminal (2nd screenshot) without a full dark theme ---- */
const PROMPT_RE = /^(northwind D|[A-Za-z]:\\[^>]*>|PS [^>]*>)\s?/;
const termLine = (line: string, keyPrefix: string): React.ReactNode => {
  if (/^DuckDB v/i.test(line) || line.includes(".help")) {
    return <span style={{ color: INK_SOFT }}>{line}</span>; // dim banner / hint
  }
  const m = line.match(PROMPT_RE);
  const prompt = m ? m[0] : "";
  const rest = m ? line.slice(prompt.length) : line;
  const tokens = rest.match(/\w+|[^\w]+/g) || [];
  return (
    <>
      {prompt && <span style={{ color: ACCENT, fontWeight: 700 }}>{prompt}</span>}
      {tokens.map((t, i) =>
        /^\w+$/.test(t) && KEYWORDS.has(t.toUpperCase()) ? (
          <span key={`${keyPrefix}-${i}`} style={{ color: KW, fontWeight: 700 }}>{t}</span>
        ) : (
          <span key={`${keyPrefix}-${i}`} style={{ color: INK }}>{t}</span>
        ),
      )}
    </>
  );
};

/* ---- a terminal that CYCLES through screens, 5s each, looping for the scene ---- */
const TerminalCard: React.FC<{ steps: { label?: string; lines: string[] }[]; frame: number; topPx?: number }> = ({ steps, frame, topPx }) => {
  const STEP = 5 * FPS; // 5 seconds per screen
  const idx = Math.floor(frame / STEP) % steps.length;
  const step = steps[idx];
  const local = frame % STEP;
  const swap = interpolate(local, [0, 9], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }); // quick fade on each change
  const maxLines = Math.max(...steps.map((s) => s.lines.length));
  return (
    <div style={{ position: "absolute", left: 300, right: 300, top: topPx ?? 250, opacity: fadeIn(frame, 4, 12), background: CARD, border: `3px solid ${INK}`, borderRadius: 14, boxShadow: "7px 7px 0 rgba(38,70,83,0.12)", padding: "22px 28px", transform: "rotate(-0.5deg)" }}>
      <div style={{ fontFamily: HAND, fontSize: 20, fontWeight: 700, color: ACCENT, marginBottom: 28 }}>{step.label ?? "▶ in your terminal"}</div>
      <pre style={{ margin: 0, fontFamily: MONO, fontSize: 26, lineHeight: 1.45, whiteSpace: "pre", opacity: swap, minHeight: 26 * 1.45 * maxLines }}>
        {step.lines.map((l, i) => (
          <div key={i}>{l === "" ? " " : termLine(l, `t${i}`)}</div>
        ))}
      </pre>
      <div style={{ marginTop: 14, display: "flex", gap: 9 }}>
        {steps.map((_, i) => (
          <div key={i} style={{ width: 11, height: 11, borderRadius: "50%", background: i === idx ? ACCENT : INK_SOFT, opacity: i === idx ? 1 : 0.3 }} />
        ))}
      </div>
    </div>
  );
};

/* ---- popped-in query result: a big number for a single cell, else a grid ---- */
const ResultCard: React.FC<{ result: ResultSet; frame: number; delay: number }> = ({ result, frame, delay }) => {
  const pop = interpolate(frame, [delay, delay + 12], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const scale = interpolate(frame, [delay, delay + 14], [0.92, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const single = result.rows.length === 1 && result.rows[0].length === 1;
  const wrap: React.CSSProperties = { position: "absolute", left: 320, right: 320, top: 540, opacity: pop, transform: `scale(${scale})`, transformOrigin: "center top" };
  if (single) {
    return (
      <div style={{ ...wrap, textAlign: "center" }}>
        <div style={{ fontFamily: MONO, fontSize: 22, color: INK_SOFT, marginBottom: 4 }}>{result.columns[0]}</div>
        <div style={{ display: "inline-block", fontFamily: HAND, fontSize: 150, fontWeight: 800, color: ACCENT, lineHeight: 1, borderBottom: `8px solid ${ACCENT}`, padding: "0 24px 6px" }}>
          {result.rows[0][0]}
        </div>
      </div>
    );
  }
  const cols = result.columns.length;
  return (
    <div style={{ ...wrap, background: CARD, border: `3px solid ${INK}`, borderRadius: 12, boxShadow: "6px 6px 0 rgba(38,70,83,0.12)", overflow: "hidden", fontFamily: MONO, fontSize: 24 }}>
      <div style={{ display: "grid", gridTemplateColumns: `repeat(${cols}, 1fr)`, background: "rgba(47,109,176,0.10)", color: KW, fontWeight: 700, borderBottom: `2px solid ${INK}` }}>
        {result.columns.map((c, i) => <div key={i} style={{ padding: "10px 18px" }}>{c}</div>)}
      </div>
      {result.rows.map((r, ri) => (
        <div key={ri} style={{ display: "grid", gridTemplateColumns: `repeat(${cols}, 1fr)`, borderBottom: ri < result.rows.length - 1 ? `1px solid rgba(139,115,85,0.25)` : "none", color: INK }}>
          {r.map((cell, ci) => <div key={ci} style={{ padding: "8px 18px" }}>{cell}</div>)}
        </div>
      ))}
    </div>
  );
};

/* ---- the LISTENER's inner monologue: hand-WRITTEN in the empty middle via the
   house-style WriteOn reveal (pen off → reads as a thought forming, not penned),
   muted ink. Visual only — never spoken. Keep each thought to ONE short line
   (WriteOn is single-line / nowrap). ---- */
const ThoughtBubble: React.FC<{ lines: string[]; who: string; frame: number }> = ({ lines, who, frame }) => {
  const multi = lines.length > 1;
  // Write the lines SEQUENTIALLY (one finishes before the next starts) — faster
  // per-line write so the whole thought still reads quickly.
  const lineDur = Math.round((multi ? 0.8 : 2.4) * FPS);
  const gap = multi ? 5 : 0;
  return (
    <>
      <div style={{ position: "absolute", left: 480, top: 292, fontFamily: HAND, fontSize: 24, fontWeight: 700, color: INK_SOFT, opacity: fadeIn(frame, 2, 10) }}>💭 {who} is thinking…</div>
      {lines.map((ln, i) => (
        <WriteOn key={i} text={ln} x={480} y={386 + i * 52} size={multi ? 34 : 38} color={INK_SOFT} weight={600} delay={8 + i * (lineDur + gap)} dur={lineDur} pen={false} />
      ))}
    </>
  );
};

const renderCaption = (text: string, base: string): React.ReactNode => {
  if (!text.includes("**")) return text;
  return text.split(/(\*\*[^*]+\*\*)/g).filter((p) => p.trim() !== "").map((p, i) => {
    const bold = p.startsWith("**") && p.endsWith("**");
    // emphasis contrasts the base: ACCENT on INK lines, INK on Leo's ACCENT lines
    const col = bold ? (base === ACCENT ? INK : ACCENT) : base;
    return <span key={i} style={{ fontWeight: bold ? 800 : 500, color: col }}>{bold ? p.slice(2, -2) : p}</span>;
  });
};

/* ---- a Notepad++-style mock of the koan file: file tree + syntax-highlighted code,
   with every ___ blank highlighted red (the thing the learner fills) ---- */
const NP_KW = new Set(["package", "import", "class", "extends", "def", "return", "new", "static", "final", "void"]);
const npToken = (line: string, kp: string): React.ReactNode => {
  const ts = line.trimStart();
  if (ts.startsWith("//") || ts.startsWith("*") || ts.startsWith("/*")) {
    return <span style={{ color: "#2e8b57", fontStyle: "italic" }}>{line}</span>;
  }
  const toks = line.match(/___|"[^"]*"|'[^']*'|\w+|[^\w]+/g) || [];
  return toks.map((t, i) => {
    if (t === "___") return <span key={`${kp}-${i}`} style={{ color: "#d32f2f", fontWeight: 800, background: "#fde2e2", borderRadius: 3, padding: "0 3px" }}>{t}</span>;
    if (t.startsWith('"') || t.startsWith("'")) return <span key={`${kp}-${i}`} style={{ color: "#9c5a2b" }}>{t}</span>;
    if (/^\d+$/.test(t)) return <span key={`${kp}-${i}`} style={{ color: "#c0562a", fontWeight: 700 }}>{t}</span>;
    if (/^\w+$/.test(t) && NP_KW.has(t)) return <span key={`${kp}-${i}`} style={{ color: "#0b6fa4", fontWeight: 700 }}>{t}</span>;
    return <span key={`${kp}-${i}`} style={{ color: "#2b2b2b" }}>{t}</span>;
  });
};
/* the koan-1 blank, animated: shows ___ , then TYPES "count" (with a cursor) and settles to
   a green highlight — so the viewer sees exactly how to fill a koan (red -> green). */
const animFill = (line: string, frame: number): React.ReactNode => {
  const indent = (line.match(/^\s*/) || [""])[0];
  const START = 55, CPF = 4, ANSWER = "count";
  const typing = frame >= START;
  const n = Math.max(0, Math.min(ANSWER.length, Math.floor((frame - START) / CPF)));
  const done = n >= ANSWER.length;
  const cursor = typing && !done && Math.floor(frame / 6) % 2 === 0;
  return (
    <span style={{ color: "#2b2b2b" }}>
      {indent}SELECT{" "}
      {!typing ? (
        <span style={{ color: "#d32f2f", fontWeight: 800, background: "#fde2e2", borderRadius: 3, padding: "0 3px" }}>___</span>
      ) : (
        <span style={{ color: "#2b2b2b", fontWeight: 700, background: done ? "#d8f3dc" : "#fff3c4", borderRadius: 3, padding: "0 2px" }}>{ANSWER.slice(0, n)}{cursor ? "▏" : ""}</span>
      )}
      (*) FROM <span style={{ color: "#9c5a2b" }}>"Orders"</span>
    </span>
  );
};
const ED_TREE: { d: number; folder?: boolean; open?: boolean; name: string; active?: boolean }[] = [
  { d: 0, folder: true, open: true, name: "datazeus" },
  { d: 1, folder: true, name: "courses" },
  { d: 1, folder: true, name: "datasets" },
  { d: 1, folder: true, open: true, name: "tests" },
  { d: 2, folder: true, open: true, name: "src" },
  { d: 3, folder: true, open: true, name: "koans" },
  { d: 4, folder: true, open: true, name: "groovy / datazeus" },
  { d: 5, folder: true, open: true, name: "mastersql" },
  { d: 6, folder: true, open: true, name: "series1" },
  { d: 7, folder: true, open: true, name: "_00" },
  { d: 8, name: "WriteYourFirstQueryKoans.groovy", active: true },
  { d: 2, folder: true, name: "verify" },
  { d: 1, name: "koans.bat" },
  { d: 1, name: "koans.sh" },
];
const ED_CODE: string[] = [
  "package datazeus.mastersql.series1._00",
  "",
  "import datazeus.koans.KoanBase",
  "import spock.lang.Stepwise",
  "",
  "@Stepwise",
  "class WriteYourFirstQueryKoans extends KoanBase {",
  "",
  "    // Fill each ___ , then run the koans — red turns green.",
  '    def "count every order in the table"() {',
  "        expect:",
  "        shouldReturn 79, '''",
  '            SELECT ___(*) FROM "Orders"',
  "        '''",
  "    }",
  "",
  '    def "keep only the orders shipped in June 2024"() {',
  "        expect:",
  "        shouldReturn 4, '''",
  '            SELECT count(*) FROM "Orders"',
  "            WHERE \"OrderDate\" >= DATE '2024-06-01'",
  "              AND \"OrderDate\" ___  DATE '2024-07-01'",
  "        '''",
  "    }",
  "}",
];
const EditorMock: React.FC<{ frame: number }> = ({ frame }) => {
  const rowH = 25;
  const menu = ["File", "Edit", "Search", "View", "Encoding", "Language", "Settings", "Tools", "Macro", "Run", "Plugins", "Window", "?"];
  return (
    <div style={{ position: "absolute", left: 70, right: 70, top: 160, bottom: 142, opacity: fadeIn(frame, 4, 14), background: "#ffffff", border: "2px solid #b9b9b2", borderRadius: 10, boxShadow: "9px 9px 0 rgba(38,70,83,0.14)", overflow: "hidden", display: "flex", flexDirection: "column", fontFamily: "'Segoe UI', sans-serif", transform: "rotate(-0.4deg)" }}>
      <div style={{ height: 34, background: "#e8e8e2", borderBottom: "1px solid #cfcfc8", display: "flex", alignItems: "center", padding: "0 14px", fontSize: 16, color: "#444" }}>
        <span style={{ flex: 1, fontWeight: 600 }}>WriteYourFirstQueryKoans.groovy — Notepad++</span>
        <span style={{ color: "#999", letterSpacing: 8 }}>_ ▢ ✕</span>
      </div>
      <div style={{ height: 26, background: "#f3f3f0", borderBottom: "1px solid #d8d8d2", display: "flex", alignItems: "center", gap: 15, padding: "0 14px", fontSize: 14, color: "#555" }}>
        {menu.map((m) => <span key={m}>{m}</span>)}
      </div>
      <div style={{ flex: 1, display: "flex", minHeight: 0 }}>
        <div style={{ width: 380, background: "#fafaf8", borderRight: "1px solid #d8d8d2", padding: "8px 0", fontSize: 15, color: "#333", overflow: "hidden" }}>
          <div style={{ padding: "0 12px 6px", fontWeight: 700, color: "#777", fontSize: 13 }}>Folder as Workspace</div>
          {ED_TREE.map((n, i) => (
            <div key={i} style={{ display: "flex", alignItems: "center", height: rowH, paddingLeft: 12 + n.d * 15, background: n.active ? "#cfe3f5" : "transparent" }}>
              <span style={{ marginRight: 6 }}>{n.folder ? (n.open ? "📂" : "📁") : "📄"}</span>
              <span style={{ fontWeight: n.active ? 700 : 400, whiteSpace: "nowrap", color: n.active ? "#1c3d5a" : "#333" }}>{n.name}</span>
            </div>
          ))}
        </div>
        <div style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
          <div style={{ height: 30, background: "#e8e8e2", borderBottom: "1px solid #d8d8d2", display: "flex", alignItems: "flex-end" }}>
            <div style={{ background: "#ffffff", border: "1px solid #cfcfc8", borderBottom: "none", padding: "5px 16px", fontSize: 14, color: "#222", borderTopLeftRadius: 5, borderTopRightRadius: 5, marginLeft: 8 }}>WriteYourFirstQueryKoans.groovy</div>
          </div>
          <div style={{ flex: 1, display: "flex", background: "#ffffff", overflow: "hidden", fontFamily: MONO, fontSize: 20, lineHeight: `${rowH}px` }}>
            <div style={{ background: "#f7f7f4", color: "#aaa", textAlign: "right", padding: "8px 10px 0", borderRight: "1px solid #ececec" }}>
              {ED_CODE.map((_, i) => <div key={i} style={{ height: rowH }}>{i + 1}</div>)}
            </div>
            <pre style={{ margin: 0, padding: "8px 0 0 14px", whiteSpace: "pre", overflow: "hidden" }}>
              {ED_CODE.map((l, i) => {
                const animated = l.indexOf('SELECT ___(*)') >= 0;
                return (
                  <div key={i} style={{ height: rowH }}>
                    {l === "" ? " " : animated ? animFill(l, frame) : npToken(l, `e${i}`)}
                  </div>
                );
              })}
            </pre>
          </div>
        </div>
      </div>
      <div style={{ height: 26, background: "#e8e8e2", borderTop: "1px solid #cfcfc8", display: "flex", alignItems: "center", padding: "0 14px", fontSize: 13, color: "#555", gap: 22 }}>
        <span style={{ flex: 1 }}>Groovy source file</span>
        <span>Ln : 13   Col : 24</span>
        <span>Unix (LF)</span>
        <span>UTF-8</span>
        <span>INS</span>
      </div>
    </div>
  );
};

/* ---- the koans run-output card: the "path to enlightenment" screen the runner prints,
   styled like the terminal card (parchment), with the real red→green colours ---- */
const KoansRunCard: React.FC<{ frame: number }> = ({ frame }) => {
  const CY = "#1f8a8a", GR = "#2e8b57", RD = "#c0392b";
  const blank = <span style={{ color: RD, fontWeight: 800, background: "#fde2e2", borderRadius: 3, padding: "0 3px" }}>___</span>;
  const row = (pad: number, node: React.ReactNode, key: string) => (
    <div key={key} style={{ paddingLeft: pad, minHeight: 36 }}>{node}</div>
  );
  return (
    <div style={{ position: "absolute", left: 280, right: 280, top: 244, opacity: fadeIn(frame, 4, 12), background: CARD, border: `3px solid ${INK}`, borderRadius: 14, boxShadow: "8px 8px 0 rgba(38,70,83,0.12)", padding: "24px 36px", transform: "rotate(-0.5deg)", fontFamily: MONO, fontSize: 24, lineHeight: 1.5 }}>
      <div style={{ fontFamily: HAND, fontSize: 20, fontWeight: 700, color: ACCENT, marginBottom: 14 }}>▶ in your terminal</div>
      {row(0, <span style={{ color: INK }}>{".\\koans.bat mastersql series1 _00"}</span>, "cmd")}
      <div style={{ height: 14 }} />
      {row(0, <span style={{ color: CY, fontWeight: 800 }}>Forging 'series1 _00 Write Your First Query'</span>, "h")}
      <div style={{ height: 12 }} />
      {row(28, <span style={{ color: GR }}>You mastered 'count every order in the table' — +1 awareness.</span>, "g")}
      {row(28, <span style={{ color: RD }}>'keep only the orders shipped in June 2024' — damaged your karma.</span>, "r")}
      <div style={{ height: 12 }} />
      {row(0, <span style={{ color: INK, fontWeight: 700 }}>You have not yet reached enlightenment ...</span>, "e")}
      {row(28, <span style={{ color: INK }}>it should return <span style={{ color: GR, fontWeight: 800 }}>4</span></span>, "hint")}
      <div style={{ height: 12 }} />
      {row(0, <span style={{ color: INK, fontWeight: 700 }}>Please meditate on the following code:</span>, "m")}
      {row(28, <span style={{ color: CY }}>…/series1/_00/WriteYourFirstQueryKoans.groovy:43</span>, "path")}
      {row(28, <span style={{ color: INK }}>43:&nbsp; AND "OrderDate" {blank}  DATE '2024-07-01'</span>, "code")}
      <div style={{ height: 12 }} />
      {row(28, <span style={{ color: INK }}>your path thus far  [<span style={{ color: GR, fontWeight: 800 }}>#</span><span style={{ color: "#bbb" }}>..</span>]  <b>1</b> of <b>3</b> koans</span>, "bar")}
    </div>
  );
};

// A rubber stamp "slammed" across the middle of the slide — presses down late and inks in.
const Stamp: React.FC<{ text: string; frame: number }> = ({ text, frame }) => {
  const op = interpolate(frame, [16, 24], [0, 0.9], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const sc = interpolate(frame, [16, 27], [1.28, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <div style={{ position: "absolute", top: 372, left: 0, right: 0, display: "flex", justifyContent: "center", pointerEvents: "none" }}>
      <div style={{ transform: `rotate(-11deg) scale(${sc})`, opacity: op, border: `6px solid ${ACCENT}`, outline: `2px solid ${ACCENT}`, outlineOffset: 5, color: ACCENT, fontFamily: HAND, fontWeight: 800, fontSize: 78, textTransform: "uppercase", letterSpacing: 3, padding: "14px 46px", borderRadius: 14 }}>
        {text}
      </div>
    </div>
  );
};

const DoodleScene: React.FC<{ scene: Scene; sceneModel: SceneModel; modelId: string; voiceId: string; durationInFrames: number; leoStart?: number; leoSceneModel?: SceneModel }> = ({ scene, sceneModel, modelId, voiceId, durationInFrames, leoStart, leoSceneModel }) => {
  const frame = useCurrentFrame();
  const hasResult = !!scene.result;
  const speaker: Speaker = scene.speaker ?? "mnemosyne";
  const listenerName = speaker === "leo" ? "Mnemosyne" : "Leo";
  // For a two-turn exchange the spotlight FOLLOWS whoever is speaking now (turns
  // paced by word count); otherwise it's the scene's single speaker.
  let activeTurn = 0;
  const exStarts: number[] = [];
  if (scene.exchange) {
    const last = scene.exchange.length - 1;
    if (leoStart != null) {
      // Two-voice exchange: Mnemosyne speaks [0, leoStart], then Leo's clip plays from
      // leoStart — highlight the turn that matches the voice you're hearing.
      scene.exchange.forEach((_, i) => exStarts.push(i < last ? 0 : leoStart));
    } else {
      // Fallback (no Leo clip): pace to the scene's length, reserve a window for the last turn.
      const lastWindow = clamp(wc(scene.exchange[last].line) * 14, 55, Math.floor(durationInFrames * 0.45));
      const head = Math.max(1, durationInFrames - lastWindow);
      const headTurns = scene.exchange.slice(0, last);
      const wsum = headTurns.reduce((a, t) => a + Math.max(1, wc(t.line)), 0) || 1;
      let acc = 0;
      headTurns.forEach((t) => { exStarts.push(Math.round(acc)); acc += (Math.max(1, wc(t.line)) / wsum) * head; });
      exStarts.push(head);
    }
    exStarts.forEach((s, i) => { if (frame >= s) activeTurn = i; });
  }
  const activeWho: Speaker = scene.exchange ? scene.exchange[activeTurn].who : speaker;
  const leoSpeaking = activeWho === "leo";
  const mnemoSpeaking = activeWho === "mnemosyne";
  const tLines = scene.thoughtLines ?? (scene.thought ? [scene.thought] : null);
  // headline shrinks for long titles (e.g. the loop's process arrow), underline follows
  const hlLen = scene.headline?.length ?? 0;
  const hlLong = hlLen > 30;
  const hlSize = hlLong ? 44 : 76;
  const capLong = (scene.text?.length ?? 0) > 200; // long narration → smaller caption + a compact code card
  const sqlTop = scene.reasoning ? 430 : hasResult ? 170 : capLong ? 300 : 320; // push the card down when reasoning sits above it
  return (
    <AbsoluteFill style={{ backgroundColor: PAPER }}>
      {TTS_AUDIO_ENABLED && VIDEO_CONFIG.audio.tts.engine !== "web-speech-api" && sceneModel.speakAloud && (
        <Audio src={staticFile(getAssetOutputFileName("assets/rb", modelId, sceneModel.id, getVoiceInfoDetails(sceneModel.voiceId || voiceId)))} volume={VIDEO_CONFIG.audio.sceneAudio.defaultVolume} />
      )}
      {/* Leo's voiced reply on the SAME slide — plays right after Mnemosyne finishes */}
      {TTS_AUDIO_ENABLED && VIDEO_CONFIG.audio.tts.engine !== "web-speech-api" && leoSceneModel && leoStart != null && (
        <Sequence from={leoStart}>
          <Audio src={staticFile(getAssetOutputFileName("assets/rb", modelId, leoSceneModel.id, getVoiceInfoDetails(leoSceneModel.voiceId || LEO_VOICE_ID)))} volume={VIDEO_CONFIG.audio.sceneAudio.defaultVolume} />
        </Sequence>
      )}
      <svg viewBox="0 0 1920 1080" xmlns="http://www.w3.org/2000/svg" style={{ width: "100%", height: "100%", position: "absolute" }}>
        <defs>
          <pattern id="dz-dots" x="0" y="0" width="20" height="20" patternUnits="userSpaceOnUse">
            <circle cx="10" cy="10" r="0.9" fill={INK_SOFT} fillOpacity="0.08" />
          </pattern>
        </defs>
        <rect x="0" y="0" width="1920" height="1080" fill="url(#dz-dots)" />
        {scene.headline && (
          <g opacity={fadeIn(frame, 0, 8)}>
            {/* title underline removed (this video only) — keep attention on the slide's content */}
            <text x={960} y={hlLong ? 98 : 110} textAnchor="middle" fontFamily={HAND} fontSize={hlSize} fontWeight={800} fill={INK}>{scene.headline}</text>
          </g>
        )}
      </svg>
      {scene.centerLines && (
        <div style={{ position: "absolute", left: 160, right: 160, top: 330, textAlign: "center", opacity: fadeIn(frame, 8, 14) }}>
          {scene.centerLines.map((l, i) => {
            const pivot = l.trim() === ">";
            return (
              <div key={i} style={{ fontFamily: HAND, fontSize: pivot ? 88 : 58, fontWeight: 800, color: pivot ? ACCENT : i === 0 ? INK : INK_SOFT, lineHeight: 1.15, margin: pivot ? "8px 0" : 0, transform: pivot ? "rotate(-2deg)" : "none" }}>
                {l}
              </div>
            );
          })}
        </div>
      )}
      {scene.cta && (
        <div style={{ position: "absolute", left: 160, right: 160, top: scene.ctaSql ? (scene.sticker ? 348 : 240) : 380, textAlign: "center", opacity: fadeIn(frame, 6, 14) }}>
          {/* first line — the big "Your turn" hook */}
          <div style={{ fontFamily: HAND, fontSize: 84, fontWeight: 800, color: ACCENT, marginBottom: scene.ctaSql ? 60 : 120, lineHeight: 1.15, transform: "rotate(-1deg)", textShadow: "3px 4px 0 rgba(38,70,83,0.12)" }}>
            {renderCaption(scene.cta[0], ACCENT)}
          </div>
          {/* the exact query to type (shown again so the learner doesn't scroll back) */}
          {scene.ctaSql && (
            <div style={{ display: "inline-block", margin: "0 auto 44px", background: CARD, border: `3px solid ${INK}`, borderRadius: 14, boxShadow: "7px 7px 0 rgba(38,70,83,0.12)", padding: "24px 38px", textAlign: "left", transform: "rotate(-0.5deg)" }}>
              <div style={{ fontFamily: HAND, fontSize: 24, fontWeight: 700, color: ACCENT, marginBottom: 16 }}>{scene.runLabel ?? "▶ run in CloudBeaver"}</div>
              <pre style={{ margin: 0, fontFamily: MONO, fontSize: 40, lineHeight: 1.4, whiteSpace: "pre" }}>
                {scene.ctaSql.map((l, i) => (<div key={i}>{hi(l, `cq${i}`)}</div>))}
              </pre>
            </div>
          )}
          {/* remaining instruction lines */}
          {scene.cta.slice(1).map((line, j) => {
            const i = j + 1;
            const last = i === scene.cta!.length - 1;
            return (
              <div key={i} style={{ fontFamily: HAND, fontSize: last ? 40 : 46, fontWeight: 600, color: last ? ACCENT : INK, marginBottom: 18, lineHeight: 1.2 }}>
                {renderCaption(line, last ? ACCENT : INK)}
              </div>
            );
          })}
        </div>
      )}
      {scene.steps && (
        <div style={{ position: "absolute", left: 470, right: 360, top: 296, fontFamily: HAND }}>
          {scene.steps.map((s, i) => {
            const o = fadeIn(frame, 10 + i * 16, 14);
            return (
              <div key={i} style={{ display: "flex", alignItems: "center", gap: 20, marginBottom: 24, opacity: o, transform: `translateX(${(1 - o) * -18}px)` }}>
                <div style={{ flex: "0 0 auto", width: 50, height: 50, borderRadius: "50%", background: ACCENT, color: PAPER, fontWeight: 800, fontSize: 28, display: "flex", alignItems: "center", justifyContent: "center" }}>{i + 1}</div>
                <div style={{ fontSize: 36, color: INK, lineHeight: 1.15 }}>{s}</div>
              </div>
            );
          })}
        </div>
      )}
      {scene.reasoning && (
        <>
          <div style={{ position: "absolute", left: 470, top: 196, fontFamily: HAND, fontSize: 24, fontWeight: 700, color: INK_SOFT, opacity: fadeIn(frame, 2, 10) }}>💭 Mnemosyne thoughts…</div>
          <div style={{ position: "absolute", left: 470, right: 470, top: 248, textAlign: "left", fontFamily: HAND, fontSize: 30, lineHeight: 1.4, color: INK_SOFT, opacity: fadeIn(frame, 6, 12) }}>
            {scene.reasoning.map((l, i) => (<div key={i}>{l}</div>))}
          </div>
        </>
      )}
      {scene.sql && <QueryCard sql={scene.sql} frame={frame} typed={scene.typed} compact={hasResult || capLong} label={scene.runLabel} topPx={sqlTop} />}
      {scene.terminalSteps && <TerminalCard steps={scene.terminalSteps} frame={frame} />}
      {scene.result && <ResultCard result={scene.result} frame={frame} delay={scene.typed ? 30 : 14} />}
      {tLines && <ThoughtBubble lines={tLines} who={listenerName} frame={frame} />}
      {scene.editor && <EditorMock frame={frame} />}
      {scene.koansRun && <KoansRunCard frame={frame} />}
      {(scene.editor || scene.koansRun) && scene.text && (
        <div style={{ position: "absolute", left: 200, right: 200, bottom: scene.koansRun ? 88 : 26, textAlign: "center", fontFamily: HAND, fontSize: 34, lineHeight: 1.25, color: INK, opacity: fadeIn(frame, 8, 12) }}>
          {renderCaption(scene.text, INK)}
          {/* authoritative on-screen directive — NOT in scene.text, so the TTS never reads it */}
          {scene.koansRun && (
            <div style={{ marginTop: 14, fontWeight: 800, color: ACCENT }}>▶ Continue only once every koan is green.</div>
          )}
        </div>
      )}
      {scene.stamp && <Stamp text={scene.stamp} frame={frame} />}
      {scene.sticker && (
        <div style={{ position: "absolute", top: scene.cta ? 208 : 372, left: "50%", transformOrigin: "center", transform: `translateX(-50%) rotate(-9deg) scale(${interpolate(frame, [12, 20, 26], [0, 1.14, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" })})`, background: INK, color: PAPER, fontFamily: HAND, fontWeight: 800, fontSize: scene.cta ? 36 : 58, padding: scene.cta ? "20px 32px" : "30px 50px", borderRadius: scene.cta ? 20 : 28, border: `4px solid ${PAPER}`, boxShadow: "0 9px 20px rgba(38,70,83,0.30)" }}>
          {scene.sticker}
        </div>
      )}
      {/* normal dialogue slide: both heads present (speaker highlighted, listener dimmed) + caption.
          A CTA / editor / run-output slide is dialogue-free — no avatars. */}
      {!scene.cta && !scene.editor && !scene.koansRun && (
        <>
          <CharacterHead who="leo" expr={scene.leo ?? "smiling"} side="left" speaking={leoSpeaking} />
          <CharacterHead who="mnemosyne" expr={scene.mnemosyne ?? "smiling"} side="right" speaking={mnemoSpeaking} />
          {scene.exchange ? (
            <div style={{ position: "absolute", left: 340, right: 340, bottom: 48, textAlign: "left" }}>
              {scene.exchange.map((t, i) => (
                <div key={i} style={{ marginBottom: 16, fontFamily: HAND, fontSize: 30, lineHeight: 1.28, color: t.who === "leo" ? ACCENT : INK, opacity: fadeIn(frame, exStarts[i] ?? 0, 8) * (i === activeTurn ? 1 : 0.5) }}>
                  <span style={{ fontWeight: 800 }}>{t.who === "leo" ? "Leo: " : "Mnemosyne: "}</span>
                  {renderCaption(t.line, t.who === "leo" ? ACCENT : INK)}
                </div>
              ))}
            </div>
          ) : scene.text ? (
            <div style={{ position: "absolute", left: 360, right: 360, bottom: 52, textAlign: "center", fontFamily: HAND, fontSize: capLong ? 32 : 40, lineHeight: 1.3, color: speaker === "leo" ? ACCENT : INK, opacity: fadeIn(frame, 4, 10) }}>
              {scene.text.split("\n").map((para, i, arr) => (
                <div key={i} style={{ marginBottom: i < arr.length - 1 ? 20 : 0 }}>
                  {renderCaption(para, speaker === "leo" ? ACCENT : INK)}
                </div>
              ))}
            </div>
          ) : null}
        </>
      )}
    </AbsoluteFill>
  );
};

export const FirstQueryVideo: React.FC<{ voiceId?: string; backgroundMusic?: string }> = ({ voiceId, backgroundMusic }) => {
  // Audio-fit per-scene durations (falls back to the word-count sceneDuration when
  // no TTS audio exists — so the video never breaks). Total is the dynamic sum.
  const { durations } = useVideoDuration(videoModelMasterSqlS1E00, voiceId || NARRATOR_VOICE_ID);
  if (!durations || durations.length === 0) return null;

  // Only the first SCENES.length model entries are visual; any extra entries are
  // appended audio-only Leo-reply clips. Map each visual exchange → its Leo clip index.
  const VISUAL_COUNT = SCENES.length;
  const leoIndexByVisual: Record<number, number> = {};
  videoModelMasterSqlS1E00.scenes.forEach((sm, idx) => {
    if (idx >= VISUAL_COUNT && sm.id.endsWith("-leo")) {
      const parent = sm.id.replace(/^\d+/, "").replace(/-leo$/, "");
      const vIdx = SCENES.findIndex((s) => s.id === parent);
      if (vIdx >= 0) leoIndexByVisual[vIdx] = idx;
    }
  });
  // A visual scene lasts its own narration + (for exchanges) Leo's reply clip.
  const visualDur = (i: number) => durations[i] + (leoIndexByVisual[i] != null ? durations[leoIndexByVisual[i]] : 0);

  const panelsStart = INTRO_FRAMES;
  let panelsFrames = 0;
  for (let i = 0; i < VISUAL_COUNT; i++) panelsFrames += visualDur(i);
  const outroStart = panelsStart + panelsFrames;
  const totalFrames = outroStart + OUTRO_FRAMES;
  const baseVol = VIDEO_CONFIG.audio.backgroundMusic.defaultVolume;
  const swellStart = outroStart;

  return (
    <>
      {/* ONE continuous music bed: fades in at the very start, plays the intro +
          lesson at 0.8x, swells to 1.0x for the outro, fades out only at the end. */}
      {VIDEO_CONFIG.audio.backgroundMusic.enabled && (
        <Audio
          src={backgroundMusic || VIDEO_CONFIG.audio.backgroundMusic.defaultFile}
          volume={(f) =>
            baseVol *
            interpolate(f, [0, 30], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }) *
            interpolate(f, [swellStart, swellStart + 45], [0.8, 1.0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }) *
            interpolate(f, [totalFrames - 40, totalFrames - 8], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" })
          }
          loop
        />
      )}

      {/* Mnemosyne ancient-Greece intro — Leo dissolves into Mnemosyne; "Learn SQL ... Become a Data God!" */}
      <Sequence key="intro" from={0} durationInFrames={INTRO_FRAMES}>
        <AncientGreeceIntro
          elderFolder="leo-mnemosyne-greek"
          elderFile="mnemosyne-proud"
          scene="helicon"
          tagline="Learn SQL ... Become a Data God!"
          accent="Data God"
          elderName="Mnemosyne"
          elderRole="goddess of learning"
          playMusic={false}
        />
      </Sequence>

      {/* the lesson panels — each as long as its narration (audio-fit) / word-count fallback */}
      {Array.from({ length: VISUAL_COUNT }).map((_, index) => {
        const sceneModel = videoModelMasterSqlS1E00.scenes[index];
        let from = panelsStart;
        for (let j = 0; j < index; j++) from += visualDur(j);
        const leoIdx = leoIndexByVisual[index];
        const leoSceneModel = leoIdx != null ? videoModelMasterSqlS1E00.scenes[leoIdx] : undefined;
        const leoStart = leoIdx != null ? durations[index] : undefined; // Leo's clip begins when Mnemosyne's ends
        return (
          <Sequence key={sceneModel.id} from={from} durationInFrames={visualDur(index)}>
            <DoodleScene scene={SCENES[index]} sceneModel={sceneModel} modelId={videoModelMasterSqlS1E00.id} voiceId={voiceId || NARRATOR_VOICE_ID} durationInFrames={visualDur(index)} leoStart={leoStart} leoSceneModel={leoSceneModel} />
          </Sequence>
        );
      })}

      <Sequence key="outro" from={outroStart} durationInFrames={OUTRO_FRAMES}>
        <YouTubeOutro hook="New hands-on data lessons!" hookAccent="hands-on" />
      </Sequence>
    </>
  );
};
