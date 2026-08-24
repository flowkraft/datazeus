# The Riverside Library — the brief

> **This is the greenfield domain for Data Modeling Series 2.** You design this one from
> nothing. Northwind was the model you *recovered*; this is the model you *invent*.

---

## What they told you

Riverside is a small independent lending library. Two staff, about four thousand books,
roughly six hundred members. They have run the whole thing on one spreadsheet since 2019 and
it has stopped coping — the file is in `lending-log.csv` and you should open it before you
read any further.

Here is what the librarian said, in her words, over about ten minutes:

> "People join, they pay a membership each year, and then they borrow books. Three weeks a
> loan, and they can renew once if nobody else is waiting. We charge 20p a day if it comes
> back late, but honestly we waive it half the time.
>
> We've got a few copies of the popular ones — four of the Dune, I think, maybe five. The
> children's section is by age band and the rest is by subject, and some of the subjects have
> sub-subjects, like History has Local History under it.
>
> Some books have two or three authors. A couple of the older ones don't have an ISBN at all,
> and the reprints have got a different ISBN from the original even though it's the same
> book, which has caused us no end of confusion.
>
> Oh — and we want to start doing ebooks next year, but that's not urgent."

That last sentence is the most expensive thing in the document. Do not act on it yet.

## What you have to produce

1. **An ER diagram**, authored as PlantUML in the DataPallas **ER Diagram** tab.
2. **A `schema.sql`** in your practice schema, with the constraints declared inline.
3. **A green acceptance suite** — `zeus koans datamodeling series2 _05`.

Write the diagram *before* the DDL, and commit to it before you look at ours. Seeing our
answer first costs you the entire exercise; there is no way to un-see a model.

---

## For whoever writes these lessons — do not paste this half into the learner's view

### The ambiguities are planted. Leave them planted.

The brief above is *deliberately* incomplete, in the specific way real briefs are incomplete:
it is confident, it is detailed, and it does not answer the questions that decide the model.
Episode 00 (`requirements-to-entities`) is about noticing that. If a future edit "tidies" the
brief by answering these, the episode has nothing left to teach.

The planted gaps, and what each one forces:

| The question the brief does not answer | What it decides |
|---|---|
| Can one member hold two copies of the *same* title at once? | whether the loan's uniqueness is per copy or per (member, title) |
| Is a renewal a new loan, or a changed due date on the old one? | whether loan history is preserved at all |
| Does a waived fine become zero, or stay recorded as waived? | whether "waived" is a state or an absence — the classic NULL-vs-value call |
| Do memberships lapse and restart, or run continuously? | whether membership is an attribute or its own dated entity |
| Can a sub-subject have its own sub-subject? | fixed two levels vs a real hierarchy (pays off in episode 20) |
| Is an ISBN-13 reprint the same book or a different one? | the whole title/edition/copy question |

**The correct learner behaviour is to write these down as questions, not to guess.** That is
the deliverable of episode 00, and it should be graded as such — a learner who silently picks
an answer has done the thing this episode exists to prevent.

### The traps, in the order they detonate

- **Title vs copy (episode 10).** Nearly everyone models one `book` table. It survives the
  first design review and then dies on "how many copies of Dune do we own?" and "who has copy
  #3?" — both unanswerable at once. Do **not** rescue them in episode 05, however tempting.
  The bill has to come due on their own schema or the lesson does not land.
- **ISBN as a natural key (episode 10).** It looks flawless. Reprints get a new one, ISBN-10
  meets ISBN-13, and two books in `lending-log.csv` have none at all. This is the single best
  argument for surrogate keys that exists, and the library was chosen partly to get it.
- **`author2` (episode 00).** A repeating group, visible in the data, discovered rather than
  taught. Leave the column named exactly that.
- **The blank `returned` column (episode 00 / 25).** Blank means *still out*, not *unknown*.
  Real optionality with real meaning — the counter-example to "NULL is always a smell".
- **`notes` (episode 42).** Free text, and different for almost every row that has it —
  translator, original language, "large print", "board book", "reference only", series
  position. Model every one of those as a column and you get a table that is mostly NULL,
  which is episode 37's failure arriving from the other direction. This column is the
  argument for a JSON blob, and it is in the data rather than in the prose on purpose.
- **`member_status` = `left`, and `last_edited` / `edited_by` (episode 27).** Jonah
  Whitfield left the library, and his two loans are still in the file. That is a soft delete
  performed by hand in a spreadsheet: the row is still there, and every count of "our
  members" is now wrong unless you remember to filter. `edited_by` holds initials — audit
  columns as they actually appear in the wild, added by someone who needed them, never
  designed.
- **The film tie-in `Dune` (episode 10).** ISBN `9780593099322` on the last row, against
  `9780441013593` everywhere else. Same book, same author, different ISBN, and it is copy 5
  of the same four-copy set the library already owns. If ISBN is the primary key, the library
  now owns two different books called Dune. This is the ISBN trap in a single row of data.
- **Ebooks (episode 60).** The librarian's throwaway line is the project. An ebook has no
  physical copy, so it detonates the copy model the learner spent the whole series getting
  right. That is deliberate and must not be softened: the lesson is not "avoid the mistake" —
  no model survives every change — it is *"here is an additive migration, and here is a
  rewrite, and here is how you can tell which one you just wrote."*

### The third artifact

The acceptance suite (`DesignTheLibraryKoans`) is the part that makes this a design exercise
rather than an essay. It is a definition of done that is **not** an answer key: it says what
must be impossible, what must stay possible, and which questions the model must be able to
answer — and says nothing at all about how many tables to use or what to call them.

Any model that passes it is a correct answer, including ones we did not think of. That is the
property to protect when adding to it: if a new koan can only pass on *our* table names, it is
testing conformance rather than modeling, and it should be rewritten or dropped.
