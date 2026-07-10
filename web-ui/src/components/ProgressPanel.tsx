import type { JobState, ProgressEvent } from "../types";
import { Stat } from "./ui";

interface Props {
  state: JobState;
  events: ProgressEvent[];
  onCancel: () => void;
}

const STATE_LABEL: Record<JobState, string> = {
  RUNNING: "solving",
  COMPLETED: "converged",
  FAILED: "failed",
  CANCELLED: "cancelled",
};

/** Four numbers, and the curve they came from. Everything else the reader can infer. */
export function ProgressPanel({ state, events, onCancel }: Props) {
  const progress = events.filter((e) => e.type === "progress");
  const last = progress.at(-1);
  const elapsed = progress.reduce((sum, e) => sum + e.elapsedMs, 0);

  return (
    <div>
      <div className="readout">
        <Stat label="state" value={STATE_LABEL[state]} tone={`state-${state.toLowerCase()}`} />
        {last && <Stat label="exploitability" value={`${last.exploitability.toFixed(3)}%`} />}
        {last && <Stat label="iteration" value={last.iteration} />}
        {last && <Stat label="elapsed" value={`${(elapsed / 1000).toFixed(1)}s`} />}
        {state === "RUNNING" && (
          <button type="button" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
      {progress.length > 1 && <ConvergenceChart points={progress} />}
    </div>
  );
}

/**
 * Exploitability against iteration, on a logarithmic vertical axis.
 *
 * <p>CFR drives exploitability down by orders of magnitude — from tens of percent to thousandths —
 * so a linear axis would draw the entire interesting tail as a flat line along the bottom. On a log
 * axis, steady convergence reads as a straight descent, and a stall reads as a plateau.
 */
function ConvergenceChart({ points }: { points: ProgressEvent[] }) {
  const width = 620;
  const height = 168;
  const padLeft = 40;
  const padRight = 8;
  const padTop = 12;
  const padBottom = 24;

  // Clamp to a floor: a solve that reaches exactly zero has nowhere to sit on a log axis.
  const FLOOR = 1e-3;
  const values = points.map((p) => Math.max(p.exploitability, FLOOR));
  // Top the axis just above the first reading. Rounding up to the next decade would leave a band of
  // empty chart; sitting exactly on the reading would collide the top gridline with its own point.
  // The bottom rounds down to a decade, giving the tail somewhere to land.
  const hi = Math.log10(Math.max(...values)) + 0.08;
  const lo = Math.floor(Math.log10(Math.min(...values)));
  const maxIteration = Math.max(...points.map((p) => p.iteration), 1);

  const x = (iteration: number) => padLeft + (iteration / maxIteration) * (width - padLeft - padRight);
  const y = (value: number) => {
    const t = (Math.log10(Math.max(value, FLOOR)) - lo) / Math.max(hi - lo, 1);
    return height - padBottom - t * (height - padTop - padBottom);
  };

  const decades = Array.from({ length: Math.floor(hi) - lo + 1 }, (_, i) => lo + i);
  const curve = points
    .map((p, i) => `${i === 0 ? "M" : "L"}${x(p.iteration).toFixed(1)},${y(p.exploitability).toFixed(1)}`)
    .join(" ");

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      className="chart"
      role="img"
      aria-label={`Exploitability fell to ${points.at(-1)?.exploitability.toFixed(3)}% of pot over ${maxIteration} iterations`}
    >
      {decades.map((decade) => (
        <g key={decade}>
          <line className="grid" x1={padLeft} y1={y(10 ** decade)} x2={width - padRight} y2={y(10 ** decade)} />
          <text x={padLeft - 6} y={y(10 ** decade) + 3} textAnchor="end">
            {decade >= 0 ? `${10 ** decade}%` : `${(10 ** decade).toFixed(-decade)}%`}
          </text>
        </g>
      ))}
      <line className="axis" x1={padLeft} y1={padTop} x2={padLeft} y2={height - padBottom} />
      <text x={width - padRight} y={height - 6} textAnchor="end">
        {maxIteration} iterations
      </text>

      <path className="curve" d={curve} />
      {points.map((p) => (
        <circle className="point" key={p.iteration} cx={x(p.iteration)} cy={y(p.exploitability)} r="2.5">
          <title>
            iteration {p.iteration}: {p.exploitability.toFixed(3)}% of pot
          </title>
        </circle>
      ))}
    </svg>
  );
}
