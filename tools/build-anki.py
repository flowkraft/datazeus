#!/usr/bin/env python3
"""
build-anki.py — turn a lesson's cards.yaml (OUR own format, NOT CrowdAnki) into an
Anki .apkg using genanki.

    pip install genanki pyyaml
    python tools/build-anki.py courses/master-sql/series-1-fundamentals/00-write-your-first-query
    python tools/build-anki.py            # build every lesson that has cards/cards.yaml

cards.yaml schema (see any lesson's cards/cards.yaml):
    deck:  "DataZeus::...::Lesson"     # deck name ("::" = sub-deck)
    guid:  "stable-id"                 # STABLE so rebuilds update, not duplicate
    tags:  ["datazeus", ...]           # applied to every card
    cards:
      - basic: { front: "...", back: "..." }
      - cloze: "... {{c1::hidden}} ..."

Output: <lesson>/cards/<guid>.apkg  (a BUILD ARTIFACT — gitignored, CI-built).
"""
import sys, glob, hashlib, pathlib
try:
    import yaml, genanki
except ImportError:
    sys.exit("Missing deps. Run:  pip install genanki pyyaml")

REPO = pathlib.Path(__file__).resolve().parent.parent

# Stable model ids derived from fixed strings → deterministic rebuilds (no duplicates).
def _id(s: str) -> int:
    return int(hashlib.sha256(s.encode()).hexdigest()[:8], 16)

BASIC_MODEL = genanki.Model(
    _id("datazeus-basic"), "DataZeus Basic",
    fields=[{"name": "Front"}, {"name": "Back"}],
    templates=[{"name": "Card 1",
                "qfmt": "{{Front}}",
                "afmt": "{{FrontSide}}<hr id=answer>{{Back}}"}],
)
CLOZE_MODEL = genanki.Model(
    _id("datazeus-cloze"), "DataZeus Cloze",
    model_type=genanki.Model.CLOZE,
    fields=[{"name": "Text"}],
    templates=[{"name": "Cloze", "qfmt": "{{cloze:Text}}", "afmt": "{{cloze:Text}}"}],
)

def build(lesson_dir: pathlib.Path) -> None:
    spec = yaml.safe_load((lesson_dir / "cards" / "cards.yaml").read_text(encoding="utf-8"))
    guid = spec["guid"]
    deck = genanki.Deck(_id(guid), spec["deck"])
    tags = spec.get("tags", [])
    for card in spec.get("cards", []):
        if "cloze" in card:
            deck.add_note(genanki.Note(model=CLOZE_MODEL, fields=[card["cloze"]], tags=tags))
        elif "basic" in card:
            b = card["basic"]
            deck.add_note(genanki.Note(model=BASIC_MODEL, fields=[b["front"], b["back"]], tags=tags))
    out = lesson_dir / "cards" / f"{guid}.apkg"
    genanki.Package(deck).write_to_file(out)
    print(f"  built {out.relative_to(REPO)}  ({len(spec.get('cards', []))} cards)")

def main() -> None:
    if len(sys.argv) > 1:
        targets = [REPO / sys.argv[1]]
    else:
        targets = [pathlib.Path(p).parent.parent for p in glob.glob(str(REPO / "courses/master-*/**/cards/cards.yaml"), recursive=True)]
    if not targets:
        sys.exit("No lessons with cards/cards.yaml found.")
    for t in targets:
        print(f"Lesson: {t.relative_to(REPO)}")
        build(t)

if __name__ == "__main__":
    main()
