import { useMemo, useRef, useState } from "react";
import { parseRange, serializeRange } from "../poker";
import { RangeGrid } from "./RangeGrid";

interface Props {
  title: string;
  value: string;
  onChange: (text: string) => void;
}

/**
 * Range editor: paint on the grid with the active weight, or edit the text form directly —
 * the two stay in sync (grid edits re-serialize the text canonically).
 */
export function RangeEditor({ title, value, onChange }: Props) {
  const [weight, setWeight] = useState(1);
  // During a drag stroke, painting either sets the active weight or erases — decided by the
  // state of the first cell touched.
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
    for (const [label, w] of model) {
      const isPair = label.length === 2;
      const isSuited = label.endsWith("s");
      total += w * (isPair ? 6 : isSuited ? 4 : 12);
    }
    return Math.round(total * 10) / 10;
  }, [model]);

  return (
    <div className="range-editor">
      <div className="range-editor-head">
        <h3>{title}</h3>
        <label className="weight-control">
          权重 {weight.toFixed(2)}
          <input
            type="range"
            min="0.05"
            max="1"
            step="0.05"
            value={weight}
            onChange={(e) => setWeight(Number.parseFloat(e.target.value))}
          />
        </label>
        <span className="muted">{combos} 个组合</span>
        <button type="button" className="link" onClick={() => onChange("")}>
          清空
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
            background:
              w > 0 ? `linear-gradient(to top, #2f855a ${w * 100}%, #1d2733 ${w * 100}%)` : "#1d2733",
            title: w > 0 ? `${label} ×${w}` : label,
          };
        }}
      />
      <textarea
        className="range-text"
        rows={3}
        spellCheck={false}
        value={value}
        placeholder="AA,KQs:0.5,87s …"
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}
