import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { ProgressEvent } from "../types";
import { ProgressPanel } from "./ProgressPanel";

describe("progress readout", () => {
  const events: ProgressEvent[] = [
    { type: "progress", iteration: 1, exploitability: 2, elapsedMs: 100, error: null },
    { type: "progress", iteration: 23, exploitability: 0.1, elapsedMs: 2500, error: null },
    { type: "completed", iteration: -1, exploitability: 0, elapsedMs: 0, error: null },
  ];

  it("shows cumulative elapsed time and completion without claiming convergence", () => {
    const html = renderToStaticMarkup(<ProgressPanel state="COMPLETED" events={events} onCancel={() => {}} />);
    expect(html).toContain("completed");
    expect(html).not.toContain("converged");
    expect(html).toContain("2.5s");
    expect(html).not.toContain("2.6s");
    expect(html).toContain("iteration 23");
  });

  it("renders the solver's failure reason", () => {
    const html = renderToStaticMarkup(<ProgressPanel state="FAILED" events={[
      { type: "failed", iteration: -1, exploitability: 0, elapsedMs: 0, error: "invalid range" },
    ]} onCancel={() => {}} />);
    expect(html).toContain("invalid range");
  });
});
