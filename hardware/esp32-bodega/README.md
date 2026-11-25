# Nodo ESP32 de la bodega

Alternativa al teléfono como nodo: el ESP32 lee el PIR y la puerta, y maneja sirena, estrobo y LED de armado. Habla con la app por MQTT (`sketch.ino` define el protocolo).

## Probar en Wokwi

1. En [wokwi.com](https://wokwi.com) crea un proyecto nuevo de ESP32.
2. Pega el contenido de `sketch.ino`, `diagram.json` y `libraries.txt` en los archivos del mismo nombre. Crea además `secrets.h` copiando `secrets.example.h` con los datos de tu deployment EMQX Cloud Serverless.
3. Pulsa ▶. El ESP32 se conecta a la red `Wokwi-GUEST` y al broker.
4. En la app, antes de iniciar sesión, elige "ESP32 Wokwi" y deja el canal por defecto (o el que pusiste en el sketch).

## Cableado (ESP32 físico)

| Componente | Pin |
|---|---|
| PIR (salida) | GPIO27 |
| Reed switch (a GND, usa pull-up interno) | GPIO26 |
| Buzzer / sirena | GPIO25 |
| Estrobo (LED; con relé si es una luz real) | GPIO33 |
| LED de sistema armado | GPIO32 |

Para un ESP32 físico cambia `WIFI_SSID` y `WIFI_PASS` en `secrets.h` por los de la red de la bodega, y en la app elige "ESP32 físico".

## Cambiar el canal

El canal está en `CANAL` (`sketch.ino`, por defecto `perimetral/a2b39e2e`). Debe ser idéntico al campo "Canal MQTT" de la app. Usa uno propio y difícil de adivinar: cualquiera que lo conozca puede leer eventos y enviar estados.

## Seguridad

Controles aplicados (comprobación detallada en el README de la raíz, sección "Seguridad MQTT"):

- **TLS 8883 con CA validada** (`WiFiClientSecure` + DigiCert Global Root G2; la app valida host y cadena). Amenaza: espionaje o suplantación del broker en la red. Se comprueba con Wireshark: en 1883 se leía el JSON en claro, en 8883 solo se ve el handshake TLS.
- **Credenciales fuera del código** (`secrets.h`, `local.properties`, ignorados por git). Amenaza: filtrarlas en el repositorio. Se comprueba con `git check-ignore` y `git ls-files | grep secrets`.
