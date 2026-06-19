import { useCallback, useEffect, useRef, useState } from "react";
import { cancelJob, createSolve, getJob, getStrategy, subscribe } from "./api";
import { ranksFor } from "./poker";
import { BoardPicker } from "./components/BoardPicker";
import { ProgressPanel } from "./components/ProgressPanel";
import { RangeEditor } from "./components/RangeEditor";
import { StreetForm } from "./components/StreetForm";
import { StrategyExplorer } from "./components/StrategyExplorer";
import { Collapsible, Field, InfoTip, Section, Segmented } from "./components/ui";
import type { GameType, JobView, ProgressEvent, SolveRequest, StrategyNode, StreetSpec } from "./types";

const DEFAULT_RANGE =
  "AA,KK,QQ,JJ,TT,99,88,77,66,AK,AQ,AJ,AT,A9,A8,KQ,KJ,KT,QJ,QT,JT,T9s,98s,87s,76s";

/** Plain-language name of the street implied by a board of n cards. */
const STREET_OF = ["", "", "", "翻牌 flop", "转牌 turn", "河牌 river"];

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
  const resultRef = useRef<HTMLDivElement | null>(null);

  const ranks = ranksFor(game);
  const ready = board.length >= 3;
  const running = job?.state === "RUNNING";

  useEffect(() => () => unsubscribe.current?.(), []);

  const runSolve = useCallback(async (request: SolveRequest) => {
    setError(null);
    setStrategy(null);
    setEvents([]);
    try {
      const created = await createSolve(request);
      setJob(created);
      requestAnimationFrame(() => resultRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
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
  }, []);

  const solve = () =>
    runSolve({
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
    });

  /** Fill a complete, valid scenario and solve it immediately — the zero-thinking path. */
  const loadExample = () => {
    setGame("holdem");
    setBoard(["Ah", "Kd", "7c"]);
    setRangeIp(DEFAULT_RANGE);
    setRangeOop(DEFAULT_RANGE);
    setPot(20);
    setStack(90);
    setRaiseLimit(5);
    setIterations(100);
    setProgressInterval(10);
    setStopExploitability(0.5);
    setAlgorithm("discounted_cfr");
    setFlop({});
    setTurn({});
    setRiver({});
    runSolve({
      game: "holdem",
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
    });
  };

  return (
    <div className="app">
      <header className="hero">
        <div className="hero-mark">♠ ♥ ♦ ♣</div>
        <h1>
          德州扑克 <em>Solver</em>
        </h1>
        <p className="hero-tagline">
          浏览器里的翻牌后 GTO 求解器 —— 给定公共牌、双方范围和筹码，算出双方接近纳什均衡的混合策略。
        </p>
      </header>

      <div className="guide">
        <div className="guide-text">
          <strong>第一次用？</strong> 按下面 4 步从上往下填即可；术语旁的
          <span className="infotip-static">?</span> 可以看解释。想直接看效果，就点右边按钮。
        </div>
        <button type="button" className="example-btn" onClick={loadExample}>
          ▶ 一键运行示例
          <small>填好一个标准场景并立即求解</small>
        </button>
      </div>

      <Section
        step={1}
        title="选择玩法"
        done={true}
        hint="标准德州用 52 张牌；短牌（六加）去掉 2–5，只留 36 张。"
      >
        <Segmented<GameType>
          value={game}
          onChange={(g) => {
            setGame(g);
            setBoard([]);
          }}
          options={[
            { value: "holdem", label: "标准德州", hint: "52 张 · Hold'em" },
            { value: "shortdeck", label: "短牌", hint: "36 张 · Short-deck" },
          ]}
        />
      </Section>

      <Section
        step={2}
        title="选公共牌"
        done={ready}
        hint={
          <>
            点牌面选出 <b>3–5 张</b> 公共牌：3 张 = 翻牌，4 张 = 转牌，5 张 = 河牌。
            {board.length > 0 && <span className="step-state"> 已选 {board.length} 张（{STREET_OF[board.length]}）</span>}
          </>
        }
      >
        <BoardPicker ranks={ranks} value={board} onChange={setBoard} />
      </Section>

      <Section
        step={3}
        title="设置双方范围"
        hint={
          <>
            每位玩家可能持有的起手牌集合。<b>IP</b> = 有利位（后手行动），<b>OOP</b> = 不利位（先手行动）。
            在网格上拖动涂抹来增删，或直接编辑下方文本。
          </>
        }
      >
        <div className="ranges">
          <RangeEditor
            title="IP 范围（有利位 / 后手）"
            ranks={ranks}
            value={rangeIp}
            onChange={setRangeIp}
          />
          <RangeEditor
            title="OOP 范围（不利位 / 先手）"
            ranks={ranks}
            value={rangeOop}
            onChange={setRangeOop}
          />
        </div>
      </Section>

      <Section
        step={4}
        title="筹码与下注尺度"
        hint="底池和有效筹码用同一种单位（如大盲数）。下注尺度按底池百分比给出，可留默认。"
      >
        <div className="fields">
          <Field
            label="底池 pot"
            hint="进入本街时底池已有的筹码"
            value={pot}
            min={1}
            onChange={setPot}
          />
          <Field
            label="有效筹码 stack"
            hint="较浅一方剩余可下注的筹码"
            value={stack}
            min={1}
            onChange={setStack}
          />
        </div>
        <div className="streets">
          <StreetForm street="flop" value={flop} onChange={setFlop} />
          <StreetForm street="turn" value={turn} onChange={setTurn} withDonk />
          <StreetForm street="river" value={river} onChange={setRiver} withDonk />
        </div>
      </Section>

      <Collapsible title="高级设置" subtitle="迭代次数、收敛阈值、算法 —— 默认值通常够用">
        <div className="fields">
          <Field
            label="加注上限"
            hint="一条线上最多允许的加注次数"
            value={raiseLimit}
            min={0}
            onChange={setRaiseLimit}
          />
          <Field
            label="迭代次数"
            hint="越多越精确，也越慢"
            value={iterations}
            min={1}
            onChange={setIterations}
          />
          <Field
            label="每隔几次报告"
            hint="多少次迭代更新一次进度"
            value={progressInterval}
            min={1}
            onChange={setProgressInterval}
          />
          <Field
            label="提前停止阈值"
            hint="可利用度低于此值即停止"
            value={stopExploitability}
            min={0}
            step={0.1}
            suffix="% 底池"
            onChange={setStopExploitability}
          />
          <div className="field">
            <label className="field-label" htmlFor="algo">
              算法
            </label>
            <div className="field-input">
              <select
                id="algo"
                value={algorithm}
                onChange={(e) => setAlgorithm(e.target.value as SolveRequest["algorithm"])}
              >
                <option value="discounted_cfr">discounted_cfr（默认 · 收敛最快）</option>
                <option value="pcfr_plus">pcfr_plus</option>
                <option value="cfr_plus">cfr_plus</option>
                <option value="cfr">cfr</option>
              </select>
            </div>
            <p className="field-hint">不确定就用默认。</p>
          </div>
        </div>
      </Collapsible>

      <div className="solve-bar">
        <button type="button" className="solve-button" disabled={!ready || running} onClick={solve}>
          {running ? "求解中…" : "开始求解"}
        </button>
        {!ready && <span className="muted">请先在第 2 步选出至少 3 张公共牌</span>}
        {error && <span className="error">出错了：{error}</span>}
      </div>

      <div ref={resultRef}>
        {job && (
          <section className="panel result-panel">
            <div className="panel-head">
              <h2>求解进度</h2>
              <InfoTip>
                可利用度（exploitability）衡量当前策略离最优有多远，以底池百分比计。越低越好，低于约
                0.5% 通常就可当作最优策略使用。
              </InfoTip>
            </div>
            <ProgressPanel
              state={job.state}
              events={events}
              onCancel={async () => setJob(await cancelJob(job.id))}
            />
          </section>
        )}

        {strategy && (
          <section className="panel result-panel">
            <div className="panel-head">
              <h2>策略结果</h2>
              <InfoTip>
                点动作按钮可深入到下一个决策点；网格里每个格子的颜色按各动作的概率比例填充。
              </InfoTip>
            </div>
            <p className="legend">
              <span className="legend-item">
                <span className="dot" style={{ background: "#3b82c4" }} /> 弃牌 fold
              </span>
              <span className="legend-item">
                <span className="dot" style={{ background: "#2f855a" }} /> 过牌/跟注 check·call
              </span>
              <span className="legend-item">
                <span className="dot" style={{ background: "#e67e22" }} /> 下注/加注 bet·raise（越深=越大）
              </span>
            </p>
            <StrategyExplorer root={strategy} ranks={ranks} />
          </section>
        )}
      </div>

      <footer className="app-foot">
        本地求解 · 数据不出本机 · <code>discounted_cfr</code> 默认收敛最快
      </footer>
    </div>
  );
}
