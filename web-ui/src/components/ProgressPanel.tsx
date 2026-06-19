import type { JobState, ProgressEvent } from "../types";

interface Props {
  state: JobState;
  events: ProgressEvent[];
  onCancel: () => void;
}

const STATE_LABEL: Record<JobState, string> = {
  RUNNING: "求解中",
  COMPLETED: "已完成",
  FAILED: "失败",
  CANCELLED: "已取消",
};

/** Plain-language verdict for a final exploitability (% of pot). */
function verdict(exploitability: number): { tier: "great" | "good" | "ok" | "rough"; title: string; detail: string } {
  if (exploitability <= 0.5)
    return {
      tier: "great",
      title: "已收敛到接近最优（GTO）",
      detail: "对手即使完美应对，也几乎占不到便宜，可直接把这套策略当作标准答案使用。",
    };
  if (exploitability <= 1)
    return {
      tier: "good",
      title: "已接近最优",
      detail: "作为近似 GTO 策略使用没有问题；若想更精确，可在高级设置里调高迭代次数。",
    };
  if (exploitability <= 2)
    return {
      tier: "ok",
      title: "大致收敛，可作参考",
      detail: "整体方向可信，但仍有可被利用的空间。增大迭代次数能进一步逼近最优。",
    };
  return {
    tier: "rough",
    title: "尚未充分收敛",
    detail: "当前策略还比较粗糙。建议把迭代次数调大，或把「提前停止阈值」调低后重新求解。",
  };
}

/** Live convergence view: exploitability-over-iterations chart plus the event log. */
export function ProgressPanel({ state, events, onCancel }: Props) {
  const progress = events.filter((e) => e.type === "progress");
  const last = progress.at(-1);
  const done = state === "COMPLETED" && last;
  const summary = done ? verdict(last.exploitability) : null;
  return (
    <div className="progress-panel">
      <div className="progress-head">
        <span className={`state state-${state.toLowerCase()}`}>{STATE_LABEL[state]}</span>
        {last && (
          <span className="muted">
            当前可利用度 <b>{last.exploitability.toFixed(3)}%</b> 底池
          </span>
        )}
        {state === "RUNNING" && (
          <button type="button" onClick={onCancel}>
            取消
          </button>
        )}
      </div>
      {done && summary && last && (
        <div className={`solve-summary summary-${summary.tier}`}>
          <div className="summary-title">{summary.title}</div>
          <p className="summary-text">
            经过 <b>{last.iteration}</b> 次迭代（约 {(last.elapsedMs / 1000).toFixed(1)}s），最终可利用度收敛到{" "}
            <b>{last.exploitability.toFixed(3)}%</b> 底池。{summary.detail}
          </p>
          <p className="summary-next">
            ↓ 在下方「策略结果」里点动作按钮，就能看到每手牌该怎么打（过牌 / 下注 / 加注的最优比例）。
          </p>
        </div>
      )}
      {progress.length > 0 && <ConvergenceChart points={progress} />}
      <div className="event-log">
        {progress.map((e) => (
          <div key={e.iteration} className="event-row">
            <span>第 {e.iteration} 次</span>
            <span>{e.exploitability.toFixed(3)}% 底池</span>
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
