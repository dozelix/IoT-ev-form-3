package com.example.mqtt

import android.util.Log
import com.example.data.MqttConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

/**
 * Gestor de conexión MQTT para el broker público HiveMQ (broker.hivemq.com:1883).
 * Se ejecuta en segundo plano (Dispatchers.IO) sin autenticación.
 */
class MqttManager(
    private val onMessageReceived: (payload: String) -> Unit
) {
    companion object {
        private const val TAG = "MqttManager"
        const val DEFAULT_BROKER_HOST = "broker.hivemq.com"
        const val DEFAULT_PORT = 1883
        const val DEFAULT_SERVER_URI = "tcp://$DEFAULT_BROKER_HOST:$DEFAULT_PORT"
        const val DEFAULT_TOPIC = "inacap/ti3042/dupla_01/telemetria"
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var mqttClient: MqttAsyncClient? = null

    private var currentTopic: String = DEFAULT_TOPIC

    private val _connectionStatus = MutableStateFlow(MqttConnectionStatus.DESCONECTADO)
    val connectionStatus: StateFlow<MqttConnectionStatus> = _connectionStatus.asStateFlow()

    private val _statusDetail = MutableStateFlow("Iniciando servicio MQTT...")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    fun conectar(topic: String = currentTopic) {
        currentTopic = topic
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    _connectionStatus.value = MqttConnectionStatus.CONECTANDO
                    _statusDetail.value = "Conectando a $DEFAULT_BROKER_HOST:$DEFAULT_PORT..."

                    if (mqttClient?.isConnected == true) {
                        try {
                            mqttClient?.disconnect()
                        } catch (_: Exception) { }
                    }

                    val clientId = "Android_ESP32_" + UUID.randomUUID().toString().take(8)
                    val client = MqttAsyncClient(DEFAULT_SERVER_URI, clientId, MemoryPersistence())
                    mqttClient = client

                    val options = MqttConnectOptions().apply {
                        isAutomaticReconnect = true
                        isCleanSession = true
                        connectionTimeout = 15
                        keepAliveInterval = 30
                    }

                    client.setCallback(object : MqttCallbackExtended {
                        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                            Log.d(TAG, "Conexión completada (reconnect=$reconnect) a $serverURI")
                            _connectionStatus.value = MqttConnectionStatus.CONECTADO
                            _statusDetail.value = if (reconnect) "Reconectado al broker" else "Conectado al broker"
                            suscribirAlTopico(currentTopic)
                        }

                        override fun connectionLost(cause: Throwable?) {
                            Log.w(TAG, "Conexión perdida con broker HiveMQ", cause)
                            _connectionStatus.value = MqttConnectionStatus.DESCONECTADO
                            _statusDetail.value = "Conexión perdida: ${cause?.localizedMessage ?: "Reintentando..."}"
                        }

                        override fun messageArrived(topic: String?, message: MqttMessage?) {
                            try {
                                val payload = message?.payload?.let { String(it, Charsets.UTF_8) } ?: ""
                                Log.d(TAG, "Mensaje MQTT recibido en '$topic': $payload")
                                onMessageReceived(payload)
                            } catch (e: Throwable) {
                                Log.e(TAG, "Error procesando mensaje MQTT entrante", e)
                            }
                        }

                        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                    })

                    client.connect(options, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken?) {
                            Log.d(TAG, "Conexión exitosa a HiveMQ")
                            _connectionStatus.value = MqttConnectionStatus.CONECTADO
                            _statusDetail.value = "Conectado a HiveMQ:1883"
                            suscribirAlTopico(currentTopic)
                        }

                        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                            Log.e(TAG, "Fallo al conectar con HiveMQ", exception)
                            _connectionStatus.value = MqttConnectionStatus.ERROR
                            _statusDetail.value = "Error al conectar: ${exception?.localizedMessage ?: "Servidor no responde"}"
                        }
                    })

                } catch (e: Exception) {
                    Log.e(TAG, "Excepción en inicialización de MQTT", e)
                    _connectionStatus.value = MqttConnectionStatus.ERROR
                    _statusDetail.value = "Excepción: ${e.localizedMessage}"
                }
            }
        }
    }

    fun cambiarTopico(nuevoTopico: String) {
        if (nuevoTopico.isBlank() || nuevoTopico == currentTopic) return
        val topicoAnterior = currentTopic
        currentTopic = nuevoTopico.trim()

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (mqttClient?.isConnected == true) {
                        try {
                            mqttClient?.unsubscribe(topicoAnterior)
                        } catch (_: Exception) { }
                        suscribirAlTopico(currentTopic)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al cambiar tópico", e)
                }
            }
        }
    }

    private fun suscribirAlTopico(topico: String) {
        try {
            mqttClient?.subscribe(topico, 0, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Suscripción activa al tópico: $topico")
                    _statusDetail.value = "Suscrito a $topico"
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Error suscribiendo al tópico: $topico", exception)
                    _statusDetail.value = "Error suscripción a $topico"
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "Excepción al suscribir a $topico", e)
        }
    }

    fun publicarMensaje(topico: String, payload: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (mqttClient?.isConnected == true) {
                        val message = MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
                            qos = 0
                            isRetained = false
                        }
                        mqttClient?.publish(topico, message)
                        Log.d(TAG, "Publicado mensaje a $topico: $payload")
                    } else {
                        Log.w(TAG, "No se puede publicar, cliente no conectado")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al publicar mensaje", e)
                }
            }
        }
    }

    fun desconectar() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    mqttClient?.disconnect()
                    _connectionStatus.value = MqttConnectionStatus.DESCONECTADO
                    _statusDetail.value = "Desconectado"
                } catch (e: Exception) {
                    Log.e(TAG, "Error al desconectar", e)
                }
            }
        }
    }
}
