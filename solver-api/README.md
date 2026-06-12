# solver-api

The embedded HTTP API around the solver core ([ADR 0001](../docs/adr/0001-unified-http-api-replaces-swing-and-jpype.md)).
Javalin 7 on virtual threads; JSON via the project-wide Jackson 3 mapper.

## Run

```bash
./gradlew :solver-api:run --args="--port 8080 --resources riversolver/src/test/resources"
```

`--resources` must point at a directory containing `compairer/card5_dic_sorted.txt`
(holdem) and/or `compairer/card5_dic_sorted_shortdeck.txt` (shortdeck). Dictionaries
load lazily on the first solve of each game type and stay resident.

## Endpoints

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/v1/solves` | Submit a solve job → `202` with the job id |
| GET | `/api/v1/solves/{id}` | Job status, recorded progress events |
| GET | `/api/v1/solves/{id}/events` | Live progress as SSE (send `Accept: text/event-stream`) |
| GET | `/api/v1/solves/{id}/strategy` | Strategy JSON once completed (`409` before that) |
| DELETE | `/api/v1/solves/{id}` | Request cancellation (takes effect at the next iteration) |
| GET | `/api/v1/health` | Process status and loaded dictionaries |

## Example

```bash
curl -s -X POST localhost:8080/api/v1/solves -H 'Content-Type: application/json' -d '{
  "game": "shortdeck",
  "board": "Kd,Jd,Td,7s,8s",
  "rangeIp":  "AA,KK,QQ,JJ,TT,99,88,AK,AQ,KQ,JT",
  "rangeOop": "AA,KK,QQ,JJ,TT,99,88,AK,AQ,KQ,JT",
  "pot": 10,
  "effectiveStack": 95,
  "iterations": 100,
  "progressInterval": 10,
  "stopExploitability": 0.5
}'

curl -N -H 'Accept: text/event-stream' localhost:8080/api/v1/solves/<id>/events
curl -s localhost:8080/api/v1/solves/<id>/strategy > strategy.json
```

Optional request fields and defaults: `game` (holdem), `raiseLimit` (5),
`iterations` (100), `progressInterval` (10), `algorithm` (discounted_cfr; also
cfr, cfr_plus), `monteCarlo` (none), `threads` (-1 = all cores),
`stopExploitability` (0 = run all iterations), and per-street sizing
`flop`/`turn`/`river`: `{"betSizes":[50],"raiseSizes":[50],"donkSizes":[],"allin":true}`
in percent of the pot (defaults: 50% bets/raises, all-in on turn and river).
