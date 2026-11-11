package com.example.data

/**
 * Modelo de datos para la telemetría del ESP32.
 * Compatible con la estructura requerida y con TelemetriaUiState.
 */
data class TelemetriaState(
    val temp: Float = 0f,
    val hum: Int = 0,
    val estado: String = "sin_datos",
    val timestamp: Long = 0L
) {
    /**
     * Alias de propiedad 'st' para mantener compatibilidad con el JSON entrante
     * {"temp": 24.5, "hum": 60, "st": "normal" | "alerta"}
     */
    val st: String get() = estado
}

/**
 * Typealias explícito para cumplir con ambos nombres requeridos en la especificación.
 */
typealias TelemetriaUiState = TelemetriaState

/**
 * Registro para el historial de lecturas recibidas.
 */
data class HistorialItem(
    val id: Long = System.currentTimeMillis(),
    val temp: Float,
    val hum: Int,
    val estado: String,
    val timestamp: Long
)

/**
 * Estado de conexión con el broker MQTT HiveMQ.
 */
enum class MqttConnectionStatus {
    DESCONECTADO,
    CONECTANDO,
    CONECTADO,
    ERROR
}

/**
 * Formato del payload enviado al broker MQTT para el actuador.
 */
enum class FormatoComandoActuador(val label: String) {
    JSON_NUMERICO("JSON: {\"actuador\": 1/0}"),
    JSON_TEXTO("JSON: {\"cmd\": \"ON/OFF\"}"),
    TEXTO_ON_OFF("Texto: ON / OFF"),
    BINARIO_1_0("Binario: 1 / 0")
}
