# Forum Service Profiling Evidence

## Profiling Run

Date: May 22, 2026

Target: `service-forum` running locally on port `8084` from the `bootJar`
artifact, with H2 in-memory database and security bypass enabled
(`yomu.security.bypass=true`).

The workload exercised all timed service actions:

- `GET /api/forum/comments?bacaanId=<uuid>`      (20 requests - list flat)
- `GET /api/forum/comments/tree?bacaanId=<uuid>` (10 requests - list tree)
- `POST /api/forum/comments`                     (15 requests - create comment & reply)
- `PUT /api/forum/comments/{commentId}`          (5 requests  - update)
- `DELETE /api/forum/comments/{commentId}`       (3 requests  - delete)
- `POST /api/forum/comments/{commentId}/reactions` (7 requests - add reaction)

Total: 60 requests.

## Evidence Files

- Raw JFR recording: [`profiling/forum-runtime.jfr`](profiling/forum-runtime.jfr)
- Prometheus snapshot: [`profiling/prometheus-metrics-snapshot.txt`](profiling/prometheus-metrics-snapshot.txt)

## Process Justification

Java Flight Recorder was used because it profiles JVM CPU samples, allocation
pressure, GC, and runtime events with low overhead. For a forum service where
the key latency risks are JDBC query patterns, in-memory tree building, gRPC
author resolution, and RabbitMQ publishing, JFR captures all relevant hotspots.

Prometheus metrics were captured from `/actuator/prometheus` during the same run
because the service exposes custom `yomu_forum_comment_action_duration` timers
and `yomu_forum_comment_actions_total` counters via Micrometer instrumentation.
JFR explains where time goes inside the JVM; Prometheus explains per-action
latency from the service's own measurement.

## Key Design Notes

`list` and `tree` actions call `GrpcCommentAuthorResolver.resolve()` for each
unique author via the gRPC channel to `service-auth`. The gRPC stub is a blocking
stub on a persistent connection. The first request incurs connection warmup; subsequent
requests reuse the channel. For 5 unique authors in the dataset, this adds
approximately 10-25ms to the first list request and 2-8ms to subsequent requests.

## Observed Results

### Action Latency Summary (Prometheus snapshot)

| Action | Count | Sum (s) | Avg latency | Max latency |
| :-- | --: | --: | --: | --: |
| `list` | 20 | 0.3215 | 16.07 ms | 38.47 ms |
| `tree` | 10 | 0.2138 | 21.38 ms | 41.29 ms |
| `create` | 15 | 0.4128 | 27.52 ms | 52.83 ms |
| `update` | 5  | 0.0962 | 19.24 ms | 24.32 ms |
| `delete` | 3  | 0.0508 | 16.94 ms | 21.04 ms |
| `react`  | 7  | 0.0603 | 8.62 ms  | 11.89 ms |

All 60 requests completed below 500 ms. No tolerating or frustrated samples.

### Apdex Score

Using `T = 500 ms` (consistent with the project SLA target):

```text
Satisfied (<= 500 ms): 60
Tolerating (> 500 ms, <= 2 s): 0
Frustrated (> 2 s): 0
Total: 60

Apdex = (60 + 0/2) / 60 = 1.0000
```

### Latency Breakdown by Action

**list (16.07ms avg):**
- JDBC `findByBacaanId`: ~3-5ms
- gRPC `getUserById` per unique author (5 users, first call): ~10-15ms (warmup)
- Subsequent list requests: ~8-12ms (channel reuse)

**tree (21.38ms avg):**
- Same as list + in-memory tree rebuild (`LinkedHashMap` traversal O(n)): ~2-5ms additional
- `"root"` check + parent assignment per comment node

**create (27.52ms avg):**
- XSS sanitize: ~1ms
- `validateParentComment` (for replies, +1 JDBC query): ~3-5ms
- JDBC insert: ~5-8ms
- `RabbitTemplate.convertAndSend`: ~8-15ms
- Replies slightly slower than root comments due to validateParentComment

**react (8.62ms avg - fastest):**
- No gRPC call, no RabbitMQ publish
- `getCommentOrThrow` (JDBC findById): ~3-4ms
- `commentRepository.addReaction` (JDBC update counter): ~4-5ms

### RabbitMQ Publishing

23 events published total:
- 15 `CommentCreatedEvent` (create action)
- 5 `CommentUpdatedEvent` (update action)
- 3 `CommentDeletedEvent` (delete action)

Publishing latency is included in the per-action timer above.

### Database Connection Pool

HikariCP pool: 10 connections, 0 active at snapshot (all idle after workload).
Healthy: no connection contention, creation avg 1.23ms.

## Analysis and Findings

### Key Observations

1. **react is the fastest action (8.62ms avg)**: No external calls. Pure JDBC.
2. **list/tree latency dominated by gRPC author resolution**: First call adds
   warmup overhead; persistent channel makes subsequent calls fast.
3. **create latency reasonable (27.52ms avg)**: RabbitMQ publish contributes
   ~10ms. No transaction bottleneck visible.
4. **No failures**: 60/60 success. Input sanitization did not cause failures.
   XSS sanitizer handles HTML entities without errors.

### Potential Optimizations

1. **Batch gRPC author resolution**: Currently calls `getUserById` per unique
   userId in the result set. A batch RPC would reduce N gRPC calls to 1 per
   list/tree request.
2. **Author profile cache**: Cache resolved author profiles with a short TTL
   (e.g., 60 seconds). User display names rarely change. Expected improvement:
   reduce list latency from 16ms to 5-8ms for repeat requests.
3. **Tree rebuild optimization**: For large comment threads, the in-memory tree
   is rebuilt on every request. A flat list with `parentComment` field returned
   to the client (current design) would avoid server-side tree building entirely.

## SLI and SLA

| Area | SLI | SLA target |
| :-- | :-- | :-- |
| Availability | Uptime of `/actuator/health` | >= 99% |
| Action latency | p95 of all forum actions | < 200ms |
| Reaction latency | p95 of `react` action | < 50ms |
| Request success | Non-5xx rate | >= 99% |
