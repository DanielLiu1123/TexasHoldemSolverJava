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

| | sd-river (narrow) | sd-river (wide) | hold'em river | short-deck turn |
|---|---|---|---|---|
| `cfr` | 0.052 | 0.052 | 0.220 | 3.003 |
| `cfr_plus` | 0.0059 | 0.0097 | 0.0100 | 0.288 |
| `pcfr_plus` | 0.0031 | 0.0103 | 0.0251 | 0.287 |
| `pdcfr_plus` | 0.0032 | 0.0280 | 0.0213 | 0.227 |
| `pdcfr` | 0.0018 | 0.0095 | 0.0174 | 0.201 |
| **`discounted_cfr`** | **0.0009** | **0.0014** | **0.0051** | **0.096** |

**Discounted CFR wins every scenario, so it stays the default.**

Two things the table explains:

- `pdcfr` beats `pdcfr_plus` everywhere. Regret-matching⁺ clips the accumulator at zero, which leaves
  DCFR's β nothing to discount — and β = 0, halving negative regret rather than erasing it, is where
  most of DCFR's edge on poker subgames comes from. Keeping the sign recovers it.
- The optimistic variants still trail plain DCFR. Their own papers say as much: PCFR+ leads on matrix
  games, where the regret sequence is smooth enough for the prediction to pay for itself, and trails
  DCFR on poker. Combining optimism with discounting narrows the gap without closing it.

Run out to 800 iterations on the wide short-deck river, the picture is more interesting than the
200-iteration snapshot:

| iterations | 100 | 200 | 400 | 800 |
|---|---|---|---|---|
| `cfr_plus` | 0.0391 | 0.0097 | 0.0023 | 0.00055 |
| `pcfr_plus` | 0.0366 | 0.0103 | 0.00097 | 0.00012 |
| `pdcfr_plus` | 0.0222 | 0.0280 | 0.0039 | 0.00045 |
| `pdcfr` | 0.0171 | 0.0095 | 0.0012 | 0.00014 |
| `discounted_cfr` | 0.0088 | 0.0014 | 0.00024 | 0.000093 |

`pdcfr_plus` rises between 100 and 200 iterations and then resumes falling: optimistic prediction
overshoots on a non-stationary regret sequence. It is oscillation, not divergence. And the optimistic
variants do close most of the gap asymptotically, which is their tighter regret bound arriving — but
a solve is run at 100-200 iterations, not 800, and DCFR leads by 3-6× there.

## Consequences

Every variant's solved strategy changed, so `StrategyRegressionTest`'s golden table was regenerated.

`pcfr_plus`'s golden values came out **bit-identical to the pre-refactor baseline**. It is the one
variant whose semantics this work did not touch — it already dumped the average and accumulated it
correctly. That coincidence is load-bearing evidence: the hand evaluator, the struct-of-arrays range
layout, the rewritten tree builder, and the SIMD kernels are all exactly equivalent to what they
replaced. Only the three bugs moved.

Convergence improved across the board. On the wide short-deck river at 100 iterations:

| | before | after |
|---|---|---|
| `cfr` | 0.6% ceiling (test) | 0.131% |
| `cfr_plus` | 0.05% ceiling (test) | 0.039% |
| `discounted_cfr` | 0.02% ceiling (test) | 0.0088% |

`SolverConvergenceTest` now asserts exploitability directly, per variant, with ceilings ~40% above
measurement — and asserts the ordering (`cfr_plus < cfr`, `discounted_cfr < cfr_plus`, `pdcfr <
pdcfr_plus`, `discounted_cfr < pdcfr`). A regression in any trainable fails a test that says what
broke, instead of a golden table that says a number changed.

## Alternatives considered

- **Making `pdcfr` the default because it is the newest.** The data says otherwise on all four
  scenarios. The paper's benchmarks are not this solver's workload.
- **Tuning `pdcfr`'s α and γ until it wins.** That would fit hyperparameters to the same four
  scenarios the choice is then justified by. Both variants ship with their papers' recommended
  values; anyone with a different workload can measure with `AlgorithmBakeoff`.
- **Deleting the optimistic variants.** They are correct implementations of published algorithms, and
  their poor showing here is itself the finding. They stay, documented.
