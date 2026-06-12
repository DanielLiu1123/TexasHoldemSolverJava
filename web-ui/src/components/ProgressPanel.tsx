import type { JobState, ProgressEvent } from "../types";

interface Props {
  state: JobState;
  events: ProgressEvent[];
  onCancel: () => void;
}

/** Live convergence view: exploitability-over-iterations chart plus the event log. */
export function ProgressPanel({ state, events, onCancel }: Props) {
  const progress = events.filter((e) => e.type === "progress");
  return (
    <div className="progress-panel">
      <div className="progress-head">
        <span className={`state state-${state.toLowerCase()}`}>{state}</span>
        {state === "RUNNING" && (
          <button type="button" onClick={onCancel}>
            cancel
          </button>
        )}
      </div>
      {progress.length > 0 && <ConvergenceChart points={progress} />}
      <div className="event-log">
        {progress.map((e) => (
          <div key={e.iteration} className="event-row">
            <span>iter {e.iteration}</span>
            <span>{e.exploitability.toFixed(3)}% pot</span>
            <span className="muted">{e.elapsedMs} ms</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function ConvergenceChart({ points }: { points: ProgressEvent[] }) {
  const w = 520;
  const h = 160;
  const pad = 28;
  const maxY = Math.max(...points.map((p) => p.exploitability), 1);
  const maxX = Math.max(...points.map((p) => p.iteration), 1);
  const x = (iter: number) => pad + (iter / maxX) * (w - 2 * pad);
  const y = (e: number) => h - pad - (e / maxY) * (h - 2 * pad);
  const path = points.map((p, i) => `${i === 0 ? "M" : "L"}${x(p.iteration).toFixed(1)},${y(p.exploitability).toFixed(1)}`).join(" ");
  return (
    <svg viewBox={`0 0 ${w} ${h}`} className="chart" role="img" aria-label="exploitability convergence">
      <line x1={pad} y1={h - pad} x2={w - pad} y2={h - pad} stroke="#3a4a5e" />
      <line x1={pad} y1={pad} x2={pad} y2={h - pad} stroke="#3a4a5e" />
      <text x={pad - 4} y={pad + 4} textAnchor="end" className="chart-label">
        {maxY.toFixed(0)}%
      </text>
      <text x={w - pad} y={h - pad + 14} textAnchor="end" className="chart-label">
        iter {maxX}
      </text>
      <path d={path} fill="none" stroke="#4fa3ff" strokeWidth="2" />
      {points.map((p) => (
        <circle key={p.iteration} cx={x(p.iteration)} cy={y(p.exploitability)} r="2.5" fill="#4fa3ff" />
      ))}
    </svg>
  );
}
