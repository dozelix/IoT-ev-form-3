package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HistorialItem
import com.example.data.MqttConnectionStatus
import com.example.data.TelemetriaState
import com.example.data.TelemetriaUiState
import com.example.data.local.TelemetriaRepository
import com.example.mqtt.MqttManager
import com.example.service.TelemetriaForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class FormatoComandoActuador(val etiqueta: String, val ejemplo: String) {
    JSON_NUMERIC("JSON: {\"actuador\": 1/0}", "{\"actuador\": 1}"),
    JSON_TEXT("JSON: {\"cmd\": \"ON\"/\"OFF\"}", "{\"cmd\": \"ON\"}"),
    TEXT_ON_OFF("Texto: ON / OFF", "ON"),
    BINARY("Binario: 1 / 0", "1")
}

/**
 * ViewModel que orquesta la telemetría, persistencia local con Room,
 * recepción en segundo plano vía Foreground Service,
 * y control bidireccional del actuador en lazo cerrado hacia HiveMQ.
 */
class TelemetriaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TelemetriaRepository.getInstance(application)

    private val _uiState = MutableStateFlow(TelemetriaState())
    val uiState: StateFlow<TelemetriaState> = _uiState.asStateFlow()

    private val _historial = MutableStateFlow<List<HistorialItem>>(emptyList())
    val historial: StateFlow<List<HistorialItem>> = _historial.asStateFlow()

    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMillis: StateFlow<Long> = _currentTimeMillis.asStateFlow()

    private val _duplaNumero = MutableStateFlow("01")
    val duplaNumero: StateFlow<String> = _duplaNumero.asStateFlow()

    private val _currentTopic = MutableStateFlow(obtenerTopico("01"))
    val currentTopic: StateFlow<String> = _currentTopic.asStateFlow()

    private val _topicoActuador = MutableStateFlow(obtenerTopicoActuador("01"))
    val topicoActuador: StateFlow<String> = _topicoActuador.asStateFlow()

    private val _tramasDescartadas = MutableStateFlow(0)
    val tramasDescartadas: StateFlow<Int> = _tramasDescartadas.asStateFlow()

    // Estado del Actuador Bidireccional
    private val _actuadorEncendido = MutableStateFlow(false)
    val actuadorEncendido: StateFlow<Boolean> = _actuadorEncendido.asStateFlow()

    // Modo Lazo Cerrado ("Lazo cerrado funcionando")
    private val _modoLazoCerrado = MutableStateFlow(true)
    val modoLazoCerrado: StateFlow<Boolean> = _modoLazoCerrado.asStateFlow()

    private val _formatoActuador = MutableStateFlow(FormatoComandoActuador.JSON_NUMERIC)
    val formatoActuador: StateFlow<FormatoComandoActuador> = _formatoActuador.asStateFlow()

    private val _ultimoComandoEnviado = MutableStateFlow("Ninguno")
    val ultimoComandoEnviado: StateFlow<String> = _ultimoComandoEnviado.asStateFlow()

    // Servicio en segundo plano
    val isSegundoPlanoActivo: StateFlow<Boolean> = TelemetriaForegroundService.isServiceRunning

    // Instancia de MqttManager comunicando a onMensajeMqttRecibido
    val mqttManager = MqttManager(
        onMessageReceived = { payload -> onMensajeMqttRecibido(payload) }
    )

    val connectionStatus: StateFlow<MqttConnectionStatus> = mqttManager.connectionStatus
    val statusDetail: StateFlow<String> = mqttManager.statusDetail

    private var historialJob: Job? = null
    private var ultimaLecturaJob: Job? = null

    init {
        // Observar persistencia de Room para la dupla inicial
        observarPersistenciaParaDupla("01")

        // Iniciar conexión automática al tópico predeterminado
        mqttManager.conectar(_currentTopic.value)

        // Reloj reactivo de 1 segundo para actualizar el tiempo transcurrido en vivo
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                _currentTimeMillis.value = System.currentTimeMillis()
            }
        }
    }

    private fun observarPersistenciaParaDupla(dupla: String) {
        historialJob?.cancel()
        ultimaLecturaJob?.cancel()

        historialJob = viewModelScope.launch(Dispatchers.IO) {
            repository.obtenerHistorial(dupla).collect { lista ->
                _historial.value = lista
            }
        }

        ultimaLecturaJob = viewModelScope.launch(Dispatchers.IO) {
            repository.obtenerUltimaLectura(dupla).collect { guardado ->
                if (guardado != null && _uiState.value.timestamp == 0L) {
                    _uiState.value = guardado
                }
            }
        }
    }

    /**
     * Procesa la trama JSON entrante del ESP32 en segundo plano.
     * Si la trama está dañada, se descarta en el catch previniendo cualquier caída (Zero Crash).
     * Además guarda la lectura en Room para persistencia garantizada.
     */
    fun onMensajeMqttRecibido(payload: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (payload.isBlank()) return@launch

                val json = JSONObject(payload)
                val temp = json.getDouble("temp").toFloat()
                val hum = json.getInt("hum")
                val estado = json.optString("st", "normal")
                val now = System.currentTimeMillis()

                val anteriorEstado = _uiState.value.estado

                _uiState.update {
                    it.copy(
                        temp = temp,
                        hum = hum,
                        estado = estado,
                        timestamp = now
                    )
                }

                // Guardar en la base de datos persistente Room
                viewModelScope.launch(Dispatchers.IO) {
                    repository.guardarLectura(
                        dupla = _duplaNumero.value,
                        temp = temp,
                        hum = hum,
                        estado = estado,
                        timestamp = now
                    )
                }

                // Si cruza a estado "alerta", activar feedback háptico (vibración)
                if (estado.equals("alerta", ignoreCase = true) && !anteriorEstado.equals("alerta", ignoreCase = true)) {
                    dispararVibracionAlerta()
                }

                // Lazo Cerrado Automático: Activa o desactiva el actuador según el dato de telemetría
                if (_modoLazoCerrado.value) {
                    if (estado.equals("alerta", ignoreCase = true) || temp >= 30.0f) {
                        if (!_actuadorEncendido.value) {
                            enviarComandoActuador(activar = true, manual = false)
                        }
                    } else if (estado.equals("normal", ignoreCase = true) && temp < 28.0f) {
                        if (_actuadorEncendido.value) {
                            enviarComandoActuador(activar = false, manual = false)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("MQTT", "Error parseo o trama corrupta descartada: $payload", e)
                _tramasDescartadas.update { it + 1 }
            }
        }
    }

    /**
     * Envía comando al broker MQTT HiveMQ en el tópico del actuador.
     * Nota: La app NUNCA se conecta al microcontrolador; publica exclusivamente a HiveMQ.
     */
    fun enviarComandoActuador(activar: Boolean, manual: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val payload = when (_formatoActuador.value) {
                FormatoComandoActuador.JSON_NUMERIC -> if (activar) """{"actuador": 1}""" else """{"actuador": 0}"""
                FormatoComandoActuador.JSON_TEXT -> if (activar) """{"cmd": "ON"}""" else """{"cmd": "OFF"}"""
                FormatoComandoActuador.TEXT_ON_OFF -> if (activar) "ON" else "OFF"
                FormatoComandoActuador.BINARY -> if (activar) "1" else "0"
            }

            _actuadorEncendido.value = activar
            val origen = if (manual) "Manual" else "Lazo Cerrado Auto"
            _ultimoComandoEnviado.value = "$payload ($origen)"

            // Publicación exclusiva al broker HiveMQ
            mqttManager.publicarMensaje(_topicoActuador.value, payload)
        }
    }

    fun alternarActuador() {
        enviarComandoActuador(!_actuadorEncendido.value, manual = true)
    }

    fun setModoLazoCerrado(habilitado: Boolean) {
        _modoLazoCerrado.value = habilitado
    }

    fun setFormatoActuador(formato: FormatoComandoActuador) {
        _formatoActuador.value = formato
    }

    /**
     * Alterna el servicio en segundo plano (Foreground Service).
     */
    fun alternarSegundoPlano(context: Context) {
        if (TelemetriaForegroundService.isServiceRunning.value) {
            TelemetriaForegroundService.detener(context)
        } else {
            TelemetriaForegroundService.iniciar(context, _duplaNumero.value, _modoLazoCerrado.value)
        }
    }

    /**
     * Permite cambiar la dupla activa (ej: "01", "02", "15") y resuscribe al tópico correspondiente.
     */
    fun actualizarDupla(dupla: String) {
        val limpia = dupla.trim().padStart(2, '0')
        _duplaNumero.value = limpia
        val nuevoTopico = obtenerTopico(limpia)
        _currentTopic.value = nuevoTopico
        _topicoActuador.value = obtenerTopicoActuador(limpia)

        observarPersistenciaParaDupla(limpia)
        mqttManager.cambiarTopico(nuevoTopico)

        // Si el servicio en segundo plano está activo, actualizarlo con la nueva dupla
        val context = getApplication<Application>()
        if (TelemetriaForegroundService.isServiceRunning.value) {
            TelemetriaForegroundService.iniciar(context, limpia, _modoLazoCerrado.value)
        }
    }

    fun limpiarHistorial() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.limpiarHistorial(_duplaNumero.value)
        }
    }

    fun reconectarMqtt() {
        mqttManager.conectar(_currentTopic.value)
    }

    /**
     * Emite una lectura de prueba al tópico MQTT para verificar la visualización
     * en caso de que el ESP32 no esté conectado físicamente aún.
     */
    fun enviarLecturaPrueba(alerta: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val tempPrueba = if (alerta) (36.0f + (Math.random() * 5.0).toFloat()) else (22.0f + (Math.random() * 4.0).toFloat())
            val humPrueba = if (alerta) 25 else (55 + (Math.random() * 15).toInt())
            val stPrueba = if (alerta) "alerta" else "normal"
            val payload = """{"temp":${String.format(java.util.Locale.US, "%.1f", tempPrueba)},"hum":$humPrueba,"st":"$stPrueba"}"""

            // Publica al broker y a su vez procesa localmente
            mqttManager.publicarMensaje(_currentTopic.value, payload)
        }
    }

    /**
     * Dispara vibración en el hardware del teléfono ante eventos de alerta.
     */
    private fun dispararVibracionAlerta() {
        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(300)
                }
            }
        } catch (e: Throwable) {
            Log.w("MQTT", "No se pudo emitir vibración", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mqttManager.desconectar()
    }

    companion object {
        fun obtenerTopico(dupla: String): String {
            val num = dupla.trim().padStart(2, '0')
            return "inacap/ti3042/dupla_$num/telemetria"
        }

        fun obtenerTopicoActuador(dupla: String): String {
            val num = dupla.trim().padStart(2, '0')
            return "inacap/ti3042/dupla_$num/actuador"
        }
    }
}
