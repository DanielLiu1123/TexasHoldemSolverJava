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
| `HandRankBenchmark` | `Dic5Compairer.get_rank` lookup — the inner loop of showdown evaluation |
| `TreeBuildingBenchmark` | Full game-tree construction (turn and river trees) |
| `RiverSolveBenchmark` | Fixed 20-iteration river solve, single-threaded vs parallel CFR+ |
| `TurnSolveBenchmark` | Fixed 10-iteration parallel turn solve (chance-node fan-out) |

The compairer dictionary is loaded from `riversolver/src/test/resources` via the
`solver.testResources` system property, wired in `build.gradle.kts` — run the benchmarks
through Gradle, not a bare JMH jar.

The legacy `benchmark_*.txt` files are the original piosolver comparison scenario exports;
they are kept as documentation of the scenario parameters.
