import { describe, expect, it } from "vitest";
import { HOLDEM_RANKS, SHORTDECK_RANKS, cellLabel, comboToCell, parseRange, serializeRange } from "./poker";

describe("cellLabel", () => {
  it("labels pairs, suited and offsuit cells", () => {
    expect(cellLabel(HOLDEM_RANKS, 0, 0)).toBe("AA");
    expect(cellLabel(HOLDEM_RANKS, 0, 1)).toBe("AKs");
    expect(cellLabel(HOLDEM_RANKS, 1, 0)).toBe("AKo");
  });
});

describe("parseRange / serializeRange", () => {
  it("round-trips weighted ranges", () => {
    const text = "AA,AKs:0.5,QJo:0.25,55";
    const model = parseRange(text, HOLDEM_RANKS);
    expect(model.get("AA")).toBe(1);
    expect(model.get("AKs")).toBe(0.5);
    expect(serializeRange(model, HOLDEM_RANKS)).toBe("AA,AKs:0.5,QJo:0.25,55");
  });

  it("expands bare two-rank tokens to suited and offsuit", () => {
    const model = parseRange("AK:0.8", HOLDEM_RANKS);
    expect(model.get("AKs")).toBe(0.8);
    expect(model.get("AKo")).toBe(0.8);
  });

  it("accepts reversed rank order", () => {
    const model = parseRange("KA", HOLDEM_RANKS);
    expect(model.get("AKs")).toBe(1);
    expect(model.get("AKo")).toBe(1);
  });

  it("ignores garbage and clamps weights", () => {
    const model = parseRange("XX,AA:7,,QQ:-1", HOLDEM_RANKS);
    expect(model.get("AA")).toBe(1);
    expect(model.has("QQ")).toBe(false);
    expect(model.size).toBe(1);
  });

  it("works for shortdeck ranks", () => {
    const model = parseRange("76s,A6o", SHORTDECK_RANKS);
    expect(model.get("76s")).toBe(1);
    expect(model.get("A6o")).toBe(1);
  });
});

describe("comboToCell", () => {
  it("maps combos to grid cells", () => {
    expect(comboToCell("AhKh", HOLDEM_RANKS)).toBe("AKs");
    expect(comboToCell("AhKs", HOLDEM_RANKS)).toBe("AKo");
    expect(comboToCell("KsAh", HOLDEM_RANKS)).toBe("AKo");
    expect(comboToCell("7c7d", HOLDEM_RANKS)).toBe("77");
    expect(comboToCell("bogus", HOLDEM_RANKS)).toBeNull();
  });
});
