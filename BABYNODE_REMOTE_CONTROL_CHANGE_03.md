# BabyNodeCAN Remote Control – Change 03

Review 04 Finding 1: show restored terminal outcomes in the normal UI.

## 1. Source files changed
- `app/src/main/java/com/babynode/automotive/MainActivity.kt`
- `app/src/main/java/com/babynode/automotive/ui/AutomotiveScreen.kt`
- `app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt`

(MainActivity.kt and BabyNodeApp.kt already had uncommitted edits from earlier changes; this change touched only MainActivity.kt of those.)

## 2. Functions / properties / parameters added or changed
- `MainActivity.onCreate`: new local `initialRestoredOutcome: CarStatusEvent?`, derived from `restoredTerminalOutcomes` (the result of `consumeTerminalRequestOutcomes()`). It is the last element that is a `CommandResponse` or a request-specific `Error` (`isRequestSpecific`). `CommandSent`, connection events and non-request-specific errors are skipped.
- `setContent`: new Compose state `restoredOutcome` (`remember { mutableStateOf(initialRestoredOutcome) }`). Two new arguments are passed to `AutomotiveScreen`.
- `AutomotiveScreen`: new parameters `restoredTerminalOutcome: CarStatusEvent? = null` and `onRestoredOutcomeDismissed: () -> Unit = {}`. Both are forwarded to `VoiceInputSection`.
- `VoiceInputSection`: same two new parameters. It now renders the restored block, and the "Speak Command" click handler calls `onRestoredOutcomeDismissed()`.
- New private `restoredOutcomeMessage(outcome: CarStatusEvent?): String?` in `VoiceInputSection.kt`.

## 3. Data flow
`BabyNodeApp.consumeTerminalRequestOutcomes()` -> `MainActivity.onCreate` filter -> `restoredOutcome` state -> `AutomotiveScreen(restoredTerminalOutcome, onRestoredOutcomeDismissed)` -> `VoiceInputSection`. The status bus and event history are not used to infer the outcome. The existing `statusBus.replaceTransportStatus(...)` call was left unchanged.

## 4. Restored CommandResponse rendering
- Status `ok`: `Restored previous outcome: BabyNodeCAN accepted the request (status: ok). Command: <command>, request ID: <id>. This does not confirm that a vehicle operation completed.`
- Other status: `Restored previous outcome: BabyNodeCAN response status: <status>. Command: <command>, request ID: <id>. This does not confirm that a vehicle operation completed.`

## 5. Restored request-specific Error rendering
`Restored previous outcome: <original error message> Command: <command>, request ID: <id>.`
The message is shown verbatim, so outcome-unknown or "may have executed" wording is preserved. The UI does not add "failed" or "confirmed" wording. If an `Error` has no command or request ID, nothing is rendered.

## 6. Exact wording
- Prefix: "Restored previous outcome: "
- Accepted: "BabyNodeCAN accepted the request (status: ok)"
- Non-ok: "BabyNodeCAN response status: <status>"
- Suffix for responses: "This does not confirm that a vehicle operation completed."
- Button: "Dismiss restored outcome"

## 7. Command and request ID
`command.command` is the canonical command string. The request ID is shown as "request ID: <id>". Both are in the same text block.

## 8. Dismissal
The "Dismiss restored outcome" button calls `onRestoredOutcomeDismissed`. In `MainActivity` this only sets `restoredOutcome = null`.

## 9. What stops the display
- The user taps "Dismiss restored outcome".
- The user taps "Speak Command", which starts a new voice workflow.
- The Activity is recreated again. The outcome was already consumed from storage, so it is not restored a second time (see limitations).

## 10. Dismissal cannot submit, retry, replay or resend
The callback only assigns null to a UI state variable. It does not reference the dispatcher or transport. No `CarCommandRequest` is built from the restored outcome. Starting a voice workflow clears the display and does not replay anything.

## 11. Current-Activity matching
The `activeRequest`, `transportStatusForCurrentRequest`, `CarRequestEventMatcher` and request-state rendering code is unchanged. The restored block is separate and extra.

## 12. Not changed
`BabyNodeApp.kt`, transport and Bluetooth behavior, command mapping, dispatch, sensitive-command confirmation, persistence serialization, debug/release visibility rules, and tests were not changed by this change. No builds or tests were run.

## 13. Remaining limitations
- The restored outcome lives only in Compose `remember` state. A second recreation (such as a rotation) before dismissal loses it, because storage was already consumed.
- Only the newest outcome is shown. This assumes the list order from `consumeTerminalRequestOutcomes()` is chronological; the code takes the last matching element.
- The restored text is not covered by accessibility or localization work.
- The change is not compiled or tested yet.

## 14. Tests that should eventually cover this
- Restored `CommandResponse` "ok" shows accepted wording, command and request ID, and the no-vehicle-completion disclaimer.
- Restored `CommandResponse` with a non-ok status.
- Restored request-specific `Error` preserves the message verbatim, including outcome-unknown wording.
- `CommandSent` and non-request-specific `Error` are not shown.
- Newest outcome selection with multiple restored events.
- Visible with `debugToolsVisible = false`.
- The dismiss button hides the outcome and the dispatcher receives no calls.
- "Speak Command" clears the outcome without dispatcher calls.
- Current-Activity request rendering is unchanged.

## 15. Git diff summary
- `MainActivity.kt`: filter for the newest terminal outcome, `restoredOutcome` state, two new arguments to `AutomotiveScreen`.
- `AutomotiveScreen.kt`: two new parameters, forwarded to `VoiceInputSection`.
- `VoiceInputSection.kt`: two new parameters, restored-outcome block with a dismiss button, dismissal on "Speak Command", `restoredOutcomeMessage()` helper.
- New file: `BABYNODE_REMOTE_CONTROL_CHANGE_03.md`.
