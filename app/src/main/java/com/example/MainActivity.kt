package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.AlarmMonitoringService
import com.example.ui.components.PerimetralBottomBar
import com.example.ui.components.PerimetralTopBar
import com.example.ui.components.rememberPermissionRequest
import com.example.ui.navigation.Screen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EventsScreen
import com.example.ui.screens.NetworkScreen
import com.example.ui.screens.NodeHubScreen
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SecurityScreen
import com.example.ui.screens.TestDiagnosticsScreen
import com.example.ui.screens.ZonesScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.PerimetralTheme
import com.example.ui.viewmodel.AlarmViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as PerimetralApplication).container

        // Iniciar servicio en primer plano para monitoreo continuo en segundo plano
        try {
            val serviceIntent = Intent(this, AlarmMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {}

        setContent {
            val viewModel: AlarmViewModel = viewModel(
                factory = AlarmViewModel.provideFactory(appContainer)
            )

            val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
            val deviceRole by viewModel.deviceRole.collectAsStateWithLifecycle()
            val isSelectingRole by viewModel.isSelectingRole.collectAsStateWithLifecycle()
            val currentState by viewModel.alarmState.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

            val siteState by viewModel.siteState.collectAsStateWithLifecycle()
            val sensorState by viewModel.sensorState.collectAsStateWithLifecycle()
            val cameraConfig by viewModel.cameraConfig.collectAsStateWithLifecycle()
            val securityEvents by viewModel.securityEvents.collectAsStateWithLifecycle()
            val zones by viewModel.zones.collectAsStateWithLifecycle()
            val connectionType by viewModel.connectionType.collectAsStateWithLifecycle()
            val discoveredNodes by viewModel.discoveredNodes.collectAsStateWithLifecycle()
            val peerLinkReady by viewModel.peerLinkReady.collectAsStateWithLifecycle()
            val nodeMode by viewModel.nodeMode.collectAsStateWithLifecycle()
            val mqttChannel by viewModel.mqttChannel.collectAsStateWithLifecycle()
            val esp32Estado by viewModel.esp32Estado.collectAsStateWithLifecycle()

            // Permiso de red WiFi local / SSID: se solicita al abrir la app
            val wifiPermission = rememberPermissionRequest(
                permissions = wifiPermissions(),
                deniedMessage = "Se negó el permiso de ubicación: no se puede leer el SSID ni buscar nodos en la red WiFi local."
            )
            LaunchedEffect(Unit) {
                if (!wifiPermission.isGranted) wifiPermission.request()
            }

            // Avisos del enlace local y de comandos (ACK ausente, clave distinta, PIN incorrecto...)
            LaunchedEffect(viewModel) {
                viewModel.userMessages.collect { message ->
                    android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }

            PerimetralTheme(systemState = currentState) {
                // 1. Si no hay usuario autenticado, mostrar pantalla de autenticación
                if (currentUser == null) {
                    AuthScreen(
                        authRepository = viewModel.authRepository,
                        onAuthSuccess = { /* AuthState listener actualiza el estado automáticamente */ },
                        nodeMode = nodeMode,
                        mqttChannel = mqttChannel,
                        onNodeConfigChange = viewModel::setNodeConfig
                    )
                }
                // 2. Si el usuario está autenticado pero el dispositivo no tiene rol asignado (o pidió cambiarlo)
                else if (deviceRole == null || isSelectingRole) {
                    if (isSelectingRole && deviceRole != null) {
                        BackHandler { viewModel.cancelRoleSelection() }
                    }
                    RoleSelectionScreen(
                        userId = currentUser!!.uid,
                        deviceRoleRepository = viewModel.deviceRoleRepository,
                        onRoleAssigned = { role -> viewModel.onRoleAssigned(role) }
                    )
                }
                // 3. Usuario autenticado con rol activo: Renderizar la interfaz según el rol
                else {
                    if (currentScreen != Screen.DASHBOARD) {
                        BackHandler {
                            viewModel.selectScreen(Screen.DASHBOARD)
                        }
                    }

                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBackground),
                        containerColor = DarkBackground,
                        topBar = {
                            PerimetralTopBar(
                                systemState = currentState,
                                deviceRole = deviceRole
                            )
                        },
                        bottomBar = {
                            PerimetralBottomBar(
                                currentScreen = currentScreen,
                                onScreenSelected = { viewModel.selectScreen(it) }
                            )
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (currentScreen) {
                                Screen.DASHBOARD -> {
                                    if (deviceRole == "nodo") {
                                        NodeHubScreen(
                                            siteState = siteState,
                                            sensorState = sensorState,
                                            cameraConfig = cameraConfig,
                                            actuatorManager = viewModel.actuatorManager,
                                            deviceModel = viewModel.deviceModel,
                                            onMotionDetected = {
                                                viewModel.onCameraMotionDetected()
                                            },
                                            onCameraStatusChange = { status ->
                                                viewModel.updateCameraStatus(status)
                                            },
                                            onFallbackToSimulated = {
                                                viewModel.fallbackToSimulatedPir()
                                            },
                                            onResetSensors = {
                                                viewModel.resetSensors()
                                            },
                                            onSelectLens = { lente -> viewModel.updateCameraLens(lente) }
                                        )
                                    } else {
                                        DashboardScreen(
                                            siteState = siteState,
                                            sensorState = sensorState,
                                            cameraConfig = cameraConfig,
                                            nodeMode = nodeMode,
                                            events = securityEvents,
                                            onArm = { viewModel.armSystem() },
                                            onDisarm = { pin -> viewModel.disarmSystem(pin) },
                                            onSilenceActuators = { pin, siren, strobe ->
                                                viewModel.silenceActuators(pin, siren, strobe)
                                            },
                                            onSimulateSensor = { tipo, detalle ->
                                                viewModel.simulateSensorTrigger(tipo, detalle)
                                            },
                                            onSimulateZone = { zoneId -> viewModel.simulateZone(zoneId) },
                                            onUpdateCameraConfig = { modo, sens, tiempo ->
                                                viewModel.updateCameraConfig(modo, sens, tiempo)
                                            },
                                            onResetSensors = {
                                                viewModel.resetSensors()
                                            },
                                            zones = zones,
                                            onActivateZone = { zoneId -> viewModel.activateZone(zoneId) },
                                            onDeactivateZone = { zoneId -> viewModel.deactivateZone(zoneId) },
                                            onActivateAllZones = { viewModel.activateAllZones() },
                                            onDeactivateAllZones = { viewModel.deactivateAllZones() }
                                        )
                                    }
                                }
                                Screen.ZONES -> ZonesScreen(
                                    zones = zones
                                )
                                Screen.EVENTS -> EventsScreen(
                                    events = securityEvents
                                )
                                Screen.SCHEDULE -> ScheduleScreen(
                                    siteState = siteState,
                                    onUpdateSchedule = { inicio, fin, autoArm ->
                                        viewModel.updateSchedule(inicio, fin, autoArm)
                                    }
                                )
                                Screen.NETWORK -> NetworkScreen(
                                    deviceRole = deviceRole,
                                    connectionType = connectionType,
                                    discoveredNodes = discoveredNodes,
                                    onConnectNode = { host, port ->
                                        viewModel.connectToLocalNode(host, port)
                                    },
                                    onRefreshDiscovery = {
                                        viewModel.refreshDiscovery()
                                    },
                                    wifiPermission = wifiPermission,
                                    peerLinkReady = peerLinkReady,
                                    nodeMode = nodeMode,
                                    mqttChannel = mqttChannel,
                                    esp32 = esp32Estado
                                )
                                Screen.SECURITY -> SecurityScreen(
                                    userEmail = currentUser?.email ?: "",
                                    deviceRole = deviceRole,
                                    deviceModel = viewModel.deviceModel,
                                    onChangeRole = { viewModel.requestChangeRole() },
                                    onNavigateToTests = { viewModel.selectScreen(Screen.TESTS) },
                                    onSignOut = { viewModel.signOut() }
                                )
                                Screen.TESTS -> TestDiagnosticsScreen(
                                    siteAlarmRepository = viewModel.siteAlarmRepository,
                                    keystoreManager = viewModel.keystoreManager,
                                    actuatorManager = viewModel.actuatorManager,
                                    authRepository = viewModel.authRepository,
                                    deviceRole = deviceRole ?: "panel"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun wifiPermissions(): Array<String> {
    val base = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        base + Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        base
    }
}
