// Perimetral: nodo ESP32 de la alarma de bodega.
// PIR de movimiento + reed switch de puerta -> eventos por MQTT.
// La app (panel) decide si hay alarma y publica el estado; el ESP32 maneja sirena y estrobo.
// Sirve igual en Wokwi (red Wokwi-GUEST) y en un ESP32 físico (cambiar WIFI_SSID/WIFI_PASS en secrets.h).

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include "secrets.h"  // WIFI_SSID, WIFI_PASS, MQTT_HOST, MQTT_USER, MQTT_PASS (ver secrets.example.h)

// --- Configuración ---
const uint16_t MQTT_PORT = 8883;  // MQTT sobre TLS (EMQX Cloud Serverless)
// Debe coincidir con el "Canal" que se ingresa en la app al elegir el nodo ESP32.
const char* CANAL = "perimetral/a2b39e2e";

// DigiCert Global Root G2: CA raíz pública que firma el certificado de EMQX Cloud (no es un secreto).
const char* CA_BROKER =
"-----BEGIN CERTIFICATE-----
"
"MIIDjjCCAnagAwIBAgIQAzrx5qcRqaC7KGSxHQn65TANBgkqhkiG9w0BAQsFADBh
"
"MQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3
"
"d3cuZGlnaWNlcnQuY29tMSAwHgYDVQQDExdEaWdpQ2VydCBHbG9iYWwgUm9vdCBH
"
"MjAeFw0xMzA4MDExMjAwMDBaFw0zODAxMTUxMjAwMDBaMGExCzAJBgNVBAYTAlVT
"
"MRUwEwYDVQQKEwxEaWdpQ2VydCBJbmMxGTAXBgNVBAsTEHd3dy5kaWdpY2VydC5j
"
"b20xIDAeBgNVBAMTF0RpZ2lDZXJ0IEdsb2JhbCBSb290IEcyMIIBIjANBgkqhkiG
"
"9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuzfNNNx7a8myaJCtSnX/RrohCgiN9RlUyfuI
"
"2/Ou8jqJkTx65qsGGmvPrC3oXgkkRLpimn7Wo6h+4FR1IAWsULecYxpsMNzaHxmx
"
"1x7e/dfgy5SDN67sH0NO3Xss0r0upS/kqbitOtSZpLYl6ZtrAGCSYP9PIUkY92eQ
"
"q2EGnI/yuum06ZIya7XzV+hdG82MHauVBJVJ8zUtluNJbd134/tJS7SsVQepj5Wz
"
"tCO7TG1F8PapspUwtP1MVYwnSlcUfIKdzXOS0xZKBgyMUNGPHgm+F6HmIcr9g+UQ
"
"vIOlCsRnKPZzFBQ9RnbDhxSJITRNrw9FDKZJobq7nMWxM4MphQIDAQABo0IwQDAP
"
"BgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBhjAdBgNVHQ4EFgQUTiJUIBiV
"
"5uNu5g/6+rkS7QYXjzkwDQYJKoZIhvcNAQELBQADggEBAGBnKJRvDkhj6zHd6mcY
"
"1Yl9PMWLSn/pvtsrF9+wX3N3KjITOYFnQoQj8kVnNeyIv/iPsGEMNKSuIEyExtv4
"
"NeF22d+mQrvHRAiGfzZ0JFrabA0UWTW98kndth/Jsw1HKj2ZL7tcu7XUIOGZX1NG
"
"Fdtom/DzMNU+MeKNhJ7jitralj41E6Vf8PlwUHBHQRFXGU7Aj64GxJUTFy8bJZ91
"
"8rGOmaFvE7FBcf6IKshPECBV1/MUReXgRPTqh5Uykw7+U0b6LJ3/iyK5S9kJRaTe
"
"pLiaWN0bfVKfjllDiIGknibVb63dDcY3fe0Dkhvld1927jyNxF1WW6LZZm6zNTfl
"
"MrY=
"
"-----END CERTIFICATE-----
";

// --- Pines ---
const int PIN_PIR = 27;      // salida del sensor PIR (HIGH = movimiento)
const int PIN_REED = 26;     // reed switch a GND con pull-up (LOW = puerta cerrada)
const int PIN_SIRENA = 25;   // buzzer / sirena
const int PIN_ESTROBO = 33;  // LED o relé de la luz estroboscópica
const int PIN_ARMADO = 32;   // LED indicador de sistema armado

// --- Tiempos ---
const unsigned long REARME_PIR_MS = 3000;   // evita repetir el mismo evento PIR
const unsigned long ANTIRREBOTE_MS = 80;
const unsigned long ESTROBO_MS = 120;        // medio periodo del destello
const unsigned long LATIDO_MS = 15000;       // reenvía el estado de los sensores

WiFiClientSecure red;
PubSubClient mqtt(red);
String tEvento, tSensores, tNodo, tEstado;

String estado = "desarmada";
bool sirena = false, estrobo = false;

bool pirPrevio = false, puertaPrevia = false;
unsigned long ultimoPir = 0, cambioReed = 0, ultimoLatido = 0, ultimoDestello = 0;
bool lecturaReed = false, ledEstrobo = false;

void publicarSensores() {
  JsonDocument doc;
  doc["pir"] = pirPrevio;
  doc["puerta"] = puertaPrevia;
  doc["rssi"] = WiFi.RSSI();
  char buf[96];
  serializeJson(doc, buf);
  mqtt.publish(tSensores.c_str(), buf, true);
}

void publicarEvento(const char* sensor, const char* detalle) {
  JsonDocument doc;
  doc["sensor"] = sensor;
  doc["detalle"] = detalle;
  doc["ms"] = millis();
  // Id único del evento: así varios paneles registran el mismo evento una sola vez en Firestore.
  doc["id"] = String((uint32_t)ESP.getEfuseMac(), HEX) + "-" + String(esp_random(), HEX);
  char buf[160];
  serializeJson(doc, buf);
  mqtt.publish(tEvento.c_str(), buf);
  Serial.printf("Evento %s: %s\n", sensor, detalle);
}

void alRecibir(char* topic, byte* payload, unsigned int largo) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, largo)) return;
  estado = doc["estado"] | "desarmada";
  sirena = doc["sirena"] | false;
  estrobo = doc["estrobo"] | false;
  Serial.printf("Estado del panel: %s sirena=%d estrobo=%d\n", estado.c_str(), sirena, estrobo);
}

void conectarWifi() {
  Serial.printf("Conectando a WiFi %s", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS, 6);  // canal 6 acelera la conexión en Wokwi; un AP físico lo ignora
  while (WiFi.status() != WL_CONNECTED) {
    delay(250);
    Serial.print(".");
  }
  Serial.printf("\nWiFi OK, IP %s\n", WiFi.localIP().toString().c_str());
}

void conectarMqtt() {
  while (!mqtt.connected()) {
    String id = "perimetral-esp32-" + String((uint32_t)ESP.getEfuseMac(), HEX);
    Serial.print("Conectando a MQTT... ");
    // Última voluntad: si se corta, el broker publica "offline" en el tema del nodo.
    if (mqtt.connect(id.c_str(), MQTT_USER, MQTT_PASS, tNodo.c_str(), 1, true, "offline")) {
      Serial.println("OK");
      mqtt.publish(tNodo.c_str(), "online", true);
      mqtt.subscribe(tEstado.c_str(), 1);
      publicarSensores();
    } else {
      Serial.printf("falló (%d), reintento en 2 s\n", mqtt.state());
      delay(2000);
    }
  }
}

void actualizarActuadores(unsigned long ahora) {
  digitalWrite(PIN_ARMADO, estado != "desarmada");

  if (sirena) {
    // Sirena de dos tonos que alterna cada 400 ms.
    tone(PIN_SIRENA, (ahora / 400) % 2 ? 1800 : 1200);
  } else {
    noTone(PIN_SIRENA);
  }

  if (estrobo) {
    if (ahora - ultimoDestello >= ESTROBO_MS) {
      ultimoDestello = ahora;
      ledEstrobo = !ledEstrobo;
      digitalWrite(PIN_ESTROBO, ledEstrobo);
    }
  } else if (ledEstrobo) {
    ledEstrobo = false;
    digitalWrite(PIN_ESTROBO, LOW);
  }
}

void leerSensores(unsigned long ahora) {
  bool pir = digitalRead(PIN_PIR) == HIGH;
  if (pir && !pirPrevio && ahora - ultimoPir >= REARME_PIR_MS) {
    ultimoPir = ahora;
    publicarEvento("pir", "Movimiento detectado (PIR ESP32)");
  }
  if (pir != pirPrevio) {
    pirPrevio = pir;
    publicarSensores();
  }

  bool abierta = digitalRead(PIN_REED) == HIGH;
  if (abierta != lecturaReed) {
    lecturaReed = abierta;
    cambioReed = ahora;
  }
  if (lecturaReed != puertaPrevia && ahora - cambioReed >= ANTIRREBOTE_MS) {
    puertaPrevia = lecturaReed;
    if (puertaPrevia) publicarEvento("reed", "Apertura de puerta (reed ESP32)");
    publicarSensores();
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_PIR, INPUT);
  pinMode(PIN_REED, INPUT_PULLUP);
  pinMode(PIN_SIRENA, OUTPUT);
  pinMode(PIN_ESTROBO, OUTPUT);
  pinMode(PIN_ARMADO, OUTPUT);

  tEvento = String(CANAL) + "/evento";
  tSensores = String(CANAL) + "/sensores";
  tNodo = String(CANAL) + "/nodo";
  tEstado = String(CANAL) + "/estado";

  lecturaReed = puertaPrevia = digitalRead(PIN_REED) == HIGH;

  conectarWifi();
  red.setCACert(CA_BROKER);
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setCallback(alRecibir);
  mqtt.setBufferSize(512);
  mqtt.setKeepAlive(60);  // margen si la simulación de Wokwi corre lenta
  conectarMqtt();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) conectarWifi();
  if (!mqtt.connected()) conectarMqtt();
  mqtt.loop();

  unsigned long ahora = millis();
  leerSensores(ahora);
  actualizarActuadores(ahora);

  if (ahora - ultimoLatido >= LATIDO_MS) {
    ultimoLatido = ahora;
    publicarSensores();
  }
  delay(10);
}
