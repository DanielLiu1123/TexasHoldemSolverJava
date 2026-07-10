# Benchmarks

JMH benchmarks for the solver core. The solve scenario mirrors the historical piosolver
comparison runs recorded in `benchmark_*.txt` (pot 180, effective stacks 910, 50%-pot
bets/raises, all-in on turn and river, board Qs Jh 2h 2d).

## Run

```bash
# Everything (slow — includes turn solves)
./gradlew :benchmarks:jmh

# A subset, by regex
./gradlew :benchmarks:jmh -PjmhIncludes='RiverSolve.*'
```

Results are written to `benchmarks/build/results/jmh/results.json` (JSON, suitable for
comparison across commits).

## Benchmarks

| Class | Measures |
| --- | --- |
| `HandRankBenchmark` | `HandEvaluator.rank` — the inner loop of showdown evaluation |
| `TreeBuildingBenchmark` | Full game-tree construction (turn and river trees) |
| `RiverSolveBenchmark` | Fixed 20-iteration river solve, single-threaded vs parallel CFR+ |
| `TurnSolveBenchmark` | Fixed 10-iteration parallel turn solve (chance-node fan-out) |

`AlgorithmBakeoff` (in `riversolver`'s test sources, `@Disabled`) is the companion measurement
for the CFR variants: it prints exploitability per variant per scenario, and is what
[ADR 0003](../docs/adr/0003-discounted-cfr-remains-the-default.md) is based on.

The legacy `benchmark_*.txt` files are the original piosolver comparison scenario exports;
they are kept as documentation of the scenario parameters.
