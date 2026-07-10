# 2. Derive hand ranks from the rules instead of loading a dictionary

Date: 2026-07-10

## Status

Accepted. Supersedes the `pokersolver.compairer` package, which is removed.

## Context

Hand evaluation sat behind `Compairer`, whose only implementation, `Dic5Compairer`, read a
comma-separated dictionary of all 2,598,960 five-card hands (52 MB) at startup. Each line mapped a
hand to its rank. Ranking a seven-card hand meant taking the best of its 21 five-card subsets — 21
probes into a 2.6M-entry open-addressed hash map, each one a likely cache miss into a 50 MB table.

This cost was structural, not incidental:

- **Startup.** 1139 ms to parse and index the hold'em dictionary, paid on every process start and
  every test JVM.
- **Memory.** ~100 MB retained. Gradle's test workers needed `maxHeapSize = "2g"` for this reason
  alone.
- **Throughput.** 56 ns per seven-card rank. A flop solve projects ~1300 hands onto each of 1081
  river boards, twice — roughly 2.8M evaluations before any regret is computed.
- **Distribution.** The data file had to ship with the JAR and be found at runtime, which is why the
  CLI took a `--config` naming a `dicfile` and the server took `--resources`.

The dictionary was also opaque. Nothing in the repository explained where its numbers came from.

## Decision

Derive the rank tables from the rules of the game at class-initialization time, in a new
`pokersolver.eval` package. There is no data file.

A hand of five to seven cards resolves down one of two disjoint paths:

- **Flush.** If any suit holds five or more cards, the best five cards are all of that suit. (Quads
  or a full house alongside a five-card suit would need at least eight cards.) A 13-bit rank bitmask
  per suit indexes `flushRanks` directly.
- **Everything else.** With no five-card suit, no five-card subset is a flush either, so the hand is
  determined entirely by how many cards it holds of each rank. That count vector is mapped through a
  minimal perfect hash — the combinatorial number system generalized from sets to multisets — into
  `nonFlushRanks`.

`Long.compress` splits the 52-bit card mask into the four suit bitmasks in one instruction where the
hardware offers `PEXT`.

The dense rank is unchanged: `1` is a royal flush, `7462` is seven-high, larger is weaker, equal ranks
tie. It reproduces the dictionary's numbering exactly, so nothing downstream needed to change.

## Consequences

Measured on an M-series Mac, JDK 25:

| | dictionary | derived | |
|---|---|---|---|
| startup | 1139 ms | 22.9 ms | **50× faster** |
| retained heap | ~100 MB | ~150 KB of tables | **~300× smaller** |
| 7-card rank | 55.96 ns | 26.28 ns | **2.1× faster** |
| data file shipped | 52 MB | none | |

A river solve of 20 iterations went from 2.50 ms to 1.97 ms end to end; the evaluator's share is
amortized there because a river board is projected once. Turn and flop solves, which project 47 and
1081 boards, gain proportionally more.

Downstream simplifications:

- `ApiServer` no longer takes `--resources`; `GameResources`, which lazily cached dictionaries, is
  deleted.
- `SolverConfig` no longer takes a `compairer`.
- Test workers no longer need a 2 GB heap.
- `LongIntHashMap`, written for the dictionary, is deleted.

The dictionary stays in `riversolver/src/test/resources/compairer/` as golden data.
`HandEvaluatorGoldenTest` asserts the generated tables reproduce **every one of its 2,598,960 rows**,
value for value — not merely inducing the same ordering — and checks the six- and seven-card paths
against the five-card path by brute force. That test is the reason this change could be made at all,
and it must keep running.

## Alternatives considered

- **A two-plus-two 7-card lookup table** (32.5M ints, 130 MB) is the fastest known evaluator, one
  array read per card. It trades the problem we have — a large table read from disk — for the same
  problem, larger.
- **Keeping the dictionary and caching seven-card results.** The range cache already does this per
  board; the remaining cost is the cold path, which is what hurts.
- **A published evaluator library** (e.g. a JNI binding to a C implementation). Adds a native
  dependency and a build-time toolchain to save ~10 ns.
