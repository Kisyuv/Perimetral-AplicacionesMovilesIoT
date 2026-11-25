package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.PendingEventDb
import com.example.actuators.AlarmActuatorManager
import com.example.data.remote.DeviceManager
import com.example.data.remote.FirebaseDataSource
import com.example.data.remote.FirestoreConfig
import com.example.data.remote.IFirebaseDataSource
import com.example.data.repository.AlarmRepositoryImpl
import com.example.data.repository.AuthRepository
import com.example.data.repository.DeviceRoleRepository
import com.example.data.repository.EventsRepositoryImpl
import com.example.data.repository.PeerKeyRepository
import com.example.data.repository.SiteAlarmRepository
import com.example.data.repository.ZonesRepositoryImpl
import com.example.domain.repository.IAlarmRepository
import com.example.domain.repository.IEventsRepository
import com.example.domain.repository.IZonesRepository
import com.example.domain.usecase.ArmAlarmUseCase
import com.example.domain.usecase.CheckOffHoursIntrusionUseCase
import com.example.domain.usecase.DisarmAlarmUseCase
import com.example.domain.usecase.TriggerAlarmUseCase
import com.example.network.ConnectivityMonitor
import com.example.network.LocalMeshManager
import com.example.network.LocalNsdManager
import com.example.network.Esp32MqttBridge
import com.example.network.LocalWifiManager
import com.example.security.KeystoreManager
import com.example.security.SecurityCodeManager
import com.google.firebase.auth.FirebaseAuth

interface AppContainer {
    val firebaseAuth: FirebaseAuth
    val authRepository: AuthRepository
    val deviceManager: DeviceManager
    val deviceRoleRepository: DeviceRoleRepository
    val siteAlarmRepository: SiteAlarmRepository
    val esp32MqttBridge: Esp32MqttBridge
    val alarmActuatorManager: AlarmActuatorManager
    val keystoreManager: KeystoreManager
    val localNsdManager: LocalNsdManager
    val localMeshManager: LocalMeshManager
    val firebaseDataSource: IFirebaseDataSource
    val alarmRepository: IAlarmRepository
    val zonesRepository: IZonesRepository
    val eventsRepository: IEventsRepository
    val securityCodeManager: SecurityCodeManager
    val localWifiManager: LocalWifiManager
    val peerKeyRepository: PeerKeyRepository
    val connectivityMonitor: ConnectivityMonitor
    val armAlarmUseCase: ArmAlarmUseCase
    val disarmAlarmUseCase: DisarmAlarmUseCase
    val triggerAlarmUseCase: TriggerAlarmUseCase
    val checkOffHoursIntrusionUseCase: CheckOffHoursIntrusionUseCase
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val firestore by lazy {
        FirestoreConfig.createFirestoreInstance(context)
    }

    private val pendingEventDb by lazy {
        Room.databaseBuilder(context, PendingEventDb::class.java, "perimetral.db").build()
    }

    override val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    override val deviceManager: DeviceManager by lazy {
        DeviceManager(context)
    }

    override val keystoreManager: KeystoreManager by lazy {
        KeystoreManager(context)
    }

    override val localNsdManager: LocalNsdManager by lazy {
        LocalNsdManager(context)
    }

    override val localMeshManager: LocalMeshManager by lazy {
        LocalMeshManager(keystoreManager)
    }

    override val authRepository: AuthRepository by lazy {
        AuthRepository(firebaseAuth, firestore)
    }

    override val deviceRoleRepository: DeviceRoleRepository by lazy {
        DeviceRoleRepository(firestore, deviceManager)
    }

    override val siteAlarmRepository: SiteAlarmRepository by lazy {
        SiteAlarmRepository(firestore, firebaseAuth, context, pendingEventDb.pendingEvents())
    }

    override val esp32MqttBridge: Esp32MqttBridge by lazy {
        Esp32MqttBridge(siteAlarmRepository)
    }

    override val alarmActuatorManager: AlarmActuatorManager by lazy {
        AlarmActuatorManager(context)
    }

    override val firebaseDataSource: IFirebaseDataSource by lazy {
        FirebaseDataSource(firestore)
    }

    override val alarmRepository: IAlarmRepository by lazy {
        AlarmRepositoryImpl(firebaseDataSource)
    }

    override val zonesRepository: IZonesRepository by lazy {
        ZonesRepositoryImpl(firebaseDataSource)
    }

    override val eventsRepository: IEventsRepository by lazy {
        EventsRepositoryImpl(firebaseDataSource)
    }

    override val securityCodeManager: SecurityCodeManager by lazy {
        SecurityCodeManager()
    }

    override val localWifiManager: LocalWifiManager by lazy {
        LocalWifiManager(context)
    }

    override val connectivityMonitor: ConnectivityMonitor by lazy {
        ConnectivityMonitor(context)
    }

    override val peerKeyRepository: PeerKeyRepository by lazy {
        PeerKeyRepository(firestore, keystoreManager)
    }

    override val armAlarmUseCase: ArmAlarmUseCase by lazy {
        ArmAlarmUseCase(alarmRepository)
    }

    override val disarmAlarmUseCase: DisarmAlarmUseCase by lazy {
        DisarmAlarmUseCase(alarmRepository, securityCodeManager)
    }

    override val triggerAlarmUseCase: TriggerAlarmUseCase by lazy {
        TriggerAlarmUseCase(alarmRepository, eventsRepository)
    }

    override val checkOffHoursIntrusionUseCase: CheckOffHoursIntrusionUseCase by lazy {
        CheckOffHoursIntrusionUseCase()
    }
}
