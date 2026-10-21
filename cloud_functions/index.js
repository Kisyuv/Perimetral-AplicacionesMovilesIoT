/**
 * Cloud Function para Firebase (Plan Blaze)
 * Envía notificación push FCM a todos los dispositivos registrados cuando se genera un evento de tipo "alarma".
 * 
 * Para desplegar en Firebase CLI:
 * 1. Inicializar functions: firebase init functions
 * 2. Pegar este código en functions/index.js
 * 3. Desplegar: firebase deploy --only functions
 */

/*
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.enviarNotificacionAlarma = onDocumentCreated("sitios/{siteId}/eventos/{eventoId}", async (event) => {
    const snap = event.data;
    if (!snap) return;

    const evento = snap.data();
    if (evento.tipo !== "alarma") {
        return; // Solo enviar push en eventos de alarma o intrusión
    }

    const db = getFirestore();
    const messaging = getMessaging();

    try {
        // 1. Obtener todos los tokens FCM registrados en users/{uid}/dispositivos/{deviceId}
        const usersSnapshot = await db.collection("users").get();
        const tokens = [];

        for (const userDoc of usersSnapshot.docs) {
            const dispositivosSnap = await userDoc.ref.collection("dispositivos").get();
            dispositivosSnap.forEach(dispDoc => {
                const fcmToken = dispDoc.data().fcmToken;
                if (fcmToken && fcmToken.length > 10 && !tokens.includes(fcmToken)) {
                    tokens.push(fcmToken);
                }
            });
        }

        if (tokens.length === 0) {
            console.log("No hay tokens FCM registrados para notificar.");
            return;
        }

        // 2. Construir carga de notificación de alta prioridad (Canal de Alarma)
        const payload = {
            notification: {
                title: "🚨 ¡INTRUSIÓN EN BODEGA!",
                body: evento.detalle || "Se ha activado una alarma perimetral fuera de horario."
            },
            data: {
                tipo: "alarma",
                origen: evento.origen || "nodo",
                timestamp: String(evento.timestamp || Date.now())
            },
            android: {
                priority: "high",
                notification: {
                    channelId: "perimetral_critical_alarms",
                    sound: "default",
                    priority: "max",
                    visibility: "public"
                }
            },
            tokens: tokens
        };

        // 3. Envío masivo mediante Firebase Cloud Messaging
        const response = await messaging.sendEachForMulticast(payload);
        console.log(`Push enviado exitosamente: ${response.successCount} entregados, ${response.failureCount} fallidos.`);
    } catch (error) {
        console.error("Error al despachar push de alarma:", error);
    }
});
*/
