# BabyNodeCAN Remote Control – Change 04

Review 04 Finding 1: keep the visible restored terminal outcome across further Activity recreation.

## 1. Source files changed
- `app/src/main/java/com/babynode/automotive/MainActivity.kt` only. `AutomotiveScreen.kt` and `VoiceInputSection.kt` needed no change.

## 2. Added or changed members
- Property `restoredTerminalOutcome: CarStatusEvent?` (`by mutableStateOf(null)`). It replaces the `remember`-based `restoredOutcome` local that was inside `setContent`, and it is the single source of truth for the displayed outcome.
- Companion keys: `STATE_RESTORED_OUTCOME`, `OUTCOME_TYPE`, `OUTCOME_TYPE_RESPONSE`, `OUTCOME_TYPE_ERROR`, `OUTCOME_GENERATION`, `OUTCOME_REQUEST_ID`, `OUTCOME_COMMAND`, `OUTCOME_VALUE`, `OUTCOME_STATUS`, `OUTCOME_MESSAGE`.
- `onCreate`: the local `initialRestoredOutcome` was renamed `consumedRestoredOutcome`. The property is now set to `restoreSavedTerminalOutcome(savedInstanceState) ?: consumedRestoredOutcome`. The `setContent` code passes `restoredTerminalOutcome` to `AutomotiveScreen` and dismissal sets it to null.
- `onSaveInstanceState`: also writes the outcome bundle when one is currently set.
- New `saveTerminalOutcome(event): Bundle?`.
- New `restoreSavedTerminalOutcome(savedInstanceState): CarStatusEvent?`.

## 3. How saved-instance-state preservation works
`onSaveInstanceState` puts a nested `Bundle` under `STATE_RESTORED_OUTCOME` only if `restoredTerminalOutcome` is currently non-null. The next `onCreate` reads it back into the same property. Nothing is written to `BabyNodeApp` or `SharedPreferences`, so persistent terminal-outcome storage is not touched.

## 4. Fields saved and restored
- `CommandResponse`: type, `sessionGeneration`, `requestId`, command name, optional command value, `status`.
- request-specific `Error`: type, `sessionGeneration` (only when non-null), `requestId`, command name, optional command value, `message`. The `throwable` is not saved.
- Other event types, and an `Error` missing `requestId` or `command`, are not saved.

## 5. Validation
The restore function runs inside `runCatching`. It returns null when any of these hold:
- the bundle is absent;
- `requestId` is missing;
- the command name is null or blank;
- the type is unknown;
- a response is missing its generation or has a blank status;
- an error has a blank message.

Any exception also returns null. A missing optional value restores as `value = null`. Failure produces no outcome and no exception.

## 6. Precedence
`restoreSavedTerminalOutcome(savedInstanceState) ?: consumedRestoredOutcome`. A valid saved outcome wins. Otherwise the newest valid outcome from `consumeTerminalRequestOutcomes()` is used (the same filter as before: `CommandResponse` or request-specific `Error`). `consumeTerminalRequestOutcomes()` is still called as before.

## 7. Dismissal and new workflow
Dismissal and "Speak Command" both invoke `onRestoredOutcomeDismissed`, which sets `restoredTerminalOutcome = null`. A null outcome is not written by `onSaveInstanceState`, so it is not in the next saved state and cannot return after recreation. The persisted store was already cleared when it was consumed.

## 8. No command submission
The new code only reads and writes `Bundle` values and builds status event objects. It does not create a `CarCommandRequest` and does not touch the dispatcher or transport. Dismissal and restoration cannot submit, retry, replay or resend anything.

## 9. Not changed
`BabyNodeApp.kt`, transport and Bluetooth behavior, dispatch, command mapping, sensitive confirmation, persistence serialization, debug/release visibility, UI wording and tests were not changed by this change. No build or tests were run.

## 10. Remaining limitations
- Saved-instance-state does not survive process death where the system drops state, or app removal from recents. Outcomes already consumed from persistent storage cannot be recovered in those cases.
- The `Error.throwable` is not preserved.
- The change is not compiled or tested.

## 11. Tests that should eventually cover this
- Save and restore round trip for `CommandResponse` and request-specific `Error`, including command values and null `sessionGeneration`.
- Saved state wins over a consumed persistent outcome.
- Malformed or partial bundles produce no outcome.
- `CommandSent`, connection events and non-request-specific errors are never saved.
- After dismissal or "Speak Command", the next saved state has no outcome.
- Nothing is written to persistent storage on recreation.
- No dispatcher or transport call happens in any of these flows.

## 12. Git diff summary
- `MainActivity.kt`: outcome bundle keys, a `restoredTerminalOutcome` property replacing the Compose-local state, save in `onSaveInstanceState`, new `saveTerminalOutcome` and `restoreSavedTerminalOutcome`, and precedence logic in `onCreate`.
- New file: `BABYNODE_REMOTE_CONTROL_CHANGE_04.md`.
