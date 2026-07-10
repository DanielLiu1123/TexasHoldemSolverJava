import { useCallback, useEffect, useRef, useState } from "react";
import { cancelJob, createSolve, getJob, subscribe } from "./api";
import { BoardPicker } from "./components/BoardPicker";
import { ProgressPanel } from "./components/ProgressPanel";
import { RangeEditor } from "./components/RangeEditor";
import { StrategyExplorer } from "./components/StrategyExplorer";
import { StreetForm } from "./components/StreetForm";
import { Field, Section } from "./components/ui";
import type { JobView, ProgressEvent, SolveRequest, StreetSpec } from "./types";

const DEFAULT_RANGE =
  "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,KQ,KJ,KT,QJ,QT,JT,T9s,98s,87s,76s";

const EXAMPLE = {
  board: "Ah,Kd,7c",
  rangeIp: DEFAULT_RANGE,
  rangeOop: DEFAULT_RANGE,
  pot: 20,
  effectiveStack: 90,
  raiseLimit: 5,
  iterations: 100,
  progressInterval: 10,
  stopExploitability: 0.5,
  algorithm: "discounted_cfr",
} as const satisfies SolveRequest;

export function App() {
  const [board, setBoard] = useState<string[]>([]);
  const [rangeIp, setRangeIp] = useState(DEFAULT_RANGE);
  const [rangeOop, setRangeOop] = useState(DEFAULT_RANGE);
  const [pot, setPot] = useState(20);
  const [stack, setStack] = useState(90);
  const [raiseLimit, setRaiseLimit] = useState(5);
  const [iterations, setIterations] = useState(100);
  const [progressInterval, setProgressInterval] = useState(10);
  const [stopExploitability, setStopExploitability] = useState(0.5);
  const [algorithm, setAlgorithm] = useState<SolveRequest["algorithm"]>("discounted_cfr");
  const [flop, setFlop] = useState<StreetSpec>({});
  const [turn, setTurn] = useState<StreetSpec>({});
  const [river, setRiver] = useState<StreetSpec>({});

  const [job, setJob] = useState<JobView | null>(null);
  const [events, setEvents] = useState<ProgressEvent[]>([]);
  const [error, setError] = useState<string | null>(null);
  const unsubscribe = useRef<(() => void) | null>(null);
  const resultRef = useRef<HTMLDivElement | null>(null);

  const ready = board.length >= 3;
  const running = job?.state === "RUNNING";
  const solved = job?.state === "COMPLETED" || job?.state === "CANCELLED";

  useEffect(() => () => unsubscribe.current?.(), []);

  const runSolve = useCallback(async (request: SolveRequest) => {
    setError(null);
    setEvents([]);
    try {
      const created = await createSolve(request);
      setJob(created);
      requestAnimationFrame(() => resultRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
      unsubscribe.current?.();
      unsubscribe.current = subscribe(created.id, async (event) => {
        setEvents((prev) => [...prev, event]);
        if (event.type !== "progress") setJob(await getJob(created.id));
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  const solve = () =>
    runSolve({
      board: board.join(","),
      rangeIp,
      rangeOop,
      pot,
      effectiveStack: stack,
      raiseLimit,
      iterations,
      progressInterval,
      stopExploitability,
      algorithm,
      flop,
      turn,
      river,
    });

  /** Fill a complete, valid scenario and solve it — the shortest path to seeing what this does. */
  const loadExample = () => {
    setBoard(EXAMPLE.board.split(","));
    setRangeIp(EXAMPLE.rangeIp);
    setRangeOop(EXAMPLE.rangeOop);
    setPot(EXAMPLE.pot);
    setStack(EXAMPLE.effectiveStack);
    setRaiseLimit(EXAMPLE.raiseLimit);
    setIterations(EXAMPLE.iterations);
    setProgressInterval(EXAMPLE.progressInterval);
    setStopExploitability(EXAMPLE.stopExploitability);
    setAlgorithm(EXAMPLE.algorithm);
    setFlop({});
    setTurn({});
    setRiver({});
    runSolve(EXAMPLE);
  };

  return (
    <div className="app">
      <header className="masthead">
        <span className="wordmark">Poker Solver</span>
        <p>Post-flop equilibrium for heads-up hold&rsquo;em.</p>
        <button type="button" className="link" onClick={loadExample}>
          load example
        </button>
      </header>

      <Section label="Board" note="Three cards for a flop, four for a turn, five for a river.">
        <BoardPicker value={board} onChange={setBoard} />
      </Section>

      <Section label="Ranges" note="IP acts last, OOP acts first. Drag across a grid to paint; the slider sets the weight.">
        <div className="ranges">
          <RangeEditor title="IP" value={rangeIp} onChange={setRangeIp} />
          <RangeEditor title="OOP" value={rangeOop} onChange={setRangeOop} />
        </div>
      </Section>

      <Section label="Stakes" note="Pot and stack share a unit — big blinds, chips, whatever you prefer.">
        <div className="fields">
          <Field label="Pot" value={pot} min={1} onChange={setPot} />
          <Field label="Effective stack" value={stack} min={1} onChange={setStack} />
        </div>
      </Section>

      <details>
        <summary>Options</summary>

        <div className="fields">
          <Field label="Raise cap" value={raiseLimit} min={0} onChange={setRaiseLimit} />
          <Field label="Iterations" value={iterations} min={1} onChange={setIterations} />
          <Field label="Report every" value={progressInterval} min={1} onChange={setProgressInterval} />
          <Field
            label="Stop below"
            value={stopExploitability}
            min={0}
            step={0.1}
            suffix="% pot"
            onChange={setStopExploitability}
          />
          <div className="field-wide">
            <label className="field-label" htmlFor="algorithm">
              Algorithm
            </label>
            <select
              id="algorithm"
              value={algorithm}
              onChange={(e) => setAlgorithm(e.target.value as SolveRequest["algorithm"])}
            >
              <option value="discounted_cfr">discounted_cfr (default)</option>
              <option value="pdcfr">pdcfr</option>
              <option value="pdcfr_plus">pdcfr_plus</option>
              <option value="pcfr_plus">pcfr_plus</option>
              <option value="cfr_plus">cfr_plus</option>
              <option value="cfr">cfr</option>
            </select>
          </div>
        </div>

        <div className="streets">
          <StreetForm street="flop" value={flop} onChange={setFlop} />
          <StreetForm street="turn" value={turn} onChange={setTurn} withDonk />
          <StreetForm street="river" value={river} onChange={setRiver} withDonk />
        </div>
      </details>

      <div className="solve-bar">
        <button type="button" className="solve-button" disabled={!ready || running} onClick={solve}>
          {running ? "Solving" : "Solve"}
        </button>
        {!ready && <span className="muted">Pick at least three board cards</span>}
        {error && <span className="error">{error}</span>}
      </div>

      <div ref={resultRef}>
        {job && (
          <Section
            label="Convergence"
            note="Exploitability is what a perfect opponent could win against this strategy. Zero is equilibrium."
          >
            <ProgressPanel
              state={job.state}
              events={events}
              onCancel={async () => setJob(await cancelJob(job.id))}
            />
          </Section>
        )}

        {job && solved && (
          <Section label="Strategy" note="Click an action to descend into it. Nodes load on demand.">
            <StrategyExplorer solveId={job.id} />
          </Section>
        )}
      </div>
    </div>
  );
}
