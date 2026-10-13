# BabyNode Bluetooth Approval Test 01

Integration coverage change for Review 04 Section 10 item 4: making the Bluetooth adapter, bonded-device selection, explicit approval, pinned identity, and reconnect boundary testable without weakening production approval rules.

No builds or tests were run as instructed. SingleCAN-WROOM32 was not modified.

---

## 1. Files Changed

### Production Source Files
- `app/src/main/java/com/babynode/automotive/BluetoothDeviceProvider.kt` (new)
- `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt` (modified)
- `app/src/main/java/com/babynode/automotive/RfcommConnectionFactory.kt` (modified)
- `app/src/main/java/com/babynode/automotive/AndroidRfcommConnectionFactory.kt` (modified)

### Test Files
- `app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothApprovalTest.kt` (new)

### Documentation Files
- `BABYNODE_BLUETOOTH_APPROVAL_TEST_01.md` (this report)

---

## 2. Provider Abstraction and Production Implementation

In `app/src/main/java/com/babynode/automotive/BluetoothDeviceProvider.kt`:

### `BluetoothDeviceIdentity`
```kotlin
data class BluetoothDeviceIdentity(
    val name: String?,
    val address: String,
    val bluetoothDevice: BluetoothDevice? = null
)
```
Represents a candidate bonded device identity containing its name, address, and an optional reference to the platform `BluetoothDevice`.

### `BluetoothDeviceProvider`
```kotlin
interface BluetoothDeviceProvider {
    val isSupported: Boolean
    val isEnabled: Boolean
    val bondedDevices: Set<BluetoothDeviceIdentity>
    fun cancelDiscovery(): Boolean
}
```
Exposes only the adapter state and device queries required by `CarCanBusBluetooth`.

### `AndroidBluetoothDeviceProvider`
```kotlin
object AndroidBluetoothDeviceProvider : BluetoothDeviceProvider {
    private val adapter: BluetoothAdapter?
        get() = BluetoothAdapter.getDefaultAdapter()

    override val isSupported: Boolean
        get() = adapter != null

    @get:SuppressLint("MissingPermission")
    override val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    @get:SuppressLint("MissingPermission")
    override val bondedDevices: Set<BluetoothDeviceIdentity>
        get() {
            val devices = adapter?.bondedDevices ?: return emptySet()
            return devices.map { device ->
                BluetoothDeviceIdentity(
                    name = device.name,
                    address = device.address,
                    bluetoothDevice = device
                )
            }.toSet()
        }

    @SuppressLint("MissingPermission")
    override fun cancelDiscovery(): Boolean {
        return adapter?.cancelDiscovery() ?: false
    }
}
```

---

## 3. How Production Still Uses the Real Android Bluetooth Stack

`CarCanBusBluetooth` constructor uses default arguments:
```kotlin
class CarCanBusBluetooth(
    private val deviceProvider:
        BluetoothDeviceProvider =
        AndroidBluetoothDeviceProvider,
    private val connectionFactory:
        RfcommConnectionFactory =
        AndroidRfcommConnectionFactory,
    private val responseTimeoutMillis:
        Long =
        RESPONSE_TIMEOUT_MILLIS
) : CarCanTransport
```
In production:
- No arguments need to be provided; callers invoke `CarCanBusBluetooth()`.
- `deviceProvider` defaults to `AndroidBluetoothDeviceProvider`, which accesses `BluetoothAdapter.getDefaultAdapter()`, `adapter.isEnabled`, `adapter.bondedDevices`, and `adapter.cancelDiscovery()`.
- `connectionFactory` defaults to `AndroidRfcommConnectionFactory`, which extracts `device.bluetoothDevice` from `BluetoothDeviceIdentity` and creates a real `AndroidRfcommConnection` with `bluetoothDevice.createRfcommSocketToServiceRecord(serviceUuid)`.

---

## 4. How Tests Represent Bonded Devices Without Constructing BluetoothDevice

In Android SDK, `BluetoothDevice` cannot be instantiated directly in tests. `BluetoothDeviceIdentity` separates the identity (`name: String?` and `address: String`) from the platform object (`bluetoothDevice: BluetoothDevice? = null`).

In tests:
```kotlin
BluetoothDeviceIdentity(
    name = "BabyNodeCAN",
    address = "02:00:00:00:00:01"
)
```
This is supplied through `FakeBluetoothDeviceProvider(bondedDevices = setOf(...))` without reflection, mocks, or platform device instances.

---

## 5. Selection Rules and Boundary Behavior

All existing selection logic in `findApprovedBabyNodeCanDevice` was preserved:

1. **Unsupported Bluetooth**: `!deviceProvider.isSupported` emits `CarStatusEvent.Error("Bluetooth is not supported")` and returns early. The connection factory is not called.
2. **Disabled Bluetooth**: `!deviceProvider.isEnabled` emits `CarStatusEvent.Error("Bluetooth is disabled")` and returns early. The connection factory is not called.
3. **Zero Matching Candidates**: If no bonded device is named `"BabyNodeCAN"`, emits `CarStatusEvent.Error("BabyNodeCAN is not paired")`. The connection factory is not called.
4. **Multiple Matching Candidates**: If more than one bonded device is named `"BabyNodeCAN"` and no device is approved, emits `CarStatusEvent.Error("More than one paired device is named BabyNodeCAN")`. The connection factory is not called.
5. **Unique Unapproved Candidate**: Emits `CarStatusEvent.DeviceApprovalRequired(deviceName, deviceAddress)`. Returns `null` without calling `connectionFactory.create()`.
6. **Missing Pinned Identity**: If an address is approved via `BabyNodeApp`, but that address is not present among `deviceProvider.bondedDevices`, emits `CarStatusEvent.Error("The approved BabyNodeCAN device is no longer paired")`. Returns `null` without calling the connection factory.
7. **Changed Name at Pinned Address**: If the approved address is bonded, but its name is no longer `"BabyNodeCAN"`, emits `CarStatusEvent.Error("The approved Bluetooth device does not identify as BabyNodeCAN")`. Returns `null` without calling the connection factory.
8. **Same-Name Device at Different Address**: If `"BabyNodeCAN"` appears at a new address while a previously approved address is missing, the pinned device lookup fails closed with `"The approved BabyNodeCAN device is no longer paired"`. It does not fall through to unapproved discovery or automatic approval.

---

## 6. Explicit Approval Storage Through BabyNodeApp

Tests invoke:
```kotlin
BabyNodeApp.instance.approveBabyNodeCanAddress(address)
```
This runs the production validation against `BLUETOOTH_ADDRESS_PATTERN` (`^[0-9A-F]{2}(:[0-9A-F]{2}){5}$`) and saves the normalized address to private shared preferences under `approved_babynodecan_bluetooth_address`.

Tests clear approval state during `@Before setUp()` and `@After tearDown()` via:
```kotlin
BabyNodeApp.instance.clearApprovedBabyNodeCanAddress()
```

---

## 7. How Reconnect Continues Using the Pinned Address

When `transport.disconnect()` is called:
- The RFCOMM socket and streams are closed.
- The active socket generation is invalidated.
- The approved address remains in `BabyNodeApp.instance.approvedBabyNodeCanAddress()`.

When `transport.connect()` is called again:
- `findApprovedBabyNodeCanDevice` queries `approvedBabyNodeCanAddress()`, finding the stored address.
- It finds the bonded device matching that address and confirms its name is `"BabyNodeCAN"`.
- It invokes `connectionFactory.create(device, SPP_UUID)` with the same pinned device identity.

---

## 8. How Connection Factory Proves Which Identity Was Selected

`RecordingRfcommConnectionFactory` records every `BluetoothDeviceIdentity` passed to `create(device, serviceUuid)`:
```kotlin
class RecordingRfcommConnectionFactory : RfcommConnectionFactory {
    val createdDevices = Collections.synchronizedList(mutableListOf<BluetoothDeviceIdentity>())
    ...
    override fun create(device: BluetoothDeviceIdentity, serviceUuid: UUID): RfcommConnection {
        createdDevices.add(device)
        ...
    }
}
```
Tests assert:
- Prior to approval: `assertTrue(factory.createdDevices.isEmpty())`.
- After approval: `assertEquals(VALID_ADDRESS_A, factory.createdDevices.single().address)`.

---

## 9. Confirmation of No Automatic Approval Path

- No code was added that automatically approves addresses.
- All approval checks continue requiring an address explicitly saved in `BabyNodeApp`.
- No bypass or backdoor flags were introduced.

---

## 10. Confirmation That Unrelated Transport and Protocol Behavior Was Unchanged

The following components and behaviors remain untouched:
- Peer session binding and wire protocol (`BabyNodeProtocol.kt`)
- Command serialization and validation (`CarCanBusBluetooth.sendCommand`)
- Pending command correlation and timeout tracking (`CarCanBusBluetooth`)
- Socket-generation guards and race prevention (`SocketGeneration.kt`)
- Debug test connection seam (`installConnectionForTest`)
- Release build guards (`BuildConfig.DEBUG`)
- Sensitive command confirmation and command dispatching (`CarCommandDispatcher.kt`)
- Activity lifecycle and retry state machine (`MainActivity.kt`)

---

## 11. Tests Added

In `app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothApprovalTest.kt`:

1. `unsupportedBluetoothFailsClosed`: Verifies `CarStatusEvent.Error("Bluetooth is not supported")` when `isSupported = false` and connection factory is not invoked.
2. `disabledBluetoothFailsClosed`: Verifies `CarStatusEvent.Error("Bluetooth is disabled")` when `isEnabled = false` and connection factory is not invoked.
3. `zeroMatchingCandidatesFailsClosed`: Verifies `CarStatusEvent.Error("BabyNodeCAN is not paired")` when bonded devices contain no device named `"BabyNodeCAN"`.
4. `multipleMatchingCandidatesAreRejected`: Verifies rejection when multiple bonded devices share the name `"BabyNodeCAN"`.
5. `uniqueUnapprovedCandidateEmitsDeviceApprovalRequiredAndDoesNotCallConnectionFactory`: Verifies `CarStatusEvent.DeviceApprovalRequired` with the device name and address; confirms connection factory is not invoked.
6. `afterApprovalReconnectSelectsPinnedDeviceAndFactoryReceivesOnlyPinnedIdentity`: Verifies that approving the address via `BabyNodeApp.instance.approveBabyNodeCanAddress()` allows reconnect to succeed, emitting `Connected` and passing only the pinned identity to the connection factory.
7. `disconnectFollowedByReconnectContinuesUsingPinnedIdentity`: Verifies that disconnecting and reconnecting maintains the pinned device binding.
8. `missingPinnedIdentityFailsClosed`: Verifies `CarStatusEvent.Error("The approved BabyNodeCAN device is no longer paired")` when the pinned address is not among bonded devices.
9. `changedNameAtPinnedAddressFailsClosed`: Verifies `CarStatusEvent.Error("The approved Bluetooth device does not identify as BabyNodeCAN")` when the bonded device at the approved address changes its name.
10. `sameNameDeviceAtDifferentAddressCannotReplaceApprovedDevice`: Verifies that an unapproved replacement candidate at a different address does not replace the pinned address.
11. `pinnedAddressSelectedEvenWhenOtherCandidatesExist`: Verifies that when an approved address is pinned, connect succeeds even if other bonded devices named `"BabyNodeCAN"` are present.

All test fixtures use valid locally administered Bluetooth addresses (`02:00:00:00:00:01` and `02:00:00:00:00:02`).

---

## 12. Remaining Limitations

- Real hardware RFCOMM Bluetooth sockets are not opened during instrumentation tests running against `FakeBluetoothDeviceProvider` and `FakeRfcommConnection`.
- Android runtime permissions (`BLUETOOTH_CONNECT`) are handled at the `MainActivity` / OS boundary; tests execute with simulated provider state.

---

## 13. Git Diff Summary

```diff
diff --git a/app/src/main/java/com/babynode/automotive/AndroidRfcommConnectionFactory.kt b/app/src/main/java/com/babynode/automotive/AndroidRfcommConnectionFactory.kt
index 852f47e..72187d9 100644
--- a/app/src/main/java/com/babynode/automotive/AndroidRfcommConnectionFactory.kt
+++ b/app/src/main/java/com/babynode/automotive/AndroidRfcommConnectionFactory.kt
@@ -1,7 +1,6 @@
 package com.babynode.automotive
 
 import android.annotation.SuppressLint
-import android.bluetooth.BluetoothDevice
 import java.util.UUID
 
 /**
@@ -12,11 +11,17 @@ object AndroidRfcommConnectionFactory :
 
     @SuppressLint("MissingPermission")
     override fun create(
-        device: BluetoothDevice,
+        device: BluetoothDeviceIdentity,
         serviceUuid: UUID
     ): RfcommConnection {
+        val bluetoothDevice =
+            device.bluetoothDevice
+                ?: throw IllegalArgumentException(
+                    "AndroidRfcommConnectionFactory requires an Android BluetoothDevice"
+                )
+
         return AndroidRfcommConnection(
-            device.createRfcommSocketToServiceRecord(
+            bluetoothDevice.createRfcommSocketToServiceRecord(
                 serviceUuid
             )
         )
diff --git a/app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt b/app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt
index 21b084c..0f92edc 100644
--- a/app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt
+++ b/app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt
@@ -1,8 +1,6 @@
 package com.babynode.automotive
 
 import android.annotation.SuppressLint
-import android.bluetooth.BluetoothAdapter
-import android.bluetooth.BluetoothDevice
 import android.util.Log
 import kotlinx.coroutines.CancellationException
 import kotlinx.coroutines.CompletableDeferred
@@ -31,6 +29,9 @@ import java.util.concurrent.ConcurrentHashMap
 import java.util.concurrent.atomic.AtomicLong
 
 class CarCanBusBluetooth(
+    private val deviceProvider:
+        BluetoothDeviceProvider =
+        AndroidBluetoothDeviceProvider,
     private val connectionFactory:
         RfcommConnectionFactory =
         AndroidRfcommConnectionFactory,
@@ -312,10 +313,7 @@ class CarCanBusBluetooth(
                 RfcommConnection? = null
 
             try {
-                val adapter =
-                    BluetoothAdapter.getDefaultAdapter()
-
-                if (adapter == null) {
+                if (!deviceProvider.isSupported) {
                     statusFlow.emit(
                         CarStatusEvent.Error(
                             message =
@@ -326,7 +324,7 @@ class CarCanBusBluetooth(
                     return
                 }
 
-                if (!adapter.isEnabled) {
+                if (!deviceProvider.isEnabled) {
                     statusFlow.emit(
                         CarStatusEvent.Error(
                             message =
@@ -339,10 +337,10 @@ class CarCanBusBluetooth(
 
                 val device =
                     findApprovedBabyNodeCanDevice(
-                        adapter
+                        deviceProvider
                     ) ?: return
 
-                adapter.cancelDiscovery()
+                deviceProvider.cancelDiscovery()
 
                 newSocket =
                     connectionFactory.create(
@@ -501,12 +499,11 @@ class CarCanBusBluetooth(
         }
     }
 
-    @SuppressLint("MissingPermission")
     private suspend fun findApprovedBabyNodeCanDevice(
-        adapter: BluetoothAdapter
-    ): BluetoothDevice? {
+        deviceProvider: BluetoothDeviceProvider
     ): BluetoothDeviceIdentity? {
         val bondedDevices =
-            adapter.bondedDevices
+            deviceProvider.bondedDevices
 
         val approvedAddress =
             BabyNodeApp
diff --git a/app/src/main/java/com/babynode/automotive/RfcommConnectionFactory.kt b/app/src/main/java/com/babynode/automotive/RfcommConnectionFactory.kt
index e280cbf..6d2dec3 100644
--- a/app/src/main/java/com/babynode/automotive/RfcommConnectionFactory.kt
+++ b/app/src/main/java/com/babynode/automotive/RfcommConnectionFactory.kt
@@ -1,6 +1,5 @@
 package com.babynode.automotive
 
-import android.bluetooth.BluetoothDevice
 import java.util.UUID
 
 /**
@@ -9,7 +8,7 @@ import java.util.UUID
 fun interface RfcommConnectionFactory {
 
     fun create(
-        device: BluetoothDevice,
+        device: BluetoothDeviceIdentity,
         serviceUuid: UUID
     ): RfcommConnection
 }
```
