package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.actuators.AlarmActuatorManager
import com.example.data.model.SecurityEvent
import com.example.data.model.SiteAlarmState
import com.example.data.model.SiteCameraConfig
import com.example.data.model.SiteSensorState
import com.example.data.model.peerStateJson
import com.example.data.model.withPeer
import com.example.di.AppContainer
import com.example.domain.model.PerimeterZone
import com.example.network.ConnectionType
import com.example.network.DiscoveredNode
import com.example.network.LocalMeshManager
import com.example.network.DEFAULT_MQTT_CHANNEL
import com.example.network.Esp32Estado
import com.example.network.LocalNsdManager
import com.example.network.NodeMode
import com.example.security.KeystoreManager
import com.example.ui.navigation.Screen
import com.example.ui.theme.SystemAlarmState
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private val connectivity = appContainer.connectivityMonitor

    val isOnline: StateFlow<Boolean> = connectivity.isOnline

    private val _peerLinkReady = MutableStateFlow(false)
    /** La clave de enlace P2P está disponible (se obtiene de Firestore y queda en caché). */
    val peerLinkReady: StateFlow<Boolean> = _peerLinkReady.asStateFlow()

    /** Avisos breves para el usuario (se muestran como Toast). */
    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    // Comandos enviados al nodo por enlace local esperando ACK
    private val pendingCommands = ConcurrentHashMap<String, String>()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(authRepository.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _deviceRole = MutableStateFlow<String?>(appContainer.deviceManager.getCachedRole())
    val deviceRole: StateFlow<String?> = _deviceRole.asStateFlow()

    private val _nodeMode = MutableStateFlow(appContainer.deviceManager.getNodeMode())
    val nodeMode: StateFlow<NodeMode> = _nodeMode.asStateFlow()

    private val _mqttChannel = MutableStateFlow(appContainer.deviceManager.getMqttChannel())
    val mqttChannel: StateFlow<String> = _mqttChannel.asStateFlow()

    /** Con un ESP32 como nodo este teléfono es siempre panel y no usa el enlace LAN. */
    private val esp32Mode: Boolean get() = _nodeMode.value != NodeMode.TELEFONO

    /** Lo que informa el ESP32 por MQTT (broker, en línea, PIR, puerta, RSSI). */
    val esp32Estado: StateFlow<Esp32Estado> = appContainer.esp32MqttBridge.estado

    private val _isSelectingRole = MutableStateFlow(false)
    val isSelectingRole: StateFlow<Boolean> = _isSelectingRole.asStateFlow()

    private val _currentScreen = MutableStateFlow(Screen.DASHBOARD)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _alarmState = MutableStateFlow(SystemAlarmState.DISARMED)
    val alarmState: StateFlow<SystemAlarmState> = _alarmState.asStateFlow()

    /**
     * Panel: estado del nodo recibido por el enlace local. Solo se superpone a Firestore cuando
     * alguno de los dos no tiene internet; si ambos están en línea, Firestore es la referencia.
     */
    private val peerOverlay = combine(localMeshManager.peerState, connectivity.isOnline) { peer, online ->
        if (peer == null || _deviceRole.value != "panel") null
        else if (!online || !peer.optBoolean("online", true)) peer
        else null
    }

    val siteState: StateFlow<SiteAlarmState> = combine(
        siteAlarmRepository.observeSiteState("bodega_central"),
        peerOverlay
    ) { remote, peer -> remote.withPeer(peer) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SiteAlarmState()
        )

    val sensorState: StateFlow<SiteSensorState> = combine(
        siteAlarmRepository.observeSensorState("bodega_central"),
        peerOverlay
    ) { remote, peer -> remote.withPeer(peer) }
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

    /** Zonas: definiciones fijas + cuáles están activas según el documento del sitio. */
    val zones: StateFlow<List<PerimeterZone>> = combine(
        appContainer.zonesRepository.observeZones(),
        siteState
    ) { defs, site -> defs.map { it.copy(isActive = it.id in site.zonasActivas) } }
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
                    launch { _peerLinkReady.value = appContainer.peerKeyRepository.ensurePeerKey(user.uid) }
                    // Requiere sesión: sin ella Firestore rechaza las lecturas y escrituras
                    launch {
                        siteAlarmRepository.initializeSiteIfNeeded("bodega_central")
                        siteAlarmRepository.resetSensors("bodega_central")
                    }
                } else {
                    _deviceRole.value = null
                }
            }
        }

        // Nodo: aplicar comandos recibidos por la WiFi local
        localMeshManager.onLocalCommandReceived = { action, pin, detail, commandId ->
            viewModelScope.launch { applyNodeCommand(action, pin, detail, commandId) }
        }

        // Panel: ACK o error del nodo
        localMeshManager.onPeerMessage = { json ->
            when (json.optString("type")) {
                "ack" -> json.optString("id").takeIf { it.isNotEmpty() }?.let { pendingCommands.remove(it) }
                "error" -> _userMessages.tryEmit(
                    when (json.optString("reason")) {
                        "sin_clave" -> "El nodo no tiene la clave de enlace: ábrelo una vez con internet."
                        else -> "El nodo rechazó el comando: la clave de enlace no coincide. Abre ambos con internet y la misma cuenta."
                    }
                )
            }
        }

        // Nodo: empujar estado a los paneles enlazados cada vez que cambia
        viewModelScope.launch {
            combine(siteState, sensorState, connectivity.isOnline) { site, sensors, online ->
                Triple(site, sensors, online)
            }.collect { (site, sensors, online) ->
                if (_deviceRole.value == "nodo") {
                    localMeshManager.broadcastState(peerStateJson(site, sensors, online))
                }
            }
        }

        // Descartar el estado del nodo al perder el enlace WiFi local
        viewModelScope.launch {
            localMeshManager.connectionType.collect { type ->
                if (type == ConnectionType.CLOUD_BACKUP) localMeshManager.clearPeerState()
            }
        }

        // Modo ESP32: forzar panel (también en Firestore, para no quedar registrado como nodo) y
        // mantener el puente MQTT activo solo con sesión iniciada.
        viewModelScope.launch {
            combine(_currentUser, _nodeMode, _mqttChannel) { user, mode, canal -> Triple(user, mode, canal) }
                .collect { (user, mode, canal) ->
                    val bridge = appContainer.esp32MqttBridge
                    if (user != null && mode != NodeMode.TELEFONO) {
                        _deviceRole.value = "panel"
                        _isSelectingRole.value = false
                        launch { deviceRoleRepository.assignRole(user.uid, "panel") }
                        bridge.start(canal)
                    } else {
                        bridge.stop()
                    }
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
                    if (!esp32Mode) localNsdManager.startDiscovery()
                }
            }
        }
    }

    /**
     * Ruta de cada comando del panel:
     * - Panel con internet y nodo en línea (o sin enlace): solo Firestore.
     * - Panel con internet y nodo sin internet: Firestore + enlace local.
     * - Panel sin internet con enlace: solo enlace local (el nodo lo aplica y devuelve su estado).
     * - Sin internet ni enlace: Firestore en cola local, se sincroniza al volver la conexión.
     * El mismo id de comando se usa como id del evento, así no se duplica si llega por dos vías.
     */
    private fun dispatch(action: String, pin: String? = null, detail: String? = null, applyLocally: suspend (eventId: String) -> Unit) {
        val commandId = UUID.randomUUID().toString()
        if (_deviceRole.value != "panel" || esp32Mode) {
            viewModelScope.launch { applyLocally(commandId) }
            return
        }
        val online = connectivity.isOnline.value
        val nodeOnline = localMeshManager.peerState.value?.optBoolean("online", true) ?: true
        val useLink = localMeshManager.hasLocalLink() && (!online || !nodeOnline)

        val sent = useLink && localMeshManager.sendLocalCommand(action, pin, detail, commandId)
        if (sent) awaitAck(commandId, action)
        if (online || !sent) {
            viewModelScope.launch { applyLocally(commandId) }
        }
        if (!online && !sent) {
            _userMessages.tryEmit("Sin internet ni enlace con el nodo: el cambio se sincronizará al volver la conexión.")
        }
        Log.d("AlarmViewModel", "Comando $action ($commandId): online=$online nodoOnline=$nodeOnline enlace=$sent")
    }

    private fun awaitAck(commandId: String, action: String) {
        pendingCommands[commandId] = action
        viewModelScope.launch {
            delay(6000L)
            if (pendingCommands.remove(commandId) != null) {
                _userMessages.tryEmit("El nodo no confirmó el comando por el enlace local. Revisa que siga enlazado.")
            }
        }
    }

    /** Nodo: aplica en su copia local (Firestore offline) un comando del panel. */
    private suspend fun applyNodeCommand(action: String, pin: String?, detail: String?, commandId: String?) {
        Log.d("AlarmViewModel", "Nodo aplica comando local: $action ($commandId)")
        when (action) {
            "arm" -> siteAlarmRepository.armSystem("bodega_central", "panel", commandId)
            "disarm" -> if (pin != null) {
                siteAlarmRepository.disarmSystem("bodega_central", pin, "panel", commandId)
            }
            "simulate_pir" -> siteAlarmRepository.recordSensorTrigger("bodega_central", "pir", detail ?: "Movimiento PIR (enlace local)", "panel", commandId)
            "simulate_reed" -> siteAlarmRepository.recordSensorTrigger("bodega_central", "reed", detail ?: "Apertura portón (enlace local)", "panel", commandId)
            "silence_siren" -> if (pin != null) siteAlarmRepository.silenceActuators("bodega_central", pin, true, false, commandId)
            "turn_off_strobe" -> if (pin != null) siteAlarmRepository.silenceActuators("bodega_central", pin, false, true, commandId)
            "simulate_zone" -> if (detail != null) siteAlarmRepository.simulateZoneTrigger("bodega_central", detail, "panel", commandId)
            "zone_on" -> if (detail != null) siteAlarmRepository.setZoneActive("bodega_central", detail, true, "panel", commandId)
            "zone_off" -> if (detail != null) siteAlarmRepository.setZoneActive("bodega_central", detail, false, "panel", commandId)
            "zones_all_on" -> siteAlarmRepository.setAllZonesActive("bodega_central", true, "panel", commandId)
            "zones_all_off" -> siteAlarmRepository.setAllZonesActive("bodega_central", false, "panel", commandId)
        }
    }

    private fun observeUserDeviceRole(userId: String) {
        viewModelScope.launch {
            deviceRoleRepository.observeDeviceRole(userId).collect { roleInfo ->
                _deviceRole.value = if (esp32Mode) "panel" else roleInfo?.role
            }
        }
    }

    fun selectScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    private val origin: String get() = _deviceRole.value ?: "panel"

    fun armSystem() = dispatch("arm") { id ->
        siteAlarmRepository.armSystem("bodega_central", origin, id)
    }

    fun disarmSystem(pinInput: String) = dispatch("disarm", pin = pinInput) { id ->
        siteAlarmRepository.disarmSystem("bodega_central", pinInput, origin, id)
            .onFailure { _userMessages.tryEmit(it.message ?: "No se pudo desarmar") }
    }

    fun silenceActuators(pinInput: String, silenceSiren: Boolean, turnOffStrobe: Boolean) {
        val action = if (silenceSiren) "silence_siren" else "turn_off_strobe"
        dispatch(action, pin = pinInput) { id ->
            siteAlarmRepository.silenceActuators("bodega_central", pinInput, silenceSiren, turnOffStrobe, id)
                .onFailure { _userMessages.tryEmit(it.message ?: "No se pudo silenciar") }
        }
    }

    fun simulateSensorTrigger(tipo: String, detalle: String) {
        val action = if (tipo == "pir") "simulate_pir" else "simulate_reed"
        dispatch(action, detail = detalle) { id ->
            siteAlarmRepository.recordSensorTrigger("bodega_central", tipo, detalle, origin, id)
        }
    }

    fun simulateZone(zoneId: String) = dispatch("simulate_zone", detail = zoneId) { id ->
        siteAlarmRepository.simulateZoneTrigger("bodega_central", zoneId, origin, id)
    }

    fun onCameraMotionDetected() {
        viewModelScope.launch {
            siteAlarmRepository.recordSensorTrigger(
                siteId = "bodega_central",
                sensorType = "pir",
                detail = "Movimiento detectado por cámara ${cameraConfig.value.lente}",
                origen = "nodo"
            )
        }
    }

    fun resetSensors() {
        viewModelScope.launch {
            siteAlarmRepository.resetSensors("bodega_central")
        }
    }

    fun activateZone(zoneId: String) = dispatch("zone_on", detail = zoneId) { id ->
        siteAlarmRepository.setZoneActive("bodega_central", zoneId, true, origin, id)
    }

    fun deactivateZone(zoneId: String) = dispatch("zone_off", detail = zoneId) { id ->
        siteAlarmRepository.setZoneActive("bodega_central", zoneId, false, origin, id)
    }

    fun activateAllZones() = dispatch("zones_all_on") { id ->
        siteAlarmRepository.setAllZonesActive("bodega_central", true, origin, id)
    }

    fun deactivateAllZones() = dispatch("zones_all_off") { id ->
        siteAlarmRepository.setAllZonesActive("bodega_central", false, origin, id)
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

    fun updateCameraLens(lente: String) {
        siteAlarmRepository.updateCameraLens("bodega_central", lente)
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
        if (esp32Mode) {
            _userMessages.tryEmit("Con un ESP32 como nodo este teléfono es siempre panel. Cierra sesión para cambiar de modo.")
            return
        }
        _isSelectingRole.value = true
    }

    /** Se elige antes de iniciar sesión (pantalla de acceso). */
    fun setNodeConfig(mode: NodeMode, canal: String) {
        val limpio = canal.trim().trim('/').ifEmpty { DEFAULT_MQTT_CHANNEL }
        appContainer.deviceManager.setNodeMode(mode)
        appContainer.deviceManager.setMqttChannel(limpio)
        _nodeMode.value = mode
        _mqttChannel.value = limpio
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
