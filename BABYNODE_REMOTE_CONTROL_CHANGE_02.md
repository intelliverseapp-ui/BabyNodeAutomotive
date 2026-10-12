# BabyNode Remote Control – Change 02

Scope: Review 04 Finding 1, `MainActivity.kt` only.

## 1. What changed in MainActivity.kt
Terminal request outcomes seen by `observeTransport()` are now retained in memory and merged with the outcomes returned by `transport.close()` in `onDestroy()` before being saved.

## 2. Functions/properties added or changed
- Added property `receivedTerminalOutcomes: LinkedHashMap<Pair<Long, Long>, CarStatusEvent>` (key = sessionGeneration to requestId).
- Added function `retainTerminalOutcome(event: CarStatusEvent)`.
- Changed `observeTransport()`: calls `retainTerminalOutcome(event)` right after `statusBus.accept(event)`, before the `when`.
- Changed `onDestroy()`: close outcomes are retained through the same function, then `receivedTerminalOutcomes.values.toList()` is passed to `BabyNodeApp.saveTerminalRequestOutcomes()`.

## 3. CommandResponse retention
Every `CarStatusEvent.CommandResponse` is stored as the complete original event, keyed by `sessionGeneration to requestId`.

## 4. Request-specific Error retention
A `CarStatusEvent.Error` is stored (complete event, including throwable) only if `isRequestSpecific` is true and both `sessionGeneration` and `requestId` are non-null.

## 5. Exclusions
`CommandSent`, `Connected`, `Disconnected`, `DeviceApprovalRequired`, and any `Error` that is not request-specific (or lacks a sessionGeneration) fall through to `else -> return` / early return and are never stored. Only terminal events can enter the map, so a non-terminal event can never replace a terminal one.

## 6. Combining with transport.close()
In `onDestroy()`, after jobs are cancelled, `transport.close()` is called once and each returned event goes through `retainTerminalOutcome`. The resulting map values (received outcomes first, then new close outcomes, in insertion order) are saved if non-empty, using the existing persistence/error-logging path.

## 7. Duplicate prevention
The map is keyed by `(sessionGeneration, requestId)` and uses `putIfAbsent`, so the first terminal outcome for a request is kept and later duplicates (including from `close()`) are ignored.

## 8. No replay/resubmission
No command is sent, replayed, or resubmitted. Events are only stored and passed to the existing save function.

## 9. Not changed
`BabyNodeApp.kt`, transport code, command dispatch behavior, Compose/UI, and tests were not touched by this change. (Note: `BabyNodeApp.kt` already had uncommitted modifications from earlier work; this step did not alter it.)

## 10. Remaining limitations
- Retention is in memory; a process kill before `onDestroy()` still loses outcomes.
- Map is unbounded for the Activity lifetime (not pruned; grows by one entry per terminal request).
- Outcomes are not removed when transports are replaced; they persist across transports until destruction (keys include sessionGeneration).
- A first-wins policy is used; if `close()` reported a different terminal outcome for an already-answered request, it is dropped.
- Retained outcomes are not yet surfaced in the UI.
- `onDestroy()` is not guaranteed to run in all termination scenarios.

## 11. Tests that should eventually cover this
- CommandResponse received then Activity destroyed → saved.
- Request-specific Error received → saved with message/throwable intact.
- Non-request-specific Error, CommandSent, Connected/Disconnected → not saved.
- Error with null sessionGeneration → not saved.
- Duplicate same generation/requestId (live and from close()) → single entry.
- Same requestId in different generations → both kept.
- Received plus close() outcomes → combined in one save call.
- Empty set → no save call; save failure → logged.
- No command dispatch occurs during destroy.

## 12. Git diff summary (MainActivity.kt)
- `+` `receivedTerminalOutcomes` map and `retainTerminalOutcome()` (~38 lines).
- `+` call to `retainTerminalOutcome(event)` in `observeTransport()`.
- `~` `onDestroy()`: `transport.close()` results are retained, and `receivedTerminalOutcomes.values.toList()` replaces `transport.close()` as the saved list.
- Total: about 46 insertions, 1 deletion.
