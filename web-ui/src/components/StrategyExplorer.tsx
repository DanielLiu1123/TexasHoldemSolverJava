import { useEffect, useMemo, useState } from "react";
import { getStrategyNode } from "../api";
import { comboToCell } from "../poker";
import type { ActionStrategyNode, StrategyNode } from "../types";
import { CardFace } from "./Card";
import { RangeGrid } from "./RangeGrid";

interface Props {
  solveId: string;
}

/**
 * Colour by what the action means, not by where it sits in the list: folds recede into grey, passive
 * lines settle into green, and aggression warms as it gets bigger.
 */
function actionColours(actions: string[]): Map<string, string> {
  const aggression = [
    "var(--aggro-1)",
    "var(--aggro-2)",
    "var(--aggro-3)",
    "var(--aggro-4)",
    "var(--aggro-5)",
  ];
  let next = 0;
  const colours = new Map<string, string>();
  for (const action of actions) {
    if (action.startsWith("FOLD")) colours.set(action, "var(--fold)");
    else if (action.startsWith("CHECK") || action.startsWith("CALL")) colours.set(action, "var(--passive)");
    else colours.set(action, aggression[Math.min(next++, aggression.length - 1)]);
  }
  return colours;
}

/**
 * Walks the solved tree one node at a time: the current path is a list of edge labels, and each node
 * is fetched on demand — the whole tree is far too large to ship at once.
 */
export function StrategyExplorer({ solveId }: Props) {
  const [path, setPath] = useState<string[]>([]);
  const [node, setNode] = useState<StrategyNode | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    setNode(null);
    getStrategyNode(solveId, path)
      .then((n) => !cancelled && setNode(n))
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : String(e)));
    return () => {
      cancelled = true;
    };
  }, [solveId, path]);

  return (
    <div>
      <nav className="breadcrumbs">
        <button type="button" className="crumb" onClick={() => setPath([])}>
          root
        </button>
        {path.map((label, i) => (
          <span key={`${i}-${label}`} style={{ display: "contents" }}>
            <span className="crumb-sep" aria-hidden>
              /
            </span>
            <button type="button" className="crumb" onClick={() => setPath(path.slice(0, i + 1))}>
              {label}
            </button>
          </span>
        ))}
      </nav>

      {error && <p className="error">{error}</p>}
      {!error && !node && <p className="muted">Loading…</p>}

      {node?.nodeType === "terminal" && <p className="muted">The line ends here.</p>}

      {node?.nodeType === "action_node" && (
        <ActionNodeView node={node} onDescend={(label) => setPath([...path, label])} />
      )}

      {node?.nodeType === "chance_node" && (
        <div className="deal">
          {node.cards.map((card) => (
            <button type="button" key={card} className="card" onClick={() => setPath([...path, card])}>
              <CardFace card={card} />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function ActionNodeView({ node, onDescend }: { node: ActionStrategyNode; onDescend: (action: string) => void }) {
  const colours = useMemo(() => actionColours(node.strategy.actions), [node]);

  // Aggregate per-combo strategies into grid cells. Combos are weighted uniformly: the strategy dump
  // does not carry reach probabilities, so a cell shows the average line across its combos.
  const cellMix = useMemo(() => {
    const sums = new Map<string, { probs: number[]; count: number }>();
    for (const [combo, probs] of Object.entries(node.strategy.strategy)) {
      const cell = comboToCell(combo);
      if (!cell) continue;
      const entry = sums.get(cell) ?? { probs: new Array<number>(probs.length).fill(0), count: 0 };
      for (let i = 0; i < probs.length; i++) entry.probs[i] += probs[i];
      entry.count += 1;
      sums.set(cell, entry);
    }
    const mix = new Map<string, number[]>();
    for (const [cell, { probs, count }] of sums) mix.set(cell, probs.map((p) => p / count));
    return mix;
  }, [node]);

  const shares = useMemo(() => {
    const totals = new Array<number>(node.strategy.actions.length).fill(0);
    let count = 0;
    for (const probs of cellMix.values()) {
      for (let i = 0; i < probs.length; i++) totals[i] += probs[i];
      count += 1;
    }
    return totals.map((t) => (count > 0 ? t / count : 0));
  }, [node, cellMix]);

  const navigable = useMemo(() => new Set(node.childActions), [node]);

  return (
    <div>
      <p className="actor">
        {node.player === 0 ? "In position" : "Out of position"} to act. Each cell is filled by the mix above.
      </p>
      <div className="actions">
        {node.strategy.actions.map((action, i) => (
          <button
            type="button"
            key={action}
            className="action"
            disabled={!navigable.has(action)}
            onClick={() => onDescend(action)}
            title={navigable.has(action) ? `Descend into ${action}` : `${action} ends the line`}
          >
            <span className="swatch" style={{ background: colours.get(action) }} />
            {action}
            <span className="share">{(shares[i] * 100).toFixed(1)}%</span>
          </button>
        ))}
      </div>
      <RangeGrid
        className="strategy-grid"
        renderCell={(label) => {
          const probs = cellMix.get(label);
          if (!probs) return { background: "var(--paper-sunk)" };
          const stops: string[] = [];
          let acc = 0;
          probs.forEach((p, i) => {
            const from = acc;
            acc += p * 100;
            stops.push(`${colours.get(node.strategy.actions[i])} ${from.toFixed(1)}% ${acc.toFixed(1)}%`);
          });
          return {
            background: `linear-gradient(to right, ${stops.join(", ")})`,
            labelColor: "var(--paper)",
            title: `${label} — ${node.strategy.actions
              .map((a, i) => `${a} ${(probs[i] * 100).toFixed(0)}%`)
              .join(", ")}`,
          };
        }}
      />
    </div>
  );
}
