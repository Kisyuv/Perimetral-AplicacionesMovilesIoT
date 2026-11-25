# Perimetral

Alarma perimetral para bodega: detecta intrusos fuera de horario con un sensor de movimiento (PIR) y un sensor magnético de puerta (reed), y activa una sirena y una luz estroboscópica. App Android (Kotlin + Jetpack Compose + Firebase/Firestore) del ramo IoT EV2.

Este archivo es la **memoria del proyecto**: qué hay, cómo se usa, qué se probó y qué falta. Las notas técnicas para Claude están en `CLAUDE.md`.

## Cómo funciona

Hay un sitio en Firestore (`sitios/bodega_central`) y cada dispositivo cumple un rol:

- **Panel:** arma y desarma el sistema, configura zonas y horarios, y ve los eventos.
- **Nodo:** tiene los sensores y la sirena. Puede ser de tres tipos, que se eligen en el login con el selector **"Nodo de sensores"**:

| Modo | Qué hace de nodo | Conexión con el panel |
|---|---|---|
| Teléfono | Otro celular: la cámara detecta movimiento y usa el parlante y el flash | Firestore + red local (WebSocket 8765 + NSD, cifrado con clave compartida) |
| ESP32 Wokwi | ESP32 simulado en wokwi.com | MQTT sobre TLS (EMQX Cloud Serverless, 8883) |
| ESP32 físico | ESP32 real con PIR, reed, buzzer y LEDs/relé | MQTT por el mismo broker |

- En los modos ESP32 el teléfono siempre es **panel**. Para cambiar de modo hay que cerrar sesión (Seguridad → CERRAR SESIÓN).
- Cada sensor dispara la alarma solo si el sistema está armado y su zona está activa: PIR → ZN-05 y puerta → ZN-01.

## Dónde está cada cosa

| Qué | Dónde |
|---|---|
| App Android | `app/` (pantallas en `ui/screens`, lógica en `ui/viewmodel/AlarmViewModel.kt`) |
| Puente MQTT con el ESP32 | `app/src/main/java/com/example/network/Esp32MqttBridge.kt` |
| Enlace por la red local | `network/LocalMeshManager.kt` |
| Programa y circuito del ESP32 | `hardware/esp32-bodega/` (`sketch.ino`, `diagram.json`, `libraries.txt`, `README.md`) |
| Monitor MQTT desde el PC | `hardware/esp32-bodega/mqtt_monitor.py` |
| Guía paso a paso de las pruebas | `~/Proyectos/Perimetral-pruebas-wokwi.md` (fuera del repo) |
| APK compilada | `~/Proyectos/Perimetral-debug.apk` (fuera del repo) |
| Fuente Doto (licencia OFL) | `app/src/main/res/font/`, licencia en `OFL-Doto.txt` |

## Compilar

```bash
ANDROID_HOME=~/Android/Sdk ./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

Necesita dos archivos locales que **no** están en git:

- `app/google-services.json`, del proyecto de Firebase;
- `debug.keystore`.

## MQTT (nodo ESP32)

- Broker: deployment EMQX Cloud Serverless, puerto 8883 (TLS, usuario y contraseña). Las credenciales no están en el código:
  - App: en `local.properties` (ignorado por git) añade `mqtt.host=xxxx.ala.us-east-1.emqxsl.com`, `mqtt.user=...` y `mqtt.password=...`. Sin `mqtt.host` el bridge no conecta.
  - ESP32: copia `hardware/esp32-bodega/secrets.example.h` a `secrets.h` (ignorado) y rellénalo.
  - Monitor: define `MQTT_HOST`, `MQTT_USER` y `MQTT_PASS` en el entorno.
- Canal: `perimetral/a2b39e2e`. Tiene que ser igual en el sketch (`CANAL`), en la app (campo "Canal MQTT") y en el monitor.
- Temas:
  - `/evento` (ESP32 → app): `{"sensor":"pir"|"reed","detalle","ms","id"}`;
  - `/sensores` (retenido): `{"pir","puerta","rssi"}`;
  - `/nodo` (retenido, última voluntad): `online` / `offline`;
  - `/estado` (app → ESP32, retenido): `{"estado","sirena","estrobo"}`.
- Ver los mensajes: `python mqtt_monitor.py`, o el cliente web de EMQX Cloud Serverless suscrito a `perimetral/a2b39e2e/#`.
- Probar la sirena sin la app: `python mqtt_monitor.py --estado alarma`. Luego `--estado desarmada`, porque el estado queda retenido en el broker.

### Seguridad MQTT

- **TLS 8883 con CA validada.** La app (Paho con `SSLSocketFactory` y verificación de hostname) y el ESP32 (`setCACert` con DigiCert Global Root G2) validan el certificado de EMQX Cloud Serverless. Amenaza: lectura o suplantación del tráfico en la red. Comprobación: captura de Wireshark; en 1883 se veía el JSON, en 8883 solo TLS.
- **Credenciales fuera del código.** `local.properties`, `secrets.h` y `google-services.json` no están en git. Amenaza: credenciales expuestas en el repositorio. Comprobación: `git check-ignore local.properties hardware/esp32-bodega/secrets.h` los lista.
- **Usuario y contraseña en el broker.** Amenaza: que cualquiera publique estados o eventos falsos. Comprobación: conectar sin credenciales es rechazado.

Circuito del ESP32:

| Pin | Componente |
|---|---|
| 27 | PIR |
| 26 | Reed (interruptor deslizante en Wokwi: izquierda = puerta cerrada) |
| 25 | Buzzer, sirena de dos tonos |
| 33 | LED estroboscópico |
| 32 | LED de sistema armado |

## Historial

- **Base:** creada en Google AI Studio y movida a desarrollo local el 2026-10-23.
- **Red local:** se reemplazó el Bluetooth (nunca funcionó bien) por un enlace WiFi LAN con WebSocket, NSD y cifrado AES-GCM con una clave compartida.
- **Zonas:** se agregaron las zonas activas por sensor, el monitor de conectividad y los permisos que se piden al usarlos.
- **2026-11-18:**
  - nodo ESP32 por MQTT (Wokwi o físico) y selector de nodo en el login;
  - rediseño monocromo estilo Nothing: negro y grises, fuente de puntos Doto en títulos y estados, rojo solo en alarma;
  - commit `9f4be2d`.

## Pruebas hechas (2026-11-18)

- [x] El ESP32 en Wokwi conecta al WiFi y al broker.
- [x] App → ESP32: un estado `alarma` publicado enciende la sirena y la luz estroboscópica.
- [x] ESP32 → broker: llegan los eventos de la puerta (`reed`) y de movimiento (`pir`), con `id`.
- [x] La app se instala y abre en Waydroid sin crashes. El panel se ve bien con el rediseño.
- [x] En el login, el selector de nodo y el campo "Canal MQTT" funcionan en los tres modos.
- [ ] **Pendiente:** prueba completa con sesión iniciada en modo ESP32 Wokwi. Pasos:
  1. armar el sistema desde la app;
  2. disparar el PIR o abrir la puerta en Wokwi;
  3. comprobar que la app pase a ALARMA y que el ESP32 encienda la sirena.

## Pendientes y limitaciones

- El broker es público y sin TLS. Para uso real habría que usar un broker propio con usuario, contraseña y TLS.
- En modo ESP32 se siguen viendo las tarjetas de la red local, aunque no se usan.
- Con la simulación de Wokwi muy lenta (por ejemplo al 3 %), el ESP32 puede tardar minutos en conectar o reconectarse al broker. Se arregla reiniciando la simulación con menos carga en el PC.
- En el panel, los botones "Simular ZN-04/05/06" quedan bajo la barra inferior. Se alcanzan desplazando la lista.
- El proyecto de Wokwi no está guardado en ninguna cuenta. Para usarlo, crea un proyecto ESP32 nuevo y pega los 3 archivos de `hardware/esp32-bodega/`.
- Si Waydroid deja de responder a adb (`No route to host`), reinícialo con `waydroid session stop` y luego `waydroid show-full-ui`.
