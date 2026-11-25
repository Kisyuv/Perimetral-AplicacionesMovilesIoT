"""Monitor del canal MQTT de Perimetral (EMQX Cloud, MQTT sobre TLS).

Uso:
  python mqtt_monitor.py                 # escucha todos los temas del canal (Ctrl+C para salir)
  python mqtt_monitor.py --estado alarma # publica un estado de prueba al ESP32 (retenido)
  python mqtt_monitor.py --estado desarmada

Requiere: pip install paho-mqtt (2.x)
"""
import argparse
import json
import os
import time
import uuid

import paho.mqtt.client as mqtt

# Mismos valores que MQTT_HOST/MQTT_USER/MQTT_PASS/CANAL de secrets.h, por variables de entorno.
BROKER = os.environ["MQTT_HOST"]
PUERTO = 8883
CANAL = os.environ["MQTT_CANAL"]  # debe coincidir con CANAL de secrets.h y el "Canal MQTT" de la app


def mostrar(_cliente, _datos, msg):
    print(f"{time.strftime('%X')}  {msg.topic}  {msg.payload.decode()}  retain={msg.retain}", flush=True)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--estado", choices=["desarmada", "armada", "alarma"], help="publica este estado en <canal>/estado")
    args = p.parse_args()

    c = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    c.on_message = mostrar
    c.username_pw_set(os.environ["MQTT_USER"], os.environ["MQTT_PASS"])
    c.tls_set()  # valida el certificado con las CA del sistema
    c.connect(BROKER, PUERTO, 60)
    c.subscribe(f"{CANAL}/#")
    c.loop_start()

    if args.estado:
        alarma = args.estado == "alarma"
        carga = json.dumps({"id": str(uuid.uuid4()), "estado": args.estado, "sirena": alarma, "estrobo": alarma})
        time.sleep(2)
        c.publish(f"{CANAL}/estado", carga, qos=1, retain=True)
        print(f"Publicado en {CANAL}/estado: {carga}")
        time.sleep(3)
    else:
        print(f"Escuchando {CANAL}/# en {BROKER}:{PUERTO} (Ctrl+C para salir)")
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            pass
    c.loop_stop()


if __name__ == "__main__":
    main()
