# 4. Drop short-deck support

Date: 2026-07-10

## Status

Accepted.

## Context

The solver played two games: standard 52-card Texas hold'em, and short-deck (six-plus) hold'em, which
removes the deuces through fives and reorders two pairs of hand categories — a flush beats a full
house, and trips beat a straight.

Supporting the second one cost more than the eleven lines of `PokerVariant` that named it. Every
component that touched a card had to be parameterized over "which game", and the parameter threaded
all the way down:

- `PokerVariant` (deck's lowest rank, category ordering) and `HandEvaluator.forVariant(...)`, with
  `RankTables` sized and shifted per variant and `TableHandEvaluator` carrying a `rankOffset` applied
  on every one of the four suit extractions.
- `Deck`, constructed from a YAML rank/suit list so it could be 36 cards instead of 52 — which meant
  `Config` existed to read that list, which meant a snakeyaml dependency, which meant every scenario
  needed a YAML file even though the only thing the solver read from it was a path to a tree.
- `deck` passed as a parameter through `GameTree`, `GameTreeBuilder`, `GameTreeJsonLoader`,
  `ChanceNode`, `SolverConfig`, and `AbstractCfrSolver`, purely so the chance node knew how many
  cards to deal.
- `GameType` in the API, `SolveRequest.game`, and a game selector in the web UI, which then had to
  thread a `ranks: string[]` prop through `BoardPicker`, `RangeGrid`, `RangeEditor`, and
  `StrategyExplorer` so each could render 9×9 or 13×13.

Nobody was asking for short-deck. The project is named after the game it is actually for.

## Decision

Remove short-deck. Then remove the abstractions that only existed to support it.

The deck is a constant: `Deck.cards()` returns all 52 cards in card-id order, so a chance node's
children are index-aligned with it and the card dealt down edge `i` is `Deck.card(i)`. Nothing needs
to be told what deck it is playing with.

That single fact collapses the rest:

| before | after |
|---|---|
| `HandEvaluator` interface + `TableHandEvaluator` + `PokerVariant` | one `HandEvaluator` with static methods |
| `RankTables(variant)`, instance fields, per-variant `EnumMap` cache | `RankTables` static tables, built once |
| `HandCategory.strengthOf(category)` per variant | `HandCategory.strength()`, fixed |
| `Config` + snakeyaml + 8 YAML files | none; the CLI takes `--tree <path>` |
| `deck` threaded through 6 classes | `Deck.cards()` where needed |
| `GameType`, `SolveRequest.game` | gone |
| `ranks: string[]` prop through 4 React components | `RANKS` imported where used |

`TableHandEvaluator.rank` loses its `>>> rankOffset` on each of the four `Long.compress` results, and
`HandEvaluator` loses its virtual dispatch: `rank(long)` is now a static call into static final
tables. This is *not* a performance change — JMH measures 27.0 ns/op against 26.3 ns before, within
noise, because C2 had already devirtualized the only implementation. The point is the deleted code,
not the deleted indirection.

## Consequences

Hold'em behaviour is unchanged. The CLI, run on the same tree, board and range as before the removal,
produces the same strategy to four decimal places — the hold'em YAML and the short-deck YAML pointed
at the *same* tree file, so only the deck differed.

`HandEvaluatorGoldenTest` still asserts all 2,598,960 dictionary rows reproduce exactly, so the
de-parameterized `RankTables` is pinned as strictly as the parameterized one was.

`StrategyRegressionTest`'s golden values were regenerated: the scenario's tree, board and range are
unchanged, but it used to solve under short-deck rules, where a flush outranks a full house.

The chance node's children now follow card-id order (`2c, 2d, 2h, 2s, 3c, …`) rather than the YAML's
rank-major order (`Ah, As, Ad, Ac, Kh, …`). Every card is still dealt exactly once, so this changes
only the order in which a turn or flop solve sums its chance-node utilities — a last-bit difference in
floating-point accumulation, not a semantic one. The strategy regression pins a river solve, which has
no chance nodes.

Removed: `PokerVariant`, `TableHandEvaluator`, `Config`, `GameType`, `SolveRequest.game`,
`GameTree.getDeck()`, `SolverConfig.deck`, the snakeyaml dependency, eight YAML rule files, the
7.4 MB short-deck dictionary, and the web UI's game selector and `Segmented` component.

## Alternatives considered

- **Keep `PokerVariant` with one constant, for future variants.** An abstraction with one
  implementation is a guess about the future paid for in the present. Omaha or draw variants would
  need a different hand-size and card-count model anyway, not another entry in this enum — the
  evaluator's whole two-path structure assumes exactly five to seven cards from a 52-bit mask.
- **Keep `Config` and YAML for the tree path.** The YAML declared a deck, a compairer, a card
  sampler, and a full `rule:` block for the upstream Python tree builder. Java read two keys from it,
  then one, then none. A `--tree` flag says what it means.
