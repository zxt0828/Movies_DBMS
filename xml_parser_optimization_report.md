# XML Parser Optimization Report

## CS122B Project 3 - Task 7

### Optimization 1: Batch Inserts (JDBC Batch Processing)

**Description:**
Instead of executing individual INSERT statements for each record, we accumulate records in memory
and execute them in batches of 500 using JDBC's `addBatch()` / `executeBatch()` API. Auto-commit is
also disabled, and we manually commit after each batch flush.

**How it works:**
- Records are added to in-memory ArrayLists (e.g., `movieBatch`, `starBatch`)
- When the list reaches BATCH_SIZE (500), all records are flushed to the database in one batch
- `conn.setAutoCommit(false)` prevents per-statement commits

**Expected time reduction:**
Reduces the number of round-trips to the database by a factor of ~500x. Typical improvement from
naive single-insert approach is 5-10x faster overall.

---

### Optimization 2: In-Memory HashSets/HashMaps for Duplicate Detection

**Description:**
Before parsing XML, we load all existing movie IDs, star names, genre names, and relationship records
into in-memory `HashSet` and `HashMap` structures. During parsing, we check these in-memory data
structures to detect duplicates instead of querying the database for each potential duplicate.

**How it works:**
- `existingMovieKeys`: HashMap mapping "title|year|director" → movieId
- `existingStarNames`: HashMap mapping starName → starId
- `existingGenres`: HashMap mapping genreName → genreId
- `existingStarsInMovies` and `existingGenresInMovies`: HashSets for relationship dedup

**Expected time reduction:**
Eliminates thousands of SELECT queries during parsing. For ~12,000 XML movies and ~60,000 cast
entries, this avoids 70,000+ individual database lookups. Typical improvement is 10-20x faster
compared to querying the database for each record.

---

### Combined Effect

With both optimizations applied together, the XML parsing and import process is expected to
complete in under 30 seconds, compared to potentially 10+ minutes with a naive implementation.
