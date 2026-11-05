package fans.crosspoint.pal.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fans.crosspoint.pal.ble.AwaitWakeState
import fans.crosspoint.pal.ble.CompanionConnectionManager
import fans.crosspoint.pal.ble.CompanionError
import fans.crosspoint.pal.ble.LinkPhase
import fans.crosspoint.pal.ble.ScannedDevice
import fans.crosspoint.pal.domain.ButtonEventSnapshot
import fans.crosspoint.pal.domain.CompanionDeviceSnapshot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Immutable UI state of the Home screen. */
data class HomeUiState(
    val device: CompanionDeviceSnapshot?,
    val lastEvent: ButtonEventSnapshot?,
    val phase: LinkPhase,
    val isScanning: Boolean,
    val scannedDevices: List<ScannedDevice>,
    val needsPermissions: Boolean,
    val awaitWake: AwaitWakeState = AwaitWakeState.Idle,
) {
    val hasDevice: Boolean
        get() = device != null

    val isConnecting: Boolean
        get() = phase in CONNECTING_PHASES

    companion object {
        val CONNECTING_PHASES = setOf(
            LinkPhase.Bonding,
            LinkPhase.Connecting,
            LinkPhase.NegotiatingMtu,
            LinkPhase.Discovering,
            LinkPhase.Subscribing,
            LinkPhase.Handshaking,
        )
    }
}

/** One-shot feedback events emitted by the Home screen. */
sealed interface HomeUiEvent {
    data class Error(val error: CompanionError) : HomeUiEvent
}

class HomeViewModel(private val connectionManager: CompanionConnectionManager) : ViewModel() {

    private val permissionsRequested = MutableStateFlow(false)

    /** Which BLE action the pending permission dialog should resume. */
    private enum class PendingAction { Scan, AwaitWake }

    private var pendingAction: PendingAction? = null

    private val _events = Channel<HomeUiEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val initial = HomeUiState(
        device = connectionManager.device.value,
        lastEvent = connectionManager.lastButtonEvent.value,
        phase = connectionManager.linkPhase.value,
        isScanning = connectionManager.isScanning.value,
        scannedDevices = connectionManager.scannedDevices.value,
        needsPermissions = false,
        awaitWake = connectionManager.awaitWake.value,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        connectionManager.device,
        connectionManager.lastButtonEvent,
        connectionManager.linkPhase,
        connectionManager.isScanning,
        connectionManager.scannedDevices,
        connectionManager.awaitWake,
        permissionsRequested,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        HomeUiState(
            device = values[0] as CompanionDeviceSnapshot?,
            lastEvent = values[1] as ButtonEventSnapshot?,
            phase = values[2] as LinkPhase,
            isScanning = values[3] as Boolean,
            scannedDevices = values[4] as List<ScannedDevice>,
            awaitWake = values[5] as AwaitWakeState,
            needsPermissions = values[6] as Boolean,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = initial,
    )

    init {
        connectionManager.errorEvents
            .onEach { error -> _events.send(HomeUiEvent.Error(error)) }
            .launchIn(viewModelScope)
    }

    /** Permissions the BLE workflow requires on this Android version. */
    val requiredPermissions: List<String>
        get() = connectionManager.requiredPermissions()

    /** Connect pressed: request permissions first, then open the scan. */
    fun onConnectPressed() {
        if (connectionManager.missingPermissions().isNotEmpty()) {
            pendingAction = PendingAction.Scan
            permissionsRequested.update { true }
            return
        }
        connectionManager.startScan()
    }

    /** Await-next-wake pressed: request permissions first, then wait. */
    fun onAwaitWakePressed() {
        if (connectionManager.missingPermissions().isNotEmpty()) {
            pendingAction = PendingAction.AwaitWake
            permissionsRequested.update { true }
            return
        }
        connectionManager.awaitNextWake()
    }

    /** Result of the runtime permission dialog. */
    fun onPermissionsResult(allGranted: Boolean) {
        permissionsRequested.value = false
        val action = pendingAction
        pendingAction = null
        if (!allGranted) {
            _events.trySend(HomeUiEvent.Error(CompanionError.PermissionsMissing))
            return
        }
        when (action) {
            PendingAction.Scan -> connectionManager.startScan()
            PendingAction.AwaitWake -> connectionManager.awaitNextWake()
            null -> Unit
        }
    }

    fun cancelScan() {
        permissionsRequested.value = false
        connectionManager.stopScan()
    }

    /** Cancel pressed on the diagnostic wait. */
    fun cancelAwaitWake() {
        permissionsRequested.value = false
        connectionManager.cancelAwaitWake()
    }

    /** The user picked a device from the scan results. */
    fun onDeviceSelected(device: ScannedDevice) {
        connectionManager.stopScan()
        connectionManager.connect(address = device.address, name = device.name)
    }

    fun onReconnectPressed() {
        connectionManager.reconnect()
    }

    fun onDisconnectPressed() {
        connectionManager.disconnect()
    }
}
