# Nodo ESP32 de la bodega

Alternativa al teléfono como nodo: el ESP32 lee el PIR (HC-SR501) y la puerta, y maneja sirena, estrobo y LED de armado. Habla con la app por MQTT (`sketch.ino` define el protocolo).

## Probar en Wokwi

1. En [wokwi.com](https://wokwi.com) crea un proyecto nuevo de ESP32.
2. Pega el contenido de `sketch/sketch.ino`, `diagram.json` y `libraries.txt` en los archivos del mismo nombre. Crea además `secrets.h` copiando `sketch/secrets.example.h` con los datos de tu deployment EMQX Cloud Serverless.
3. Pulsa ▶. El ESP32 se conecta a la red `Wokwi-GUEST` y al broker.
4. En la app, antes de iniciar sesión, elige "ESP32 Wokwi" y escribe el mismo canal que pusiste en `CANAL` (o ponlo en `mqtt.channel` de `local.properties`).

## Cableado (ESP32 físico)

| Componente | Pin |
|---|---|
| PIR HC-SR501 (salida OUT) | GPIO27 |
| Reed switch (a GND, usa pull-up interno) | GPIO26 |
| Buzzer / sirena | GPIO25 |
| Estrobo (LED; con relé si es una luz real) | GPIO33 |
| LED de sistema armado | GPIO32 |

Para un ESP32 físico cambia `WIFI_SSID` y `WIFI_PASS` en `secrets.h` por los de la red de la bodega, y en la app elige "ESP32 físico".

## Cambiar el canal

El canal está en `CANAL` de `secrets.h` (ignorado por git; `secrets.example.h` trae un valor de relleno). Debe ser idéntico al campo "Canal MQTT" de la app (`mqtt.channel` en `local.properties`) y a `MQTT_CANAL` del monitor. No hay canal por defecto. El canal que se usó antes quedó en el historial de git: usa uno nuevo y difícil de adivinar, porque quien lo conozca y tenga credenciales del broker puede leer eventos y enviar estados.

## Protocolo

Temas bajo `<canal>` (detalle y motivos de ERR en el README de la raíz, sección "Protocolo"):

- `estado` (app → nodo, retenido, QoS 1): `{"id","estado","sirena","estrobo"}`, sin claves extra. El nodo valida la orden completa.
- `ack` (nodo → app): `{"id","ok","motivo","ts"}`. `motivo` solo si hay error: `json_invalido`, `no_es_objeto`, `clave_desconocida`, `campo_faltante`, `tipo_incorrecto`, `id_invalido`, `estado_invalido`.
- `evento`: `{"sensor","detalle","ms","ts","id"}`. `sensores` (retenido, cada 15 s): `{"pir","puerta","rssi","ts"}`. `nodo` (retenido, última voluntad): `online` / `offline`.
- `ts` es el epoch UTC por NTP (`pool.ntp.org`); vale 0 si no sincronizó.
- Cada orden rechazada se registra por Serial y no toca los actuadores.

## Reconexión

El firmware no bloquea el sensado mientras reconecta. WiFi: espera de 4, 8, 16... s. MQTT: 1, 2, 4... s. Ambos con tope de 60 s y vuelven al mínimo al conectar. Keepalive MQTT de 15 s; al perder el WiFi se corta la sesión MQTT de inmediato.

## Buffer de eventos en flash

Sin MQTT, el nodo guarda solo eventos (no latidos) en LittleFS, sin tarjeta SD:

- Archivos `/pend.jsonl` y `/pend_viejo.jsonl`, de hasta 100 eventos cada uno (unos 119 bytes máx. por evento, unos 24 KB en total, tope de 200 eventos).
- Al llenarse el actual se borra el viejo (se pierden los 100 más antiguos) y el actual pasa a ser el viejo.
- Al reconectar se reenvían en orden (el viejo primero) y se borran. Si un envío falla se conservan; el `id` único evita duplicados.
- QoS 0 no confirma: los últimos 20 eventos enviados quedan en RAM y, al detectar el corte, los de los últimos 30 s pasan a flash. Si se corta la corriente, esa ventana de 30 s se pierde si solo estaba en RAM. La app deduplica por id.
- Un evento con `ts` más de 60 s atrasado se registra en la app con su hora real y no dispara la alarma.
- Desgaste: solo se escribe cuando hay un evento sin conexión, agregando una línea (no se reescribe el archivo entero) y LittleFS reparte las escrituras entre sectores. Una flash típica aguanta unos 100 000 ciclos por sector.
- Vaciar a mano: reflashear con borrado de flash ("Erase All Flash" en Arduino IDE) o esperar a la reconexión.

## Seguridad

Controles aplicados (referencia ISO/IEC 27001:2022 Anexo A; pasos de comprobación en el README de la raíz, sección "Seguridad"):

- **A.8.24 Criptografía:** TLS 8883 con CA validada (`WiFiClientSecure` + DigiCert Global Root G2; la app valida host y cadena). Comprobación: Wireshark, en 1883 se leía el JSON en claro y en 8883 solo el handshake TLS.
- **A.5.17 Información de autenticación:** credenciales y canal en `secrets.h` y `local.properties`, ignorados por git. Comprobación: `git check-ignore -v hardware/esp32-bodega/sketch/secrets.h` y `git ls-files | grep secrets`.
- **A.8.5 Autenticación segura:** usuario y contraseña en el broker. Comprobación: una clave errónea o ninguna es rechazada.
- **A.8.15 Registro:** cada rechazo de orden sale por Serial y vuelve como ERR en `ack`.
