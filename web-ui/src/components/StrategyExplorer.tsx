import { useMemo, useState } from "react";
import { SUIT_COLOR, SUIT_SYMBOL, comboToCell } from "../poker";
import type { ActionStrategyNode, StrategyNode } from "../types";
import { RangeGrid } from "./RangeGrid";

interface Props {
  root: StrategyNode;
  ranks: string[];
}

type Step = { kind: "action" | "card"; label: string };

/** Color per action label: folds blue, checks/calls green, bets/raises warm by size order. */
function actionColors(actions: string[]): Map<string, string> {
  const warm = ["#e67e22", "#e74c3c", "#c0392b", "#922b21", "#641e16"];
  let warmIndex = 0;
  const colors = new Map<string, string>();
  for (const action of actions) {
    if (action.startsWith("FOLD")) colors.set(action, "#3b82c4");
    else if (action.startsWith("CHECK") || action.startsWith("CALL")) colors.set(action, "#2f855a");
    else colors.set(action, warm[Math.min(warmIndex++, warm.length - 1)]);
  }
  return colors;
}

function walk(root: StrategyNode, path: Step[]): StrategyNode | null {
  let node: StrategyNode | null = root;
  for (const step of path) {
    if (node === null) return null;
    if (node.node_type === "action_node") node = node.children?.[step.label] ?? null;
    else node = node.deal_cards[step.label] ?? null;
  }
  return node;
}

export function StrategyExplorer({ root, ranks }: Props) {
  const [path, setPath] = useState<Step[]>([]);
  const node = useMemo(() => walk(root, path), [root, path]);

  return (
    <div className="strategy-explorer">
      <nav className="breadcrumbs">
        <button type="button" className="crumb" onClick={() => setPath([])}>
          起点
        </button>
        {path.map((step, i) => (
          <button type="button" key={`${i}-${step.label}`} className="crumb" onClick={() => setPath(path.slice(0, i + 1))}>
            {step.label}
          </button>
        ))}
      </nav>
      {node === null && <p className="muted">终局节点 —— 这条线到此结束。</p>}
      {node?.node_type === "action_node" && (
        <ActionNodeView node={node} ranks={ranks} onDescend={(label) => setPath([...path, { kind: "action", label }])} />
      )}
      {node?.node_type === "chance_node" && (
        <div className="chance-view">
          <h3>选择发出的牌</h3>
          <div className="deal-cards">
            {Object.keys(node.deal_cards).map((card) => (
              <button
                type="button"
                key={card}
                className="card"
                onClick={() => setPath([...path, { kind: "card", label: card }])}
              >
                {card[0]}
                <span style={{ color: SUIT_COLOR[card[1]] }}>{SUIT_SYMBOL[card[1]]}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function ActionNodeView({
  node,
  ranks,
  onDescend,
}: {
  node: ActionStrategyNode;
  ranks: string[];
  onDescend: (action: string) => void;
}) {
  const colors = useMemo(() => actionColors(node.strategy.actions), [node]);

  // Aggregate combo strategies into grid cells (uniform combo weighting; reach
  // probabilities are not part of the dump).
  const cellMix = useMemo(() => {
    const sums = new Map<string, { probs: number[]; count: number }>();
    for (const [combo, probs] of Object.entries(node.strategy.strategy)) {
      const cell = comboToCell(combo, ranks);
      if (!cell) continue;
      const entry = sums.get(cell) ?? { probs: new Array<number>(probs.length).fill(0), count: 0 };
      for (let i = 0; i < probs.length; i++) entry.probs[i] += probs[i];
      entry.count += 1;
      sums.set(cell, entry);
    }
    const mix = new Map<string, number[]>();
    for (const [cell, { probs, count }] of sums) mix.set(cell, probs.map((p) => p / count));
    return mix;
  }, [node, ranks]);

  const averages = useMemo(() => {
    const totals = new Array<number>(node.strategy.actions.length).fill(0);
    let count = 0;
    for (const probs of cellMix.values()) {
      for (let i = 0; i < probs.length; i++) totals[i] += probs[i];
      count += 1;
    }
    return totals.map((t) => (count > 0 ? t / count : 0));
  }, [node, cellMix]);

  return (
    <div className="action-view">
      <h3>轮到 {node.player === 0 ? "IP（有利位）" : "OOP（不利位）"} 行动</h3>
      <div className="action-buttons">
        {node.strategy.actions.map((action, i) => (
          <button
            type="button"
            key={action}
            className="action-button"
            style={{ borderColor: colors.get(action) }}
            disabled={!node.children?.[action]}
            onClick={() => onDescend(action)}
          >
            <span className="dot" style={{ background: colors.get(action) }} />
            {action}
            <span className="muted"> {(averages[i] * 100).toFixed(1)}%</span>
          </button>
        ))}
      </div>
      <RangeGrid
        ranks={ranks}
        renderCell={(label) => {
          const probs = cellMix.get(label);
          if (!probs) return { background: "#161e29" };
          const stops: string[] = [];
          let acc = 0;
          probs.forEach((p, i) => {
            const from = acc;
            acc += p * 100;
            stops.push(`${colors.get(node.strategy.actions[i])} ${from.toFixed(1)}% ${acc.toFixed(1)}%`);
          });
          return {
            background: `linear-gradient(to right, ${stops.join(", ")})`,
            title: `${label}: ${node.strategy.actions.map((a, i) => `${a} ${(probs[i] * 100).toFixed(0)}%`).join(", ")}`,
          };
        }}
      />
    </div>
  );
}
