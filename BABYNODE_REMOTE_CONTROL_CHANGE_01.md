# BabyNode Remote Control – Change 01

Scope: `app/src/main/java/com/babynode/automotive/BabyNodeApp.kt` only (Review 04 Finding 1, first step).
Based on the current working tree. No builds or tests were run.

## 1. What changed

- `saveTerminalRequestOutcomes` now persists `CarStatusEvent.CommandResponse` in addition to request-specific `CarStatusEvent.Error`.
- Every persisted entry now carries an `event_type` discriminator.
- `consumeTerminalRequestOutcomes` uses `event_type` to rebuild the correct `CarStatusEvent` subtype.
- Two private constants were added to the companion object: `EVENT_TYPE_ERROR = "error"` and `EVENT_TYPE_COMMAND_RESPONSE = "command_response"`.

## 2. Functions changed

- `BabyNodeApp.saveTerminalRequestOutcomes(events: List<CarStatusEvent>): Boolean`
- `BabyNodeApp.consumeTerminalRequestOutcomes(): List<CarStatusEvent>`
- Companion object: constants added only.

`clearTerminalRequestOutcomes` and all other members are unchanged.

## 3. Persistence before the change

- Input was filtered with `filterIsInstance<CarStatusEvent.Error>()` and `isRequestSpecific`.
- Each `Error` was stored as a JSON object with `message`, `session_generation`, `request_id`, `command` and an optional `value`.
- Errors lacking a generation, request ID or command were skipped.
- There was no event type field. Restore only produced `CarStatusEvent.Error`.
- Restore required a valid request ID, a nonblank command and a nonblank message.

## 4. Persistence now

- Every event in the list is inspected with a `when`:
  - `Error`: stored only if `isRequestSpecific` and generation, request ID and command are all non-null (same rules as before).
  - `CommandResponse`: always stored (its generation, request ID, command and status are non-nullable).
  - Any other event type is skipped.
- The result is written as a JSON array to `terminal_request_outcomes` with `commit()`, as before. The return value is still the `commit()` result.
- The stored value is overwritten on each save, as before.

## 5. `CarStatusEvent.Error`

Serialized fields:

- `event_type` = `"error"`
- `message`
- `session_generation`
- `request_id`
- `command`
- `value` (only if the command value is non-null)

Restore builds `CarStatusEvent.Error(message, sessionGeneration, requestId, command = CanonicalCommand(command, value))`. The `throwable` is not persisted, so it is `null` after restore, as before.

## 6. `CarStatusEvent.CommandResponse`

Serialized fields:

- `event_type` = `"command_response"`
- `status`
- `session_generation`
- `request_id`
- `command`
- `value` (only if the command value is non-null)

Restore builds `CarStatusEvent.CommandResponse(sessionGeneration, requestId, command = CanonicalCommand(command, value), status)`.

## 7. JSON fields

| Field | Status |
|---|---|
| `event_type` | **Added**, required on every entry (`"error"` or `"command_response"`) |
| `status` | **Added**, only on `command_response` entries |
| `message` | Unchanged, now only on `error` entries |
| `session_generation`, `request_id`, `command`, `value` | Unchanged, written for both types |

## 8. Restore validation

For each entry:

- `session_generation` and `request_id` must be present and readable as long.
- `command` and `event_type` must be present and readable as string.
- `value` is read as a string if present.
- `BabyNodeProtocol.validateRequestId(requestId)` must be true.
- `command` must be nonblank.
- For `error`, `message` must be present and nonblank.
- For `command_response`, `status` must be present and nonblank.

An entry failing a blank or request-ID check is dropped. An entry with an unrecognized `event_type` produces no event.

## 9. Fail-closed behavior

- The whole parse runs inside `runCatching`, with `getOrElse { emptyList() }`.
- Invalid JSON, a non-object array element, or a missing or mistyped required field throws. This yields an empty list for the whole stored payload, including entries that came before the bad one.
- Entries with a blank command, blank message or status, an invalid request ID, or an unknown `event_type` are skipped individually.
- Entries saved before this change have no `event_type`, so `getString("event_type")` throws. Such legacy payloads restore as an empty list.
- Nothing is added to the result unless validation passes.

## 10. No command submission or replay

- `BabyNodeApp` still contains no transport reference, dispatch or submit logic. Nothing was added.
- Restore only constructs `CarStatusEvent` data objects (`Error`, `CommandResponse`) and returns them as a list.
- Anything that acts on those events would be in other files, which were not changed. This change does not cause any command to be sent.

## 11. Consume-once preserved

- `consumeTerminalRequestOutcomes` is unchanged in its flow. It reads the stored string, returns an empty list if absent, then calls `clearTerminalRequestOutcomes()` **before** parsing.
- If clearing fails, it returns an empty list.
- Parsing happens only after a successful clear, so a second call returns an empty list, including when the payload was malformed.

## 12. Remaining limitations

- `Error.throwable` is not persisted.
- Save does not validate the request ID or blank fields. Validation happens only on restore.
- `CommandResponse.sessionGeneration` defaults to `0L` in its type. This code persists whatever value it holds, and `0L` is not rejected on restore. Only the request ID is validated.
- A single malformed entry discards the whole payload, while some invalid entries are skipped individually. This is inconsistent.
- `getString` in `org.json` may coerce non-string JSON values such as numbers, so type strictness is not guaranteed.
- Unknown `event_type` values are dropped silently, with no logging.
- Legacy payloads without `event_type` are discarded.
- Each save overwrites previous unconsumed outcomes.
- No caller was changed. Whether callers pass `CommandResponse` events into save, or handle restored `CommandResponse` events in the UI, is outside this change.
- No tests exist yet for the new behavior.

## 13. Tests to add or update

Android `SharedPreferences` / `org.json` tests (Robolectric or instrumented):

- Round-trip `Error` with and without a command value: generation, request ID, command, value and message preserved.
- Round-trip `CommandResponse` with and without a command value: generation, request ID, command, value and status preserved.
- Mixed list of both types is restored in order.
- Non-request-specific `Error` is not saved.
- Other event types are not saved.
- `event_type` is written as `"error"` and `"command_response"`.
- Unknown `event_type` produces no event.
- Missing `event_type` (legacy payload) returns an empty list.
- Invalid request ID is skipped.
- Blank command, blank message and blank status are skipped.
- Missing `status` on `command_response` and missing `message` on `error` return an empty list.
- Malformed JSON returns an empty list.
- Consume-once: the second call returns an empty list, and the key is removed even for malformed data.
- Restore performs no transport interaction (use a fake transport to assert zero submissions).
- Update any existing tests that build the stored JSON without `event_type`.

## 14. Git diff summary (`BabyNodeApp.kt`)

`git diff --stat`: 1 file changed, 122 insertions(+), 51 deletions(-).

- `saveTerminalRequestOutcomes`: removed the `filterIsInstance<Error>` and `isRequestSpecific` prefilter. Added a `when` over `Error` and `CommandResponse` that sets the type-specific field (`message` or `status`). Added a shared write of `event_type`, `session_generation`, `request_id`, `command` and `value`.
- `consumeTerminalRequestOutcomes`: reads `event_type`. `message` is no longer read up front. Validation now covers request ID and command, then branches by type to build `Error` or `CommandResponse` with its own nonblank check.
- Companion object: added `EVENT_TYPE_ERROR` and `EVENT_TYPE_COMMAND_RESPONSE`.
