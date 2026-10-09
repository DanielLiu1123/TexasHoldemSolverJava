export interface StreetSpec {
  betSizes?: number[];
  raiseSizes?: number[];
  donkSizes?: number[];
  allin?: boolean;
}

export interface SolveRequest {
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
  algorithm?: "discounted_cfr" | "pdcfr" | "pdcfr_plus" | "pcfr_plus" | "cfr_plus" | "cfr";
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

/**
 * A single strategy-tree node, fetched on demand by path (the full post-flop tree is far too large
 * to ship at once — a flop solve dumps close to a gigabyte). The server returns just this node plus
 * the labels of its navigable children.
 */
export type StrategyNode = ActionStrategyNode | ChanceStrategyNode | TerminalStrategyNode;

export interface ActionStrategyNode {
  nodeType: "action_node";
  /** 0 = in position, 1 = out of position */
  player: 0 | 1;
  actions: string[];
  /** Action labels that lead to a further (non-terminal) node — i.e. are navigable. */
  childActions: string[];
  strategy: {
    actions: string[];
    /** combo (e.g. "AhKs") → probability per action, parallel to actions */
    strategy: Record<string, number[]>;
  };
}

export interface ChanceStrategyNode {
  nodeType: "chance_node";
  /** Dealt-card labels (e.g. "Ah"); navigate by appending one to the path. */
  cards: string[];
}

export interface TerminalStrategyNode {
  nodeType: "terminal";
}
