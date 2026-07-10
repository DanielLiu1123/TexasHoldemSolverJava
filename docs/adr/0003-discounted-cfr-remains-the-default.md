# 3. Discounted CFR remains the default, and the trainables are corrected

Date: 2026-07-10

## Status

Accepted.

## Context

The solver offered four CFR variants. Reading them against their papers turned up three defects, each
of which had been invisible because nothing measured what the algorithms were supposed to produce.

**Vanilla CFR was serializing a strategy that does not converge.** `CfrTrainable.strategyForDump()`
returned the *current* strategy. Only the *average* strategy of vanilla CFR converges to a Nash
equilibrium; the current strategy cycles around it. This is why the old regression test's golden
values for `cfr` were pure strategies (1.0 / 0.0) and why its exploitability ceiling was 0.6% of the
pot — thirty times worse than the other variants, and treated as expected.

**CFR+ was averaging regrets rather than the strategies they induce.**

```java
this.cumRPlus[index] += this.rPlus[index] * iterationNumber;   // R⁺ · t
```

CFR+ (Tammelin 2014) prescribes `C^t = C^{t-1} + t · π^t · σ^t` — the strategy, reach-weighted.
Accumulating unnormalized R⁺ instead makes `getAverageStrategy()` meaningless. It went unnoticed
because `strategyForDump()` also returned the current strategy, so the average was never read.

**Discounted CFR discounted the wrong term.** Brown & Sandholm (2019) discount the *accumulator*
before this iteration's regret lands on it, branching on the accumulator's sign:

```
R^T = R^{T-1} · (T-1)^α / ((T-1)^α + 1) + r^T     when R^{T-1} > 0
C^T = C^{T-1} · ((T-1)/T)^γ + π^T · σ^T
```

The code computed `r = R + r; r *= (r > 0 ? alphaCoef : beta)` — discounting the freshest information
along with the stale, and testing the sign of the wrong quantity. Its average-strategy recursion was
an ad-hoc exponential moving average (`cum * 0.9f + σ · (T/(T+1))^γ · π`) with γ on the wrong term.

All four also averaged σ^{t+1} against iteration t's reach probabilities — an off-by-one, since the
strategy that *produced* iteration t's regrets is the one that belongs in the average.

## Decision

Fix all four. Add the two variants from Xu et al. (IJCAI 2024), which combine DCFR's discounting with
PCFR+'s optimistic prediction: `PDCFR_PLUS` (regret-matching⁺ accumulator) and `PDCFR` (signed
accumulator).

Then **measure**, rather than assume the newest paper wins. `AlgorithmBakeoff` reports exploitability
(percentage of the pot, lower is better) after 200 single-threaded iterations:

| | river (narrow) | river (wide) | river (broadway) | turn |
|---|---|---|---|---|
| `cfr` | 0.0465 | 0.0413 | 0.2196 | 1.506 |
| `cfr_plus` | 0.0066 | 0.0099 | 0.0100 | 0.204 |
| `pcfr_plus` | 0.0035 | 0.0103 | 0.0251 | 0.137 |
| `pdcfr_plus` | 0.0027 | 0.0381 | 0.0213 | 0.209 |
| `pdcfr` | 0.0023 | 0.0089 | 0.0174 | 0.0714 |
| **`discounted_cfr`** | **0.0010** | **0.0013** | **0.0051** | **0.0400** |

**Discounted CFR wins every scenario, so it stays the default.**

Two things the table explains:

- `pdcfr` beats `pdcfr_plus` everywhere. Regret-matching⁺ clips the accumulator at zero, which leaves
  DCFR's β nothing to discount — and β = 0, halving negative regret rather than erasing it, is where
  most of DCFR's edge on poker subgames comes from. Keeping the sign recovers it.
- The optimistic variants still trail plain DCFR. Their own papers say as much: PCFR+ leads on matrix
  games, where the regret sequence is smooth enough for the prediction to pay for itself, and trails
  DCFR on poker. Combining optimism with discounting narrows the gap without closing it.

Run out to 800 iterations, the picture is more interesting than the 200-iteration snapshot. On the
wide-range river:

| iterations | 50 | 100 | 200 | 800 |
|---|---|---|---|---|
| `cfr_plus` | 0.1159 | 0.0401 | 0.0099 | 0.00058 |
| `pcfr_plus` | 0.1081 | 0.0379 | 0.0103 | 0.00010 |
| `pdcfr_plus` | 0.0913 | 0.0237 | 0.0381 | 0.00079 |
| `pdcfr` | 0.0687 | 0.0167 | 0.0089 | 0.00012 |
| `discounted_cfr` | 0.0539 | 0.0127 | 0.0013 | 0.000065 |

`pdcfr_plus` rises between 100 and 200 iterations and then resumes falling: optimistic prediction
overshoots on a non-stationary regret sequence. It is oscillation, not divergence. And the optimistic
variants do close most of the gap asymptotically — on the narrow-range river `pcfr_plus` actually
passes DCFR by 800 iterations — which is their tighter regret bound arriving. But a solve runs at
50-200 iterations, not 800, and DCFR leads by 3-6× there.

## Consequences

Every variant's solved strategy changed, so `StrategyRegressionTest`'s golden table was regenerated.

`pcfr_plus`'s golden values came out **bit-identical to the pre-refactor baseline**. It is the one
variant whose semantics this work did not touch — it already dumped the average and accumulated it
correctly. That coincidence is load-bearing evidence: the hand evaluator, the struct-of-arrays range
layout, the rewritten tree builder, and the SIMD kernels are all exactly equivalent to what they
replaced. Only the three bugs moved.

Convergence improved across the board. On the wide-range river at 100 iterations:

| | before | after |
|---|---|---|
| `cfr` | 0.6% ceiling (test) | 0.106% |
| `cfr_plus` | 0.05% ceiling (test) | 0.040% |
| `discounted_cfr` | 0.02% ceiling (test) | 0.0127% |

`SolverConvergenceTest` now asserts exploitability directly, per variant, with ceilings ~40% above
measurement — and asserts the ordering (`cfr_plus < cfr`, `discounted_cfr < cfr_plus`, `pdcfr <
pdcfr_plus`, `discounted_cfr < pdcfr`). A regression in any trainable fails a test that says what
broke, instead of a golden table that says a number changed.

The measurements above were re-taken on the 52-card deck after short-deck support was removed (see
[ADR 0004](0004-drop-short-deck-support.md)); the conclusion did not change.

## Alternatives considered

- **Making `pdcfr` the default because it is the newest.** The data says otherwise on all four
  scenarios. The paper's benchmarks are not this solver's workload.
- **Tuning `pdcfr`'s α and γ until it wins.** That would fit hyperparameters to the same four
  scenarios the choice is then justified by. Both variants ship with their papers' recommended
  values; anyone with a different workload can measure with `AlgorithmBakeoff`.
- **Deleting the optimistic variants.** They are correct implementations of published algorithms, and
  their poor showing here is itself the finding. They stay, documented.
