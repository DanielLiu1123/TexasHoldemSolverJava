import type { CSSProperties, ReactNode } from "react";
import { RANKS, cellLabel } from "../poker";

export interface CellRender {
  background: string;
  /** Label colour. The caller knows whether the cell is filled; the grid does not. */
  labelColor?: string;
  title?: string;
  content?: ReactNode;
}

interface Props {
  className?: string;
  renderCell: (label: string) => CellRender;
  onPaint?: (label: string) => void;
  onPaintStart?: () => void;
}

/**
 * The 13×13 hand grid. Pure presentation: the parent decides each cell's background — a selection
 * weight, or a strategy mix — and receives paint callbacks for click-drag editing.
 */
export function RangeGrid({ className, renderCell, onPaint, onPaintStart }: Props) {
  const gridStyle: CSSProperties = {
    display: "grid",
    gridTemplateColumns: `repeat(${RANKS.length}, 1fr)`,
  };
  return (
    <div className={className ? `range-grid ${className}` : "range-grid"} style={gridStyle}>
      {RANKS.map((_, row) =>
        RANKS.map((_, col) => {
          const label = cellLabel(row, col);
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
              <span className="range-cell-label" style={cell.labelColor ? { color: cell.labelColor } : undefined}>
                {label}
              </span>
              {cell.content}
            </div>
          );
        }),
      )}
    </div>
  );
}
