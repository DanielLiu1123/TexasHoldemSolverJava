/** Rank/range domain logic shared by the editor and the strategy explorer. */

export const HOLDEM_RANKS = ["A", "K", "Q", "J", "T", "9", "8", "7", "6", "5", "4", "3", "2"];
export const SHORTDECK_RANKS = ["A", "K", "Q", "J", "T", "9", "8", "7", "6"];
export const SUITS = ["h", "s", "d", "c"];

export function ranksFor(game: "holdem" | "shortdeck"): string[] {
  return game === "holdem" ? HOLDEM_RANKS : SHORTDECK_RANKS;
}

/**
 * Grid cell label for row i / column j over `ranks` (high rank first):
 * diagonal = pair ("AA"), above = suited ("AKs"), below = offsuit ("AKo").
 */
export function cellLabel(ranks: string[], row: number, col: number): string {
  if (row === col) return ranks[row] + ranks[col];
  if (row < col) return ranks[row] + ranks[col] + "s";
  return ranks[col] + ranks[row] + "o";
}

/** A range as cell label → weight in (0, 1]. */
export type RangeModel = Map<string, number>;

/**
 * Parses range text ("AA,KQs:0.5,87") into a cell model. A bare two-rank token like "AK"
 * expands to both "AKs" and "AKo". Unknown tokens are ignored (the API performs the
 * authoritative validation).
 */
export function parseRange(text: string, ranks: string[]): RangeModel {
  const model: RangeModel = new Map();
  const valid = new Set<string>();
  for (let i = 0; i < ranks.length; i++) {
    for (let j = 0; j < ranks.length; j++) valid.add(cellLabel(ranks, i, j));
  }
  for (const rawToken of text.split(",")) {
    const token = rawToken.trim();
    if (!token) continue;
    const [hand, weightStr] = token.split(":");
    const weight = weightStr === undefined ? 1 : Number.parseFloat(weightStr);
    if (!Number.isFinite(weight) || weight <= 0) continue;
    const clamped = Math.min(weight, 1);
    if (valid.has(hand)) {
      model.set(hand, clamped);
    } else if (hand.length === 2 && hand[0] !== hand[1]) {
      // "AK" → suited + offsuit; ranks may appear in either order
      for (const candidate of [hand + "s", hand + "o", hand[1] + hand[0] + "s", hand[1] + hand[0] + "o"]) {
        if (valid.has(candidate)) model.set(candidate, clamped);
      }
    }
  }
  return model;
}

/** Serializes a cell model back to canonical range text, in grid order. */
export function serializeRange(model: RangeModel, ranks: string[]): string {
  const parts: string[] = [];
  for (let i = 0; i < ranks.length; i++) {
    for (let j = 0; j < ranks.length; j++) {
      const label = cellLabel(ranks, i, j);
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
export function comboToCell(combo: string, ranks: string[]): string | null {
  if (combo.length !== 4) return null;
  const r1 = combo[0];
  const s1 = combo[1];
  const r2 = combo[2];
  const s2 = combo[3];
  const i1 = ranks.indexOf(r1);
  const i2 = ranks.indexOf(r2);
  if (i1 < 0 || i2 < 0) return null;
  if (r1 === r2) return r1 + r2;
  const [hi, lo] = i1 < i2 ? [r1, r2] : [r2, r1];
  return hi + lo + (s1 === s2 ? "s" : "o");
}

/** All cards of a deck in display order, e.g. "Ah". */
export function deckCards(ranks: string[]): string[] {
  const cards: string[] = [];
  for (const rank of ranks) for (const suit of SUITS) cards.push(rank + suit);
  return cards;
}

export const SUIT_SYMBOL: Record<string, string> = { h: "♥", s: "♠", d: "♦", c: "♣" };
export const SUIT_COLOR: Record<string, string> = { h: "#e74c3c", s: "#ecf0f1", d: "#3498db", c: "#2ecc71" };
