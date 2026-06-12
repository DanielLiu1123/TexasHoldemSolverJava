import type { CSSProperties, ReactNode } from "react";
import { cellLabel } from "../poker";

export interface CellRender {
  background: string;
  title?: string;
  content?: ReactNode;
}

interface Props {
  ranks: string[];
  renderCell: (label: string) => CellRender;
  onPaint?: (label: string) => void;
  onPaintStart?: () => void;
}

/**
 * The 13×13 (or 9×9 for shortdeck) hand grid. Pure presentation: the parent decides each
 * cell's background (selection weight or strategy mix) via renderCell, and receives paint
 * callbacks for click-drag editing.
 */
export function RangeGrid({ ranks, renderCell, onPaint, onPaintStart }: Props) {
  const gridStyle: CSSProperties = {
    display: "grid",
    gridTemplateColumns: `repeat(${ranks.length}, 1fr)`,
  };
  return (
    <div className="range-grid" style={gridStyle}>
      {ranks.map((_, row) =>
        ranks.map((_, col) => {
          const label = cellLabel(ranks, row, col);
          const cell = renderCell(label);
          return (
            <div
              key={label}
              className="range-cell"
              title={cell.title ?? label}
              style={{ background: cell.background }}
              onMouseDown={(e) => {
                e.preventDefault();
                onPaintStart?.();
                onPaint?.(label);
              }}
              onMouseEnter={(e) => {
                if (e.buttons === 1) onPaint?.(label);
              }}
            >
              <span className="range-cell-label">{label}</span>
              {cell.content}
            </div>
          );
        }),
      )}
    </div>
  );
}
