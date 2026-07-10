# Texas Hold'em Solver

[![license](https://img.shields.io/github/license/DanielLiu1123/TexasHoldemSolverJava?style=flat-square)](LICENSE)
[![build](https://img.shields.io/github/actions/workflow/status/DanielLiu1123/TexasHoldemSolverJava/build.yml?style=flat-square)](.github/workflows/build.yml)

README [English](README.md) | [中文](README.zh-CN.md)

A post-flop solver for heads-up Texas hold'em, built around Counterfactual Regret Minimization.
It constructs the game tree for a spot, runs a CFR variant until the strategy's exploitability
converges, and serializes the resulting mixed strategy at every decision node.

Runs on Java 25. No data files, no native dependencies, no install step beyond the Gradle wrapper.

## Features

- **Browser web UI** and a language-agnostic **HTTP/JSON API**, with live convergence streamed over
  SSE ([ADR 0001](docs/adr/0001-unified-http-api-replaces-swing-and-jpype.md))
- **Six CFR variants** — `discounted_cfr` (default), `pdcfr`, `pdcfr_plus`, `pcfr_plus`, `cfr_plus`,
  `cfr` — chosen by [measurement, not by paper date](docs/adr/0003-discounted-cfr-remains-the-default.md)
- **Perfect-hash hand evaluator** derived from the rules of the game: ~150 KB of tables built in
  23 ms, no dictionary to load ([ADR 0002](docs/adr/0002-derive-hand-ranks-instead-of-loading-a-dictionary.md))
- **SIMD-vectorized** CFR hot loops (`jdk.incubator.vector`)
- Exploitability measured directly against a best response, so convergence is a number, not a hope

See [CONTEXT.md](CONTEXT.md) for the domain glossary and [docs/adr](docs/adr) for the decisions
behind the design.

## Requirements

- **JDK 25** — the build targets Java 25 and uses the incubating Vector API
- The Gradle wrapper (`./gradlew`) is included; no separate Gradle install
- Node is fetched automatically by the build for the `web-ui` module

## Build & run

### Web UI

Start the embedded server — it serves both the HTTP API and the bundled web UI — then open
<http://localhost:8080>:

```bash
./gradlew :solver-api:run --args="--port 8080"
```

The UI provides a range editor, board picker, live convergence chart, and a strategy explorer.

### HTTP API

The same server exposes a JSON API for solving from any language. Endpoints and request fields are
documented in [solver-api/README.md](solver-api/README.md).

```bash
curl -s -X POST localhost:8080/api/v1/solves -H 'Content-Type: application/json' -d '{
  "board": "Kd,Jd,Td,7s,8s",
  "rangeIp":  "AA,KK,QQ,JJ,TT,99,88,AK,AQ,KQ,JT",
  "rangeOop": "AA,KK,QQ,JJ,TT,99,88,AK,AQ,KQ,JT",
  "pot": 10, "effectiveStack": 95, "iterations": 100, "stopExploitability": 0.5
}'
```

### Command line

The CLI takes a game-tree JSON file plus ranges, board and iteration count, and writes the strategy
as JSON:

```bash
./gradlew :solver-core:installDist
cd solver-core
./build/install/poker-solver/bin/poker-solver \
  --tree src/test/resources/gametree/river-tree.json \
  -p1 "AA,KK,QQ,JJ,TT,99,AK,AQ,KQ,JT" \
  -p2 "AA,KK,QQ,JJ,TT,99,AK,AQ,KQ,JT" \
  -b "Kd,Jd,Td,7s,8s" -n 100 -i 10 \
  -a discounted_cfr -o /tmp/strategy.json
```

> Running an `installDist` binary requires `JAVA_HOME` to point at a JDK 25 install.

## Modules

| Module | What it is |
| --- | --- |
| `solver-core` | Game-tree builder, CFR solvers, hand evaluator, CLI |
| `solver-api` | Embedded HTTP/JSON API (Javalin on virtual threads); serves the web UI |
| `web-ui` | React + TypeScript front end, bundled into `solver-api` at build time |
| `benchmarks` | JMH benchmarks for the hand evaluator, tree building, and solving |

## Reading the output

While solving, exploitability is logged every `-i` iterations, as a percentage of the pot:

```text
iteration 0:  exploitability 20.737505% of pot
iteration 21: exploitability 0.191139% of pot (14 ms)
iteration 41: exploitability 0.056625% of pot (9 ms)
```

Exploitability is what a best-responding opponent could win against the current strategy. It reaches
zero exactly at a Nash equilibrium; below ~0.5% of the pot is generally good enough to treat as
optimal.

The strategy JSON maps each action node to the actions available and, per hand, the mixed strategy
over them:

```json
{
  "nodeType": "action_node",
  "player": 1,
  "actions": ["CHECK", "BET 2.0"],
  "strategy": {
    "strategy": {
      "AsAh": [0.9961, 0.0039],
      "JsTs": [0.6416, 0.3584]
    }
  }
}
```

So with `JsTs` the equilibrium play is to check 64% and bet 36%.

## Algorithm

Six CFR variants ship. Exploitability (percentage of the pot, lower is better) after 200
single-threaded iterations, measured by `AlgorithmBakeoff`:

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
[ADR 0003](docs/adr/0003-discounted-cfr-remains-the-default.md) for the reasoning.

Hand ranking is a perfect-hash evaluator generated from the rules of the game: a seven-card rank is
four bit-gathers, a popcount and two array reads into ~150 KB of tables, with no data file to load
([ADR 0002](docs/adr/0002-derive-hand-ranks-instead-of-loading-a-dictionary.md)).

Only 52-card hold'em is supported ([ADR 0004](docs/adr/0004-drop-short-deck-support.md)).

## Benchmarks

Performance is guarded by the JMH `benchmarks` module:

```bash
./gradlew :benchmarks:jmh -PjmhIncludes='RiverSolveBenchmark'
```

Measured on an M-series Mac, JDK 25:

| | |
| --- | --- |
| 7-card hand rank | 26.3 ns |
| river solve, 20 iterations, single-threaded | 1.97 ms |
| evaluator table construction (once, at startup) | 22.9 ms |

See [benchmarks/README.md](benchmarks/README.md) for the full list.

## Contributing

Issues and pull requests are welcome. `./gradlew build` runs the full check: tests, Spotless,
Error Prone and NullAway. Architectural decisions are recorded in [docs/adr](docs/adr) — if a change
overturns one, add the next ADR rather than editing the old one.

## License

[MIT](LICENSE).

## Acknowledgements

This project began as a fork of [TexasHoldemSolverJava](https://github.com/bupticybee/TexasHoldemSolverJava)
by bupticybee, which is no longer maintained. The solver core has since been substantially rewritten;
the original copyright is retained in [LICENSE](LICENSE) as the MIT license requires.
