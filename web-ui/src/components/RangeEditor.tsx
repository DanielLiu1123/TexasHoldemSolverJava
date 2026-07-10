import { useMemo, useRef, useState } from "react";
import { parseRange, serializeRange } from "../poker";
import { RangeGrid } from "./RangeGrid";

interface Props {
  title: string;
  value: string;
  onChange: (text: string) => void;
}

/** Combos per cell: 6 for a pair, 4 suited, 12 offsuit. */
function combosIn(label: string): number {
  if (label.length === 2) return 6;
  return label.endsWith("s") ? 4 : 12;
}

/**
 * Paint on the grid with the active weight, or edit the text form directly — the two stay in sync,
 * and grid edits re-serialize the text canonically.
 */
export function RangeEditor({ title, value, onChange }: Props) {
  const [weight, setWeight] = useState(1);
  // During a drag stroke, painting either sets the active weight or erases — decided by the state of
  // the first cell touched, so a stroke never toggles back and forth under the cursor.
  const strokeMode = useRef<"paint" | "erase">("paint");
  const strokeStarted = useRef(false);

  const model = useMemo(() => parseRange(value), [value]);

  const paint = (label: string) => {
    if (!strokeStarted.current) {
      strokeMode.current = (model.get(label) ?? 0) === weight ? "erase" : "paint";
      strokeStarted.current = true;
    }
    const next = new Map(model);
    if (strokeMode.current === "erase") next.delete(label);
    else next.set(label, weight);
    onChange(serializeRange(next));
  };

  const combos = useMemo(() => {
    let total = 0;
    for (const [label, w] of model) total += w * combosIn(label);
    return Math.round(total * 10) / 10;
  }, [model]);

  return (
    <div>
      <div className="range-head">
        <h3>{title}</h3>
        <span className="muted mono combos">{combos} combos</span>
        <input
          type="range"
          min="0.05"
          max="1"
          step="0.05"
          value={weight}
          aria-label={`${title} paint weight`}
          onChange={(e) => setWeight(Number.parseFloat(e.target.value))}
        />
        <span className="muted mono">{weight.toFixed(2)}</span>
        <button type="button" className="link" onClick={() => onChange("")}>
          clear
        </button>
      </div>
      <RangeGrid
        onPaintStart={() => {
          strokeStarted.current = false;
        }}
        onPaint={paint}
        renderCell={(label) => {
          const w = model.get(label) ?? 0;
          return {
            // The fill height *is* the weight — no number to read, no legend to consult.
            background:
              w > 0
                ? `linear-gradient(to top, var(--passive) ${w * 100}%, var(--paper-sunk) ${w * 100}%)`
                : "var(--paper-sunk)",
            // Once the fill reaches the label, the label has to sit on top of it.
            labelColor: w >= 0.55 ? "var(--paper)" : "var(--ink-faint)",
            title: w > 0 && w < 1 ? `${label} × ${w}` : label,
          };
        }}
      />
      <textarea
        className="range-text"
        rows={2}
        spellCheck={false}
        value={value}
        placeholder="AA,KQs:0.5,87s"
        aria-label={`${title} as text`}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}
