package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.actuators.AlarmActuatorManager
import com.example.data.model.SecurityEvent
import com.example.data.model.SiteAlarmState
import com.example.data.model.SiteCameraConfig
import com.example.data.model.SiteSensorState
import com.example.di.AppContainer
import com.example.domain.model.PerimeterZone
import com.example.network.ConnectionType
import com.example.network.DiscoveredNode
import com.example.network.LocalMeshManager
import com.example.network.LocalNsdManager
import com.example.security.KeystoreManager
import com.example.ui.navigation.Screen
import com.example.ui.theme.SystemAlarmState
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(
    val appContainer: AppContainer
) : ViewModel() {

    val authRepository = appContainer.authRepository
    val deviceRoleRepository = appContainer.deviceRoleRepository
    val siteAlarmRepository = appContainer.siteAlarmRepository
    val actuatorManager: AlarmActuatorManager = appContainer.alarmActuatorManager
    val keystoreManager: KeystoreManager = appContainer.keystoreManager
    val localNsdManager: LocalNsdManager = appContainer.localNsdManager
    val localMeshManager: LocalMeshManager = appContainer.localMeshManager

    private val _currentUser = MutableStateFlow<FirebaseUser?>(authRepository.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _deviceRole = MutableStateFlow<String?>(appContainer.deviceManager.getCachedRole())
    val deviceRole: StateFlow<String?> = _deviceRole.asStateFlow()

    private val _isSelectingRole = MutableStateFlow(false)
    val isSelectingRole: StateFlow<Boolean> = _isSelectingRole.asStateFlow()

    private val _currentScreen = MutableStateFlow(Screen.DASHBOARD)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _alarmState = MutableStateFlow(SystemAlarmState.DISARMED)
    val alarmState: StateFlow<SystemAlarmState> = _alarmState.asStateFlow()

    val siteState: StateFlow<SiteAlarmState> = siteAlarmRepository.observeSiteState("bodega_central")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SiteAlarmState()
        )

    val sensorState: StateFlow<SiteSensorState> = siteAlarmRepository.observeSensorState("bodega_central")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SiteSensorState()
        )

    val cameraConfig: StateFlow<SiteCameraConfig> = siteAlarmRepository.observeCameraConfig("bodega_central")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SiteCameraConfig()
        )

    val securityEvents: StateFlow<List<SecurityEvent>> = siteAlarmRepository.observeEvents("bodega_central", 30)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val zones: StateFlow<List<PerimeterZone>> = appContainer.zonesRepository.observeZones()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val connectionType: StateFlow<ConnectionType> = localMeshManager.connectionType
    val discoveredNodes: StateFlow<List<DiscoveredNode>> = localNsdManager.discoveredNodes

    val deviceModel: String
        get() = deviceRoleRepository.getLocalDeviceModel()

    init {
        viewModelScope.launch {
            siteAlarmRepository.initializeSiteIfNeeded("bodega_central")
        }

        // Sincronizar estado global del sitio con el tema visual de acento único
        viewModelScope.launch {
            siteState.collect { state ->
                _alarmState.value = when (state.estado.lowercase()) {
                    "armada" -> SystemAlarmState.ARMED
                    "alarma" -> SystemAlarmState.ALARM
                    else -> SystemAlarmState.DISARMED
                }

                if (_deviceRole.value == "nodo") {
                    if (state.estado == "alarma" && state.sirenaActiva) {
                        actuatorManager.startSiren()
                    } else {
                        actuatorManager.stopSiren()
                    }
                }
            }
        }

        // Observar estado de autenticación
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                _currentUser.value = user
                if (user != null) {
                    observeUserDeviceRole(user.uid)
                } else {
                    _deviceRole.value = null
                }
            }
        }

        // Configurar listener para comandos locales en el nodo
        localMeshManager.onLocalCommandReceived = { action, pin, detail ->
            when (action) {
                "arm" -> armSystem(viaLocal = true)
                "disarm" -> if (pin != null) disarmSystem(pin, viaLocal = true)
                "simulate_pir" -> simulateSensorTrigger("pir", detail ?: "Movimiento PIR por LAN", viaLocal = true)
                "simulate_reed" -> simulateSensorTrigger("reed", detail ?: "Apertura portón por LAN", viaLocal = true)
                "silence_siren" -> if (pin != null) silenceActuators(pin, true, false, viaLocal = true)
                "turn_off_strobe" -> if (pin != null) silenceActuators(pin, false, true, viaLocal = true)
            }
        }

        // Iniciar servicio correspondiente según rol
        viewModelScope.launch {
            _deviceRole.collect { role ->
                if (role == "nodo") {
                    localNsdManager.stopDiscovery()
                    localMeshManager.disconnectClient()
                    localMeshManager.startLocalServer(8765)
                    localNsdManager.registerNodeService(8765, "Nodo Bodega (${deviceModel.take(12)})")
                } else if (role == "panel") {
                    localMeshManager.stopLocalServer()
                    localNsdManager.unregisterNodeService()
                    localNsdManager.startDiscovery()
                }
            }
        }
    }

    private fun observeUserDeviceRole(userId: String) {
        viewModelScope.launch {
            deviceRoleRepository.observeDeviceRole(userId).collect { roleInfo ->
                _deviceRole.value = roleInfo?.role
            }
        }
    }

    fun selectScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    fun armSystem(viaLocal: Boolean = false) {
        if (!viaLocal && _deviceRole.value == "panel") {
            localMeshManager.sendLocalCommand("arm")
        }
        viewModelScope.launch {
            siteAlarmRepository.armSystem("bodega_central", _deviceRole.value ?: "panel")
        }
    }

    fun disarmSystem(pinInput: String, viaLocal: Boolean = false) {
        if (!viaLocal && _deviceRole.value == "panel") {
            localMeshManager.sendLocalCommand("disarm", pin = pinInput)
        }
        viewModelScope.launch {
            siteAlarmRepository.disarmSystem("bodega_central", pinInput, _deviceRole.value ?: "panel")
        }
    }

    fun silenceActuators(pinInput: String, silenceSiren: Boolean, turnOffStrobe: Boolean, viaLocal: Boolean = false) {
        if (!viaLocal && _deviceRole.value == "panel") {
            val act = if (silenceSiren) "silence_siren" else "turn_off_strobe"
            localMeshManager.sendLocalCommand(act, pin = pinInput)
        }
        viewModelScope.launch {
            siteAlarmRepository.silenceActuators("bodega_central", pinInput, silenceSiren, turnOffStrobe)
        }
    }

    fun simulateSensorTrigger(tipo: String, detalle: String, viaLocal: Boolean = false) {
        if (!viaLocal && _deviceRole.value == "panel") {
            val act = if (tipo == "pir") "simulate_pir" else "simulate_reed"
            localMeshManager.sendLocalCommand(act, detail = detalle)
        }
        viewModelScope.launch {
            siteAlarmRepository.recordSensorTrigger("bodega_central", tipo, detalle, _deviceRole.value ?: "panel")
        }
    }

    fun onCameraMotionDetected() {
        viewModelScope.launch {
            siteAlarmRepository.recordSensorTrigger(
                siteId = "bodega_central",
                sensorType = "pir",
                detail = "Movimiento detectado por cámara frontal",
                origen = "nodo"
            )
        }
    }

    fun updateSchedule(inicio: String, fin: String, autoArm: Boolean) {
        viewModelScope.launch {
            siteAlarmRepository.updateSchedule(
                siteId = "bodega_central",
                inicio = inicio,
                fin = fin,
                dias = listOf(1L, 2L, 3L, 4L, 5L),
                armadoAutomatico = autoArm
            )
        }
    }

    fun updateCameraConfig(modoPir: String, sensibilidad: Long, tiempoConfirmacion: Long) {
        viewModelScope.launch {
            siteAlarmRepository.updateCameraConfig("bodega_central", modoPir, sensibilidad, tiempoConfirmacion)
        }
    }

    fun updateCameraStatus(status: String) {
        viewModelScope.launch {
            siteAlarmRepository.updateCameraStatus("bodega_central", status)
        }
    }

    fun fallbackToSimulatedPir() {
        viewModelScope.launch {
            siteAlarmRepository.updateCameraConfig("bodega_central", "simulado", cameraConfig.value.sensibilidad, cameraConfig.value.tiempoConfirmacion)
            siteAlarmRepository.updateCameraStatus("bodega_central", "error")
        }
    }

    fun connectToLocalNode(host: String, port: Int) {
        localMeshManager.connectToNode(host, port)
    }

    fun refreshDiscovery() {
        localNsdManager.startDiscovery()
    }

    fun requestChangeRole() {
        _isSelectingRole.value = true
    }

    fun onRoleAssigned(role: String) {
        _deviceRole.value = role
        _isSelectingRole.value = false
    }

    fun cancelRoleSelection() {
        _isSelectingRole.value = false
    }

    fun signOut() {
        viewModelScope.launch {
            actuatorManager.stopAll()
            localMeshManager.stopLocalServer()
            localMeshManager.disconnectClient()
            localNsdManager.unregisterNodeService()
            localNsdManager.stopDiscovery()
            authRepository.signOut()
            _currentUser.value = null
            _deviceRole.value = null
            _isSelectingRole.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        localMeshManager.stopLocalServer()
        localMeshManager.disconnectClient()
        localNsdManager.unregisterNodeService()
        localNsdManager.stopDiscovery()
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AlarmViewModel(container) as T
                }
            }
    }
}
