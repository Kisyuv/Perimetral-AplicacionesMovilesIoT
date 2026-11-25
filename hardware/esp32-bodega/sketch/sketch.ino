// Perimetral: nodo ESP32 de la alarma de bodega.
// PIR de movimiento + reed switch de puerta -> eventos por MQTT.
// La app (panel) decide si hay alarma y publica el estado; el ESP32 maneja sirena y estrobo.
// Sirve igual en Wokwi (red Wokwi-GUEST) y en un ESP32 físico (cambiar WIFI_SSID/WIFI_PASS en secrets.h).

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <LittleFS.h>
#include <time.h>
#include "secrets.h"  // WIFI_SSID, WIFI_PASS, MQTT_HOST, MQTT_USER, MQTT_PASS, CANAL (ver secrets.example.h)

// --- Configuración ---
const uint16_t MQTT_PORT = 8883;  // MQTT sobre TLS (EMQX Cloud Serverless)
// CANAL (en secrets.h) debe coincidir con el "Canal" que se ingresa en la app al elegir el nodo ESP32.

// DigiCert Global Root G2: CA raíz pública que firma el certificado de EMQX Cloud (no es un secreto).
const char* CA_BROKER =
"-----BEGIN CERTIFICATE-----\n"
"MIIDjjCCAnagAwIBAgIQAzrx5qcRqaC7KGSxHQn65TANBgkqhkiG9w0BAQsFADBh\n"
"MQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3\n"
"d3cuZGlnaWNlcnQuY29tMSAwHgYDVQQDExdEaWdpQ2VydCBHbG9iYWwgUm9vdCBH\n"
"MjAeFw0xMzA4MDExMjAwMDBaFw0zODAxMTUxMjAwMDBaMGExCzAJBgNVBAYTAlVT\n"
"MRUwEwYDVQQKEwxEaWdpQ2VydCBJbmMxGTAXBgNVBAsTEHd3dy5kaWdpY2VydC5j\n"
"b20xIDAeBgNVBAMTF0RpZ2lDZXJ0IEdsb2JhbCBSb290IEcyMIIBIjANBgkqhkiG\n"
"9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuzfNNNx7a8myaJCtSnX/RrohCgiN9RlUyfuI\n"
"2/Ou8jqJkTx65qsGGmvPrC3oXgkkRLpimn7Wo6h+4FR1IAWsULecYxpsMNzaHxmx\n"
"1x7e/dfgy5SDN67sH0NO3Xss0r0upS/kqbitOtSZpLYl6ZtrAGCSYP9PIUkY92eQ\n"
"q2EGnI/yuum06ZIya7XzV+hdG82MHauVBJVJ8zUtluNJbd134/tJS7SsVQepj5Wz\n"
"tCO7TG1F8PapspUwtP1MVYwnSlcUfIKdzXOS0xZKBgyMUNGPHgm+F6HmIcr9g+UQ\n"
"vIOlCsRnKPZzFBQ9RnbDhxSJITRNrw9FDKZJobq7nMWxM4MphQIDAQABo0IwQDAP\n"
"BgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBhjAdBgNVHQ4EFgQUTiJUIBiV\n"
"5uNu5g/6+rkS7QYXjzkwDQYJKoZIhvcNAQELBQADggEBAGBnKJRvDkhj6zHd6mcY\n"
"1Yl9PMWLSn/pvtsrF9+wX3N3KjITOYFnQoQj8kVnNeyIv/iPsGEMNKSuIEyExtv4\n"
"NeF22d+mQrvHRAiGfzZ0JFrabA0UWTW98kndth/Jsw1HKj2ZL7tcu7XUIOGZX1NG\n"
"Fdtom/DzMNU+MeKNhJ7jitralj41E6Vf8PlwUHBHQRFXGU7Aj64GxJUTFy8bJZ91\n"
"8rGOmaFvE7FBcf6IKshPECBV1/MUReXgRPTqh5Uykw7+U0b6LJ3/iyK5S9kJRaTe\n"
"pLiaWN0bfVKfjllDiIGknibVb63dDcY3fe0Dkhvld1927jyNxF1WW6LZZm6zNTfl\n"
"MrY=\n"
"-----END CERTIFICATE-----\n";

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
const unsigned long ESPERA_MIN_MS = 1000;    // reconexión: 1, 2, 4... s hasta el tope
const unsigned long ESPERA_MAX_MS = 60000;
const unsigned long ESPERA_MIN_WIFI_MS = 4000;  // WiFi: 4, 8, 16... s; asociarse tarda unos segundos y un begin() nuevo corta el anterior

// --- Eventos pendientes (solo mientras no hay MQTT): un JSON por línea en flash ---
// Dos archivos de hasta 100 eventos: al llenarse el actual, el viejo se borra y el actual pasa a viejo.
// Así nunca se reescribe un archivo entero por cada evento (menos desgaste de la flash).
const char* ARCHIVO_PEND = "/pend.jsonl";
const char* ARCHIVO_VIEJO = "/pend_viejo.jsonl";
const int MAX_POR_ARCHIVO = 100;  // tope total: 200 eventos; al llenarse se descartan los 100 más antiguos

WiFiClientSecure red;
PubSubClient mqtt(red);
String tEvento, tSensores, tNodo, tEstado, tAck;

String estado = "desarmada";
bool sirena = false, estrobo = false;

bool pirPrevio = false, puertaPrevia = false;
unsigned long ultimoPir = 0, cambioReed = 0, ultimoLatido = 0, ultimoDestello = 0;
bool lecturaReed = false, ledEstrobo = false;

unsigned long esperaWifi = ESPERA_MIN_WIFI_MS, proximoWifi = 0;
unsigned long esperaMqtt = ESPERA_MIN_MS, proximoMqtt = 0;
bool wifiOk = false, mqttOk = false;
// QoS 0 no tiene confirmación: si la red se cae, lo publicado hasta que se nota el corte (keepalive)
// se pierde en el socket muerto. Los últimos eventos quedan aquí y, al detectar el corte, los
// de la ventana de duda pasan a flash. El reenvío puede duplicar alguno: la app deduplica por id.
const int N_RECIENTES = 20;
const unsigned long VENTANA_DUDA_MS = 30000;  // > 1,5 × keepalive (15 s)
String recientes[N_RECIENTES];
unsigned long msRecientes[N_RECIENTES];
int iReciente = 0;
int nPend = 0;  // eventos en ARCHIVO_PEND (el viejo, si existe, tiene MAX_POR_ARCHIVO)

// Epoch en segundos por NTP; 0 mientras no haya sincronizado (el reloj arranca en 1970).
uint32_t epoch() {
  time_t t = time(nullptr);
  return t > 1700000000 ? (uint32_t)t : 0;
}

void publicarSensores() {
  if (!mqtt.connected()) return;  // el estado de sensores es retenido: basta el último, no se guarda
  JsonDocument doc;
  doc["pir"] = pirPrevio;
  doc["puerta"] = puertaPrevia;
  doc["rssi"] = WiFi.RSSI();
  doc["ts"] = epoch();
  char buf[128];
  serializeJson(doc, buf);
  mqtt.publish(tSensores.c_str(), buf, true);
}

void guardarPendiente(const String& linea) {
  if (nPend >= MAX_POR_ARCHIVO) {
    LittleFS.remove(ARCHIVO_VIEJO);  // se pierden los 100 más antiguos
    LittleFS.rename(ARCHIVO_PEND, ARCHIVO_VIEJO);
    nPend = 0;
  }
  File f = LittleFS.open(ARCHIVO_PEND, "a");
  if (!f) {
    Serial.println("No se pudo abrir el archivo de pendientes");
    return;
  }
  f.print(linea);
  f.print('\n');
  f.close();
  nPend++;
  Serial.printf("Sin MQTT: evento guardado en flash (%d en el archivo actual)\n", nPend);
}

// Publica en orden un archivo de pendientes y lo borra. false si algo falla (se conserva: el id evita duplicados).
bool reenviarArchivo(const char* ruta) {
  if (!LittleFS.exists(ruta)) return true;
  File f = LittleFS.open(ruta, "r");
  if (!f) return false;
  while (f.available()) {
    String linea = f.readStringUntil('\n');
    if (linea.length() && !mqtt.publish(tEvento.c_str(), linea.c_str())) {
      f.close();
      return false;
    }
  }
  f.close();
  LittleFS.remove(ruta);
  return true;
}

// Reenvía lo guardado sin conexión, primero el archivo viejo.
void reenviarPendientes() {
  if (!reenviarArchivo(ARCHIVO_VIEJO)) return;
  if (!reenviarArchivo(ARCHIVO_PEND)) return;
  if (nPend) Serial.println("Eventos pendientes reenviados");
  nPend = 0;
}

void publicarEvento(const char* sensor, const char* detalle) {
  JsonDocument doc;
  doc["sensor"] = sensor;
  doc["detalle"] = detalle;
  doc["ms"] = millis();
  doc["ts"] = epoch();
  // Id único del evento: así varios paneles registran el mismo evento una sola vez en Firestore.
  doc["id"] = String((uint32_t)ESP.getEfuseMac(), HEX) + "-" + String(esp_random(), HEX);
  char buf[200];
  serializeJson(doc, buf);
  if (WiFi.status() == WL_CONNECTED && mqtt.connected() && mqtt.publish(tEvento.c_str(), buf)) {
    recientes[iReciente] = buf;
    msRecientes[iReciente] = millis();
    iReciente = (iReciente + 1) % N_RECIENTES;
  } else {
    guardarPendiente(buf);
  }
  Serial.printf("Evento %s: %s\n", sensor, detalle);
}

void publicarAck(const char* id, const char* motivo) {
  JsonDocument doc;
  doc["id"] = id;
  doc["ok"] = motivo == nullptr;
  if (motivo) doc["motivo"] = motivo;
  doc["ts"] = epoch();
  char buf[160];
  serializeJson(doc, buf);
  mqtt.publish(tAck.c_str(), buf);
}

// Devuelve nullptr si la orden es válida, o el motivo del rechazo (se envía en el ERR).
const char* validarOrden(JsonDocument& doc) {
  if (!doc.is<JsonObject>()) return "no_es_objeto";
  for (JsonPair kv : doc.as<JsonObject>()) {
    const char* k = kv.key().c_str();
    if (strcmp(k, "id") && strcmp(k, "estado") && strcmp(k, "sirena") && strcmp(k, "estrobo")) return "clave_desconocida";
  }
  if (doc["id"].isNull() || doc["estado"].isNull() || doc["sirena"].isNull() || doc["estrobo"].isNull()) return "campo_faltante";
  if (!doc["id"].is<const char*>() || !doc["estado"].is<const char*>() || !doc["sirena"].is<bool>() || !doc["estrobo"].is<bool>()) return "tipo_incorrecto";
  size_t n = strlen(doc["id"].as<const char*>());
  if (n < 1 || n > 36) return "id_invalido";
  const char* e = doc["estado"].as<const char*>();
  if (strcmp(e, "desarmada") && strcmp(e, "armada") && strcmp(e, "alarma")) return "estado_invalido";
  return nullptr;
}

void alRecibir(char* topic, byte* payload, unsigned int largo) {
  JsonDocument doc;
  const char* motivo = deserializeJson(doc, payload, largo) ? "json_invalido" : validarOrden(doc);
  // Sin id válido el ERR sale con id vacío: la app no puede asociarlo a una orden.
  const char* id = doc["id"] | "";
  if (strlen(id) > 36) id = "";  // evita un ACK truncado (JSON roto) con un id absurdo
  if (motivo) {
    Serial.printf("Orden rechazada (%s): %.*s\n", motivo, (int)largo, (const char*)payload);
  } else {
    estado = doc["estado"].as<String>();
    sirena = doc["sirena"];
    estrobo = doc["estrobo"];
    Serial.printf("Estado del panel: %s sirena=%d estrobo=%d\n", estado.c_str(), sirena, estrobo);
  }
  publicarAck(id, motivo);
}

// Reconexión sin bloquear: loop() sigue leyendo sensores y manejando actuadores.
void mantenerWifi(unsigned long ahora) {
  if (WiFi.status() == WL_CONNECTED) {
    if (!wifiOk) {
      wifiOk = true;
      esperaWifi = ESPERA_MIN_WIFI_MS;
      Serial.printf("WiFi OK, IP %s\n", WiFi.localIP().toString().c_str());
      configTime(0, 0, "pool.ntp.org");  // UTC; el epoch lo da time() cuando sincroniza
    }
    return;
  }
  if (wifiOk) {
    wifiOk = false;
    Serial.println("WiFi perdido");
    mqtt.disconnect();  // sin esto PubSubClient sigue "conectado" hasta el keepalive y publica al vacío
  }
  if ((long)(ahora - proximoWifi) < 0) return;
  Serial.printf("Conectando a WiFi %s (si falla, reintento en %lu s)\n", WIFI_SSID, esperaWifi / 1000);
  WiFi.begin(WIFI_SSID, WIFI_PASS, 6);  // canal 6 acelera la conexión en Wokwi; un AP físico lo ignora
  proximoWifi = ahora + esperaWifi;
  esperaWifi = min(esperaWifi * 2, ESPERA_MAX_MS);
}

// Al notar el corte: lo enviado en la ventana de duda pudo no llegar; se guarda en flash, del más antiguo al más nuevo.
void rescatarRecientes(unsigned long ahora) {
  for (int k = 0; k < N_RECIENTES; k++) {
    int i = (iReciente + k) % N_RECIENTES;
    if (recientes[i].length() && ahora - msRecientes[i] < VENTANA_DUDA_MS) guardarPendiente(recientes[i]);
    recientes[i] = "";
  }
}
void mantenerMqtt(unsigned long ahora) {
  if (mqtt.connected()) return;
  if (mqttOk) {
    mqttOk = false;
    Serial.println("MQTT desconectado");
    rescatarRecientes(ahora);
  }
  if (!wifiOk || (long)(ahora - proximoMqtt) < 0) return;
  String id = "perimetral-esp32-" + String((uint32_t)ESP.getEfuseMac(), HEX);
  Serial.print("Conectando a MQTT... ");
  // Última voluntad: si se corta, el broker publica "offline" en el tema del nodo.
  if (mqtt.connect(id.c_str(), MQTT_USER, MQTT_PASS, tNodo.c_str(), 1, true, "offline")) {
    Serial.println("OK");
    mqttOk = true;
    esperaMqtt = ESPERA_MIN_MS;
    mqtt.publish(tNodo.c_str(), "online", true);
    mqtt.subscribe(tEstado.c_str(), 1);
    publicarSensores();
    reenviarPendientes();
  } else {
    Serial.printf("falló (%d), reintento en %lu s\n", mqtt.state(), esperaMqtt / 1000);
    proximoMqtt = ahora + esperaMqtt;
    esperaMqtt = min(esperaMqtt * 2, ESPERA_MAX_MS);
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
  tAck = String(CANAL) + "/ack";

  lecturaReed = puertaPrevia = digitalRead(PIN_REED) == HIGH;

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(false);  // los reintentos los hace mantenerWifi con espera creciente

  // true: formatea la partición la primera vez. Sin flash utilizable el nodo sigue, solo sin buffer.
  if (LittleFS.begin(true) && LittleFS.exists(ARCHIVO_PEND)) {
    File f = LittleFS.open(ARCHIVO_PEND, "r");
    while (f.available()) if (f.read() == '\n') nPend++;
    f.close();
    Serial.printf("%d eventos pendientes en flash (archivo actual)\n", nPend);
  }

  red.setCACert(CA_BROKER);
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setCallback(alRecibir);
  mqtt.setBufferSize(512);
  mqtt.setKeepAlive(15);  // detecta antes un enlace muerto (define VENTANA_DUDA_MS)
  mqtt.setSocketTimeout(5);  // tope de lo que puede bloquear un intento de conexión
}

void loop() {
  unsigned long ahora = millis();
  mantenerWifi(ahora);
  mantenerMqtt(ahora);
  mqtt.loop();

  leerSensores(ahora);
  actualizarActuadores(ahora);

  if (ahora - ultimoLatido >= LATIDO_MS) {
    ultimoLatido = ahora;
    publicarSensores();
  }
  delay(10);
}
