/** Rank/range domain logic shared by the editor and the strategy explorer. */

/** The thirteen hold'em ranks, high first. The grid, the board picker and the deck all order by it. */
export const RANKS = ["A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2"];

export const SUITS = ["h", "s", "d", "c"];

/**
 * Grid cell label for row i / column j:
 * diagonal = pair ("AA"), above = suited ("AKs"), below = offsuit ("AKo").
 */
export function cellLabel(row: number, col: number): string {
  if (row === col) return RANKS[row] + RANKS[col];
  if (row < col) return RANKS[row] + RANKS[col] + "s";
  return RANKS[col] + RANKS[row] + "o";
}

/** Every legal cell label, computed once. */
const CELL_LABELS: ReadonlySet<string> = (() => {
  const labels = new Set<string>();
  for (let i = 0; i < RANKS.length; i++) {
    for (let j = 0; j < RANKS.length; j++) labels.add(cellLabel(i, j));
  }
  return labels;
})();

/** A range as cell label → weight in (0, 1]. */
export type RangeModel = Map<string, number>;

/**
 * Parses range text ("AA,KQs:0.5,87") into a cell model. A bare two-rank token like "AK"
 * expands to both "AKs" and "AKo". Unknown tokens are ignored (the API performs the
 * authoritative validation).
 */
export function parseRange(text: string): RangeModel {
  const model: RangeModel = new Map();
  for (const rawToken of text.split(",")) {
    const token = rawToken.trim();
    if (!token) continue;
    const [hand, weightStr] = token.split(":");
    const weight = weightStr === undefined ? 1 : Number.parseFloat(weightStr);
    if (!Number.isFinite(weight) || weight <= 0) continue;
    const clamped = Math.min(weight, 1);
    if (CELL_LABELS.has(hand)) {
      model.set(hand, clamped);
    } else if (hand.length === 2 && hand[0] !== hand[1]) {
      // "AK" → suited + offsuit; ranks may appear in either order
      for (const candidate of [hand + "s", hand + "o", hand[1] + hand[0] + "s", hand[1] + hand[0] + "o"]) {
        if (CELL_LABELS.has(candidate)) model.set(candidate, clamped);
      }
    }
  }
  return model;
}

/** Serializes a cell model back to canonical range text, in grid order. */
export function serializeRange(model: RangeModel): string {
  const parts: string[] = [];
  for (let i = 0; i < RANKS.length; i++) {
    for (let j = 0; j < RANKS.length; j++) {
      const label = cellLabel(i, j);
      const weight = model.get(label);
      if (weight === undefined || weight <= 0) continue;
      parts.push(weight >= 1 ? label : `${label}:${trimFloat(weight)}`);
    }
  }
  return parts.join(",");
}

function trimFloat(x: number): string {
  return x.toFixed(2).replace(/0+$/, "").replace(/\.$/, "");
}

/** Maps a combo string like "AhKs" to its grid cell label, e.g. "AKs"/"AKo"/"AA". */
export function comboToCell(combo: string): string | null {
  if (combo.length !== 4) return null;
  const [r1, s1, r2, s2] = combo;
  const i1 = RANKS.indexOf(r1);
  const i2 = RANKS.indexOf(r2);
  if (i1 < 0 || i2 < 0) return null;
  if (r1 === r2) return r1 + r2;
  const [hi, lo] = i1 < i2 ? [r1, r2] : [r2, r1];
  return hi + lo + (s1 === s2 ? "s" : "o");
}

/** All 52 cards in display order, e.g. "Ah". */
export function deckCards(): string[] {
  const cards: string[] = [];
  for (const rank of RANKS) for (const suit of SUITS) cards.push(rank + suit);
  return cards;
}

export const SUIT_SYMBOL: Record<string, string> = { h: "♥", s: "♠", d: "♦", c: "♣" };
export const SUIT_COLOR: Record<string, string> = { h: "#e74c3c", s: "#ecf0f1", d: "#3498db", c: "#2ecc71" };
