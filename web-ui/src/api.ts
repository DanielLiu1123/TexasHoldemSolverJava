import type { JobView, ProgressEvent, SolveRequest, StrategyNode } from "./types";

const BASE = "/api/v1";

async function expectOk(res: Response): Promise<Response> {
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const body = (await res.json()) as { error?: string };
      if (body.error) detail = body.error;
    } catch {
      // keep statusText
    }
    throw new Error(detail);
  }
  return res;
}

export async function createSolve(request: SolveRequest): Promise<JobView> {
  const res = await fetch(`${BASE}/solves`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  return (await expectOk(res)).json();
}

export async function getJob(id: string): Promise<JobView> {
  return (await expectOk(await fetch(`${BASE}/solves/${id}`))).json();
}

export async function cancelJob(id: string): Promise<JobView> {
  return (await expectOk(await fetch(`${BASE}/solves/${id}`, { method: "DELETE" }))).json();
}

/**
 * Fetches a single strategy node addressed by `path` (a list of edge labels from the root; empty =
 * root). One node per request — the tree is walked lazily rather than downloaded whole.
 */
export async function getStrategyNode(id: string, path: string[]): Promise<StrategyNode> {
  const query = path.length > 0 ? `?path=${encodeURIComponent(path.join(","))}` : "";
  return (await expectOk(await fetch(`${BASE}/solves/${id}/strategy${query}`))).json();
}

/**
 * Subscribes to a job's SSE stream. Returns an unsubscribe function. The server closes the
 * stream after a terminal event; we also close client-side to stop EventSource auto-reconnect.
 */
export function subscribe(id: string, onEvent: (event: ProgressEvent) => void): () => void {
  const source = new EventSource(`${BASE}/solves/${id}/events`);
  const handler = (message: MessageEvent<string>) => {
    const event = JSON.parse(message.data) as ProgressEvent;
    if (event.type !== "progress") source.close();
    onEvent(event);
  };
  for (const type of ["progress", "completed", "failed", "cancelled"]) {
    source.addEventListener(type, handler);
  }
  return () => source.close();
}
