# BabyNode Release Factory Change 01

Fix for Review 04 Finding 3: preventing `MainActivity`'s test transport-factory override from being usable in release builds while preserving existing debug instrumentation tests.

No builds or tests were run as instructed.

---

## 1. Files Changed

1. `app/src/main/java/com/babynode/automotive/MainActivity.kt`
2. `app/src/androidTest/java/com/babynode/automotive/MainActivityExternalIntentTest.kt`
3. `app/src/androidTest/java/com/babynode/automotive/MainActivityLifecycleTest.kt`
4. `app/src/androidTest/java/com/babynode/automotive/MainActivityRetryTest.kt`
5. `BABYNODE_RELEASE_FACTORY_CHANGE_01.md` (this report)

No other files were modified. No files were moved between source sets.

---

## 2. Properties and Methods Added, Removed, or Changed

### `app/src/main/java/com/babynode/automotive/MainActivity.kt`

- **`transportFactory` (changed)**:
  - Previously: `internal var transportFactory: () -> CarCanTransport = { CarCanBusBluetooth() }`
  - Now: `private var transportFactory: (() -> CarCanTransport)? = null`
  - Visibility changed from `internal` to `private`, removing public/internal mutation access. Default value is now `null`.
- **`installTransportFactoryForTest` (added)**:
  - Signature: `internal fun installTransportFactoryForTest(factory: () -> CarCanTransport)`
  - Rejects release execution with `check(BuildConfig.DEBUG)` before storing the override.
- **`resetTransportFactoryForTest` (changed)**:
  - Signature: `internal fun resetTransportFactoryForTest()`
  - Rejects release execution with `check(BuildConfig.DEBUG)`.
  - Resets `transportFactory` to `null`.
- **`createTransport` (added)**:
  - Signature: `private fun createTransport(): CarCanTransport`
  - In debug builds, invokes `transportFactory?.invoke()` if present; otherwise defaults to `CarCanBusBluetooth()`. In release builds (`BuildConfig.DEBUG == false`), ignores any stored value and always returns `CarCanBusBluetooth()`.
- **Call-site updates in `MainActivity`**:
  - `onCreate` line 330: changed from `transportFactory()` to `createTransport()`.
  - `replaceTransport` line 754: changed from `transportFactory()` to `createTransport()`.

### Instrumentation Test Classes

- **`MainActivityExternalIntentTest.kt`**:
  - Replaced `MainActivity.transportFactory = { transport }` with `MainActivity.installTransportFactoryForTest { transport }`.
- **`MainActivityLifecycleTest.kt`**:
  - Replaced `MainActivity.transportFactory = { transports.removeFirst() }` with `MainActivity.installTransportFactoryForTest { transports.removeFirst() }`.
- **`MainActivityRetryTest.kt`**:
  - Replaced `MainActivity.transportFactory = { transport }` with `MainActivity.installTransportFactoryForTest { transport }`.

---

## 3. How Direct transportFactory Mutation Was Removed

In `MainActivity.kt`, the companion object property `transportFactory` was declared `internal var`, allowing any class within the module (including application or test code) to reassign the factory at will.

The property was modified to `private var transportFactory: (() -> CarCanTransport)? = null`. Callers outside `MainActivity` can no longer read or write `MainActivity.transportFactory` directly; compilation will reject any external assignment.

---

## 4. How the Guarded Test Installation Method Works

`MainActivity.installTransportFactoryForTest(factory: () -> CarCanTransport)` provides a controlled entry point for debug instrumentation tests:

```kotlin
internal fun installTransportFactoryForTest(
    factory: () -> CarCanTransport
) {
    check(
        BuildConfig.DEBUG
    ) {
        "Transport factory test installation is unavailable in release builds"
    }

    transportFactory =
        factory
}
```

The method is scoped `internal` to the module and guards execution with `check(BuildConfig.DEBUG)`.

---

## 5. How Release Invocation Fails Before Storing an Override

In `installTransportFactoryForTest`:
1. `check(BuildConfig.DEBUG) { ... }` is evaluated as the very first statement.
2. If `BuildConfig.DEBUG` is `false` (in release builds), Kotlin's `check()` throws `IllegalStateException("Transport factory test installation is unavailable in release builds")`.
3. The execution terminates immediately by throwing the exception before reaching `transportFactory = factory`. Thus, no override is stored in memory.

---

## 6. How Reset Behavior Is Protected

`resetTransportFactoryForTest()` is also guarded by `check(BuildConfig.DEBUG)`:

```kotlin
internal fun resetTransportFactoryForTest() {
    check(
        BuildConfig.DEBUG
    ) {
        "Transport factory reset is unavailable in release builds"
    }

    transportFactory =
        null
}
```

If invoked in a release build, it immediately throws `IllegalStateException` and cannot be used as an unintended release manipulation hook. In debug tests, it sets `transportFactory` back to `null`, ensuring test isolation during `setUp` and `tearDown`.

---

## 7. How Normal Production Creation Remains Fixed to CarCanBusBluetooth

Production transport creation occurs via `createTransport()`:

```kotlin
private fun createTransport(): CarCanTransport {
    val factory =
        if (BuildConfig.DEBUG) {
            transportFactory
        } else {
            null
        }

    return factory?.invoke() ?: CarCanBusBluetooth()
}
```

- In release builds (`BuildConfig.DEBUG == false`): `factory` is always `null` unconditionally, completely bypassing `transportFactory` even if an override were somehow present. It evaluates `null?.invoke() ?: CarCanBusBluetooth()`, guaranteeing that `CarCanBusBluetooth()` is always instantiated.
- In debug builds (`BuildConfig.DEBUG == true`): if no test override is installed (`transportFactory == null`), it falls back to `CarCanBusBluetooth()`. Only when an override was installed via `installTransportFactoryForTest` does it invoke the test factory.

---

## 8. How the Three Instrumentation Tests Were Updated

The three existing instrumentation test classes had their direct property assignments updated to call `MainActivity.installTransportFactoryForTest`:

1. **`MainActivityExternalIntentTest.kt`**:
   ```kotlin
   MainActivity.installTransportFactoryForTest {
       transport
   }
   ```
2. **`MainActivityLifecycleTest.kt`**:
   ```kotlin
   MainActivity.installTransportFactoryForTest {
       transports.removeFirst()
   }
   ```
3. **`MainActivityRetryTest.kt`**:
   ```kotlin
   MainActivity.installTransportFactoryForTest {
       transport
   }
   ```

Their existing `@Before setUp()` and `@After tearDown()` calls to `MainActivity.resetTransportFactoryForTest()` remain unchanged and function properly in debug test builds.

---

## 9. Confirmation That Unrelated Production Behavior Was Unchanged

The following areas were preserved without modification:
- Bluetooth transport implementation (`CarCanBusBluetooth.kt`)
- Wire protocol and session binding (`BabyNodeProtocol.kt`, `CarCanBusBluetoothSessionBindingTest.kt`)
- Command dispatching logic (`CarCommandDispatcher.kt`)
- Reconnect and retry configuration/state machine (`MainActivity.kt`)
- Activity lifecycle handling and terminal outcome restoration/consumption (`MainActivity.kt`)
- External URI / Assist / Process-Text intent parsing (`MainActivity.kt`)
- UI and screen layout composables (`AutomotiveScreen.kt`, `VoiceInputSection.kt`, `CommandTestSection.kt`)
- All other unit and instrumentation tests

---

## 10. Tests That Should Be Run

Once builds and test executions are permitted, the following test suites should be executed:

1. **Instrumentation Tests**:
   - `app/src/androidTest/java/com/babynode/automotive/MainActivityExternalIntentTest.kt`
   - `app/src/androidTest/java/com/babynode/automotive/MainActivityLifecycleTest.kt`
   - `app/src/androidTest/java/com/babynode/automotive/MainActivityRetryTest.kt`
   - `app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothConformanceTest.kt`
   - `app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothSessionBindingTest.kt`
2. **Unit Tests**:
   - `app/src/test/java/com/babynode/automotive/CarCommandDispatcherTest.kt`
   - `app/src/test/java/com/babynode/automotive/BabyNodeProtocolTest.kt`
   - `app/src/test/java/com/babynode/automotive/BabyNodePeerContractTest.kt`
3. **Release Build Verification**:
   - Assemble release APK (e.g. `./gradlew assembleRelease`) to verify that `BuildConfig.DEBUG == false` compiles cleanly and eliminates test transport hooks.

---

## 11. Remaining Limitations

- `installTransportFactoryForTest` remains compiled into the release DEX because `MainActivity.kt` resides in `main` source set; however, it fails closed at runtime by immediately throwing `IllegalStateException` before storing any factory reference, and `createTransport()` independently ignores any override when `BuildConfig.DEBUG == false`.
- If ProGuard / R8 rules strip or inline `BuildConfig.DEBUG`, standard dead-code elimination can remove the unreachable override branch in release builds.

---

## 12. Git Diff Summary

```diff
diff --git a/app/src/androidTest/java/com/babynode/automotive/MainActivityExternalIntentTest.kt b/app/src/androidTest/java/com/babynode/automotive/MainActivityExternalIntentTest.kt
index 4261e4c..bcb0fa9 100644
--- a/app/src/androidTest/java/com/babynode/automotive/MainActivityExternalIntentTest.kt
+++ b/app/src/androidTest/java/com/babynode/automotive/MainActivityExternalIntentTest.kt
@@ -33,7 +33,7 @@ class MainActivityExternalIntentTest {
         val transport =
             RecordingIntentTransport()
 
-        MainActivity.transportFactory = {
+        MainActivity.installTransportFactoryForTest {
             transport
         }
 
diff --git a/app/src/androidTest/java/com/babynode/automotive/MainActivityLifecycleTest.kt b/app/src/androidTest/java/com/babynode/automotive/MainActivityLifecycleTest.kt
index 529c263..8a08825 100644
--- a/app/src/androidTest/java/com/babynode/automotive/MainActivityLifecycleTest.kt
+++ b/app/src/androidTest/java/com/babynode/automotive/MainActivityLifecycleTest.kt
@@ -100,7 +100,7 @@ class MainActivityLifecycleTest {
                 )
             }
 
-        MainActivity.transportFactory = {
+        MainActivity.installTransportFactoryForTest {
             transports.removeFirst()
         }
 
diff --git a/app/src/androidTest/java/com/babynode/automotive/MainActivityRetryTest.kt b/app/src/androidTest/java/com/babynode/automotive/MainActivityRetryTest.kt
index 3fdc159..ee9acbe 100644
--- a/app/src/androidTest/java/com/babynode/automotive/MainActivityRetryTest.kt
+++ b/app/src/androidTest/java/com/babynode/automotive/MainActivityRetryTest.kt
@@ -48,7 +48,7 @@ class MainActivityRetryTest {
                     4
             )
 
-        MainActivity.transportFactory = {
+        MainActivity.installTransportFactoryForTest {
             transport
         }
 
diff --git a/app/src/main/java/com/babynode/automotive/MainActivity.kt b/app/src/main/java/com/babynode/automotive/MainActivity.kt
index 0907fe3..726e04d 100644
--- a/app/src/main/java/com/babynode/automotive/MainActivity.kt
+++ b/app/src/main/java/com/babynode/automotive/MainActivity.kt
@@ -45,15 +45,43 @@ class MainActivity : ComponentActivity() {
         private const val TAG =
             "BNA_MainActivity"
 
-        internal var transportFactory:
-            () -> CarCanTransport = {
-                CarCanBusBluetooth()
+        private var transportFactory:
+            (() -> CarCanTransport)? =
+            null
+
+        internal fun installTransportFactoryForTest(
+            factory: () -> CarCanTransport
+        ) {
+            check(
+                BuildConfig.DEBUG
+            ) {
+                "Transport factory test installation is unavailable in release builds"
             }
 
+            transportFactory =
+                factory
+        }
+
         internal fun resetTransportFactoryForTest() {
-            transportFactory = {
-                CarCanBusBluetooth()
+            check(
+                BuildConfig.DEBUG
+            ) {
+                "Transport factory reset is unavailable in release builds"
             }
+
+            transportFactory =
+                null
+        }
+
+        private fun createTransport(): CarCanTransport {
+            val factory =
+                if (BuildConfig.DEBUG) {
+                    transportFactory
+                } else {
+                    null
+                }
+
+            return factory?.invoke() ?: CarCanBusBluetooth()
         }
 
         private const val TRANSPORT_NAME =
@@ -302,7 +330,7 @@ class MainActivity : ComponentActivity() {
         enableEdgeToEdge()
 
         val initialTransport =
-            transportFactory()
+            createTransport()
 
         transport =
             initialTransport
@@ -726,7 +754,7 @@ class MainActivity : ComponentActivity() {
         )
 
         val newTransport =
-            transportFactory()
+            createTransport()
 
         transport =
             newTransport
```
