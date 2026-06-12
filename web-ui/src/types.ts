export type GameType = "holdem" | "shortdeck";

export interface StreetSpec {
  betSizes?: number[];
  raiseSizes?: number[];
  donkSizes?: number[];
  allin?: boolean;
}

export interface SolveRequest {
  game: GameType;
  board: string;
  rangeIp: string;
  rangeOop: string;
  pot: number;
  effectiveStack: number;
  raiseLimit?: number;
  flop?: StreetSpec;
  turn?: StreetSpec;
  river?: StreetSpec;
  flopOop?: StreetSpec;
  turnOop?: StreetSpec;
  riverOop?: StreetSpec;
  iterations?: number;
  progressInterval?: number;
  algorithm?: "discounted_cfr" | "pcfr_plus" | "cfr_plus" | "cfr";
  monteCarlo?: "none" | "public";
  threads?: number;
  stopExploitability?: number;
}

export interface ProgressEvent {
  type: "progress" | "completed" | "failed" | "cancelled";
  iteration: number;
  exploitability: number;
  elapsedMs: number;
  error: string | null;
}

export type JobState = "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";

export interface JobView {
  id: string;
  state: JobState;
  error: string | null;
  events: ProgressEvent[];
}

/** Strategy tree as produced by GameTree.dumps. Terminal/showdown nodes are absent. */
export type StrategyNode = ActionStrategyNode | ChanceStrategyNode;

export interface ActionStrategyNode {
  node_type: "action_node";
  /** 0 = in position, 1 = out of position */
  player: 0 | 1;
  actions: string[];
  children?: Record<string, StrategyNode>;
  strategy: {
    actions: string[];
    /** combo (e.g. "AhKs") → probability per action, parallel to actions */
    strategy: Record<string, number[]>;
  };
}

export interface ChanceStrategyNode {
  node_type: "chance_node";
  deal_number: number;
  deal_cards: Record<string, StrategyNode>;
}
