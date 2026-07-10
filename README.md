# TexasHoldemSolverJava

[![license](https://img.shields.io/github/license/bupticybee/TexasHoldemSolverJava?style=flat-square)](LICENSE)

README [English](README.md) | [中文](README.zh-CN.md)

> **About this fork.** This is a modernized fork of [bupticybee's original
> TexasHoldemSolverJava](https://github.com/bupticybee/TexasHoldemSolverJava) (no longer
> maintained upstream). It runs on Java 25 with a Gradle multi-module build; the Swing GUI and
> JPype Python bridge have been replaced by a browser web UI over an embedded HTTP API
> ([ADR 0001](docs/adr/0001-unified-http-api-replaces-swing-and-jpype.md)); hand ranks are
> derived from the rules of the game rather than loaded from a 52 MB dictionary
> ([ADR 0002](docs/adr/0002-derive-hand-ranks-instead-of-loading-a-dictionary.md)); and the CFR
> hot loops are SIMD-vectorized with the Java Vector API. For a faster native solver, see the
> C++ port [TexasSolver](https://github.com/bupticybee/TexasSolver).

## Introduction

An open-source, efficient solver for Texas Hold'em. Like commercial solvers such as piosolver, it
focuses on **post-flop** play, and its results align with piosolver. On the river it is faster than
piosolver; on the flop it is slower.

Built around Counterfactual Regret Minimization (CFR): it constructs the post-flop game tree,
runs a CFR variant until the strategy's exploitability converges, and serializes the resulting
mixed strategy at every node to JSON. See [CONTEXT.md](CONTEXT.md) for the domain glossary.

This project is suitable for:

- high-level Texas Hold'em players
- researchers in imperfect-information games

## Features

- Efficient — river and turn solving comparable to or faster than piosolver
- Accurate — results closely match piosolver
- Fully open source and free (MIT)
- Browser web UI and a language-agnostic HTTP/JSON API (with live SSE convergence streaming)
- Six selectable CFR variants — `discounted_cfr` (default), `pdcfr`, `pdcfr_plus`, `pcfr_plus`,
  `cfr_plus`, `cfr` — with [measured convergence](docs/adr/0003-discounted-cfr-remains-the-default.md)
- SIMD-vectorized CFR hot loops (`jdk.incubator.vector`)
- Perfect-hash hand evaluator with no data files: 23 ms to build its tables, ~150 KB resident

## Requirements

- **JDK 25** (the build targets Java 25 and uses the incubating Vector API)
- The Gradle wrapper (`./gradlew`) is included — no separate Gradle install needed
- Node is fetched automatically by the build for the `web-ui` module

Nothing else is needed. The hand evaluator derives its rank tables from the rules of the game at
startup, so there are no data files to download or ship.

## Build & run

### Web UI (recommended)

Start the embedded server — it serves both the HTTP API and the bundled web UI — then open
<http://localhost:8080>:

```bash
./gradlew :solver-api:run --args="--port 8080"
```

The UI provides a range editor, board picker, live convergence chart, and a strategy explorer.

### HTTP API

The same server exposes a JSON API for solving from any language. Endpoints, request fields,
and `curl` examples are documented in [solver-api/README.md](solver-api/README.md):

```bash
curl -s -X POST localhost:8080/api/v1/solves -H 'Content-Type: application/json' -d '{
  "board": "Kd,Jd,Td,7s,8s",
  "rangeIp":  "AA,KK,QQ,JJ,TT,99,88,AK,AQ,KQ,JT",
  "rangeOop": "AA,KK,QQ,JJ,TT,99,88,AK,AQ,KQ,JT",
  "pot": 10, "effectiveStack": 95, "iterations": 100, "stopExploitability": 0.5
}'
```

### Command line

The CLI takes a game-tree JSON file plus ranges/board/iterations and writes a strategy JSON:

```bash
./gradlew :riversolver:installDist
cd riversolver
./build/install/RiverSolver/bin/RiverSolver \
  --tree src/test/resources/gametree/simple_part_tree_depthinf.km \
  -p1 "AA,KK,QQ,JJ,TT,99,AK,AQ,KQ,JT" \
  -p2 "AA,KK,QQ,JJ,TT,99,AK,AQ,KQ,JT" \
  -b "Kd,Jd,Td,7s,8s" -n 100 -i 10 \
  -a discounted_cfr -o /tmp/strategy.json
```

> Running an `installDist` binary requires `JAVA_HOME` to point at a JDK 25 install.

## Modules

| Module | What it is |
| --- | --- |
| `riversolver` | The solver core: game-tree builder, CFR solvers, hand evaluator, CLI |
| `solver-api` | Embedded HTTP/JSON API (Javalin on virtual threads); serves the web UI |
| `web-ui` | React + TypeScript front end, bundled into `solver-api` at build time |
| `benchmarks` | JMH benchmarks for the hand evaluator, tree building, and solving |

## Reading the solver's output

While solving, the solver logs exploitability per iteration (in % of the pot):

```text
Iter: 0   Total exploitability 47.49 percent
Iter: 11  Total exploitability  4.53 percent
Iter: 41  Total exploitability  0.68 percent
```

Watch how exploitability converges — a strategy below ~0.5% pot is generally good enough to
treat as optimal.

The strategy JSON maps each node to the actions considered and, per hand, the mixed strategy
over those actions:

![strategy](img/strategy2.png)

For example, with `qd7c` the optimal play might be check 34% / bet 65%.

![strategy detail](img/strategy3.png)

## Benchmarks

Performance is guarded by the JMH `benchmarks` module:

```bash
./gradlew :benchmarks:jmh -PjmhIncludes='RiverSolveBenchmark'
```

See [benchmarks/README.md](benchmarks/README.md) for the available benchmarks. For historical
reference, the original per-street comparison against piosolver (turn/river comparable, flop
slower due to tree size):

|                       | flop  | turn  | river |
| --------------------- | ----- | ----- | ----- |
| piosolver             | 7.91s | 1.5s  | 0.56s |
| TexasHoldemSolverJava | 98s   | 4.21s | 0.06s |

## Algorithm

The solver builds the post-flop game tree, runs a CFR variant until the strategy's exploitability
converges, and serializes the resulting mixed strategy at every action node.

Six variants ship. Exploitability (percentage of the pot, lower is better) after 200 single-threaded
iterations, from `AlgorithmBakeoff`:

|                                                               | river (wide) | river (broadway) | turn       |
| ------------------------------------------------------------- | ------------ | ---------------- | ---------- |
| `cfr` — vanilla CFR (Zinkevich 2007)                          | 0.0413       | 0.2196           | 1.506      |
| `cfr_plus` — regret-matching⁺ (Tammelin 2014)                 | 0.0099       | 0.0100           | 0.204      |
| `pcfr_plus` — predictive CFR+ (Farina 2021)                   | 0.0103       | 0.0251           | 0.137      |
| `pdcfr_plus` — predictive discounted CFR+ (Xu 2024)           | 0.0381       | 0.0213           | 0.209      |
| `pdcfr` — predictive discounted CFR (Xu 2024)                 | 0.0089       | 0.0174           | 0.0714     |
| **`discounted_cfr`** — discounted CFR (Brown & Sandholm 2019) | **0.0013**   | **0.0051**       | **0.0400** |

Discounted CFR wins every scenario measured, so it is the default. The optimistic variants lead on
matrix games and trail here, which their own papers predict. See
[ADR 0003](docs/adr/0003-discounted-cfr-remains-the-default.md) for the full table and the reasoning.

Hand ranking is a perfect-hash evaluator generated from the rules of the game — a 7-card rank is four
bit-gathers and two array reads into ~150 KB of tables, with no data file to load. See
[ADR 0002](docs/adr/0002-derive-hand-ranks-instead-of-loading-a-dictionary.md). Short-deck support was
removed in [ADR 0004](docs/adr/0004-drop-short-deck-support.md).

![algorithms](img/algs.png)

## C++ version

If the Java version is not fast enough, there is a native [C++ port,
TexasSolver](https://github.com/bupticybee/TexasSolver), which is faster on turn and river and
roughly 5x faster on the flop.

## License

[MIT](LICENSE) © bupticybee

## Contact

icybee@yeah.net
