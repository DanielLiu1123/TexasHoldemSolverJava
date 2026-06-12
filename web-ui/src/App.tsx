import { useCallback, useEffect, useRef, useState } from "react";
import { cancelJob, createSolve, getJob, getStrategy, subscribe } from "./api";
import { ranksFor } from "./poker";
import { BoardPicker } from "./components/BoardPicker";
import { ProgressPanel } from "./components/ProgressPanel";
import { RangeEditor } from "./components/RangeEditor";
import { StreetForm } from "./components/StreetForm";
import { StrategyExplorer } from "./components/StrategyExplorer";
import type { GameType, JobView, ProgressEvent, SolveRequest, StrategyNode, StreetSpec } from "./types";

const DEFAULT_RANGE =
  "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,KQ,KJ,KT,QJ,QT,JT,T9s,98s,87s,76s";

export function App() {
  const [game, setGame] = useState<GameType>("holdem");
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
  const [strategy, setStrategy] = useState<StrategyNode | null>(null);
  const [error, setError] = useState<string | null>(null);
  const unsubscribe = useRef<(() => void) | null>(null);

  const ranks = ranksFor(game);

  useEffect(() => () => unsubscribe.current?.(), []);

  const solve = useCallback(async () => {
    setError(null);
    setStrategy(null);
    setEvents([]);
    try {
      const request: SolveRequest = {
        game,
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
      };
      const created = await createSolve(request);
      setJob(created);
      unsubscribe.current?.();
      unsubscribe.current = subscribe(created.id, async (event) => {
        setEvents((prev) => [...prev, event]);
        if (event.type !== "progress") {
          const finished = await getJob(created.id);
          setJob(finished);
          if (event.type === "completed") setStrategy(await getStrategy(created.id));
        }
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [game, board, rangeIp, rangeOop, pot, stack, raiseLimit, iterations, progressInterval, stopExploitability, algorithm, flop, turn, river]);

  return (
    <div className="app">
      <header>
        <h1>Texas Holdem Solver</h1>
        <label>
          game
          <select
            value={game}
            onChange={(e) => {
              setGame(e.target.value as GameType);
              setBoard([]);
            }}
          >
            <option value="holdem">holdem</option>
            <option value="shortdeck">shortdeck</option>
          </select>
        </label>
      </header>

      <section className="panel">
        <h2>Board</h2>
        <BoardPicker ranks={ranks} value={board} onChange={setBoard} />
      </section>

      <section className="panel ranges">
        <RangeEditor title="IP range" ranks={ranks} value={rangeIp} onChange={setRangeIp} />
        <RangeEditor title="OOP range" ranks={ranks} value={rangeOop} onChange={setRangeOop} />
      </section>

      <section className="panel">
        <h2>Tree & solver</h2>
        <div className="param-row">
          <label>
            pot
            <input type="number" value={pot} onChange={(e) => setPot(Number(e.target.value))} />
          </label>
          <label>
            effective stack
            <input type="number" value={stack} onChange={(e) => setStack(Number(e.target.value))} />
          </label>
          <label>
            raise limit
            <input type="number" value={raiseLimit} onChange={(e) => setRaiseLimit(Number(e.target.value))} />
          </label>
          <label>
            iterations
            <input type="number" value={iterations} onChange={(e) => setIterations(Number(e.target.value))} />
          </label>
          <label>
            progress every
            <input
              type="number"
              value={progressInterval}
              onChange={(e) => setProgressInterval(Number(e.target.value))}
            />
          </label>
          <label>
            stop at expl. %
            <input
              type="number"
              step="0.1"
              value={stopExploitability}
              onChange={(e) => setStopExploitability(Number(e.target.value))}
            />
          </label>
          <label>
            algorithm
            <select value={algorithm} onChange={(e) => setAlgorithm(e.target.value as SolveRequest["algorithm"])}>
              <option value="discounted_cfr">discounted_cfr</option>
              <option value="pcfr_plus">pcfr_plus</option>
              <option value="cfr_plus">cfr_plus</option>
              <option value="cfr">cfr</option>
            </select>
          </label>
        </div>
        <div className="street-row">
          <StreetForm street="flop" value={flop} onChange={setFlop} />
          <StreetForm street="turn" value={turn} onChange={setTurn} withDonk />
          <StreetForm street="river" value={river} onChange={setRiver} withDonk />
        </div>
        <div className="solve-row">
          <button type="button" className="solve-button" disabled={board.length < 3} onClick={solve}>
            Solve
          </button>
          {board.length < 3 && <span className="muted">select a board first</span>}
          {error && <span className="error">{error}</span>}
        </div>
      </section>

      {job && (
        <section className="panel">
          <h2>Progress</h2>
          <ProgressPanel
            state={job.state}
            events={events}
            onCancel={async () => setJob(await cancelJob(job.id))}
          />
        </section>
      )}

      {strategy && (
        <section className="panel">
          <h2>Strategy</h2>
          <StrategyExplorer root={strategy} ranks={ranks} />
        </section>
      )}
    </div>
  );
}
