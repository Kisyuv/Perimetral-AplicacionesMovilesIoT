// Perimetral: nodo ESP32 de la alarma de bodega.
// PIR de movimiento + reed switch de puerta -> eventos por MQTT.
// La app (panel) decide si hay alarma y publica el estado; el ESP32 maneja sirena y estrobo.
// Sirve igual en Wokwi (red Wokwi-GUEST) y en un ESP32 físico (cambiar WIFI_SSID/WIFI_PASS).

#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// --- Configuración ---
const char* WIFI_SSID = "Wokwi-GUEST";   // ESP32 físico: SSID de la red de la bodega
const char* WIFI_PASS = "";
const char* MQTT_HOST = "broker.hivemq.com";
const uint16_t MQTT_PORT = 1883;
// Debe coincidir con el "Canal" que se ingresa en la app al elegir el nodo ESP32.
const char* CANAL = "perimetral/a2b39e2e";

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

WiFiClient red;
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
    if (mqtt.connect(id.c_str(), tNodo.c_str(), 1, true, "offline")) {
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
