package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.TelemetriaRepository
import com.example.mqtt.MqttManager
import com.example.ui.TelemetriaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class TelemetriaForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: TelemetriaRepository
    private var mqttManager: MqttManager? = null

    private var currentDupla: String = "01"
    private var currentTopic: String = TelemetriaViewModel.obtenerTopico("01")
    private var topicoActuador: String = TelemetriaViewModel.obtenerTopicoActuador("01")
    private var modoLazoCerrado: Boolean = true

    override fun onCreate() {
        super.onCreate()
        repository = TelemetriaRepository.getInstance(applicationContext)
        crearCanalesNotificacion()
        _isServiceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            detenerServicio()
            return START_NOT_STICKY
        }

        currentDupla = intent?.getStringExtra(EXTRA_DUPLA) ?: currentDupla
        modoLazoCerrado = intent?.getBooleanExtra(EXTRA_LAZO_CERRADO, true) ?: true
        currentTopic = TelemetriaViewModel.obtenerTopico(currentDupla)
        topicoActuador = TelemetriaViewModel.obtenerTopicoActuador(currentDupla)

        val notification = construirNotificacion("Conectando al broker HiveMQ...", "Monitoreando Dupla $currentDupla")
        startForeground(NOTIFICATION_ID, notification)

        iniciarMqttSegundoPlano()

        return START_STICKY
    }

    private fun iniciarMqttSegundoPlano() {
        mqttManager?.desconectar()
        mqttManager = MqttManager { payload ->
            procesarMensajeSegundoPlano(payload)
        }.apply {
            conectar(currentTopic)
        }
    }

    private fun procesarMensajeSegundoPlano(payload: String) {
        serviceScope.launch {
            try {
                if (payload.isBlank()) return@launch

                val json = JSONObject(payload)
                val temp = json.getDouble("temp").toFloat()
                val hum = json.getInt("hum")
                val estado = json.optString("st", "normal")
                val now = System.currentTimeMillis()

                // 1. Guardar permanentemente en la base de datos Room
                repository.guardarLectura(
                    dupla = currentDupla,
                    temp = temp,
                    hum = hum,
                    estado = estado,
                    timestamp = now
                )

                // 2. Actualizar notificación permanente de monitoreo
                val textoEstado = "${String.format(Locale.US, "%.1f", temp)} °C · $hum% HR · Estado: ${estado.uppercase()}"
                actualizarNotificacion("Dupla $currentDupla: $textoEstado")

                // 3. Si llega alerta, disparar notificación prioritaria
                if (estado.equals("alerta", ignoreCase = true)) {
                    dispararNotificacionAlerta(temp, hum)
                }

                // 4. Lazo cerrado automático en segundo plano
                if (modoLazoCerrado && (estado.equals("alerta", ignoreCase = true) || temp >= 30.0f)) {
                    mqttManager?.publicarMensaje(topicoActuador, """{"actuador": 1}""")
                }

            } catch (e: Exception) {
                Log.e("TelemetriaService", "Error parseo en segundo plano descartado", e)
            }
        }
    }

    private fun construirNotificacion(titulo: String, contenido: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, TelemetriaForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titulo)
            .setContentText(contenido)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun actualizarNotificacion(contenido: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, construirNotificacion("Telemetría ESP32 en vivo", contenido))
    }

    private fun dispararNotificacionAlerta(temp: Float, hum: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle("ALERTA TELEMETRÍA ESP32 · Dupla $currentDupla")
            .setContentText("Lectura crítica: ${String.format(Locale.US, "%.1f", temp)} °C / $hum% HR")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(ALERT_NOTIFICATION_ID, alertNotification)
    }

    private fun crearCanalesNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Monitoreo Telemetría",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la recepción MQTT en segundo plano para el ESP32"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Alertas Críticas de Telemetría",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones urgentes ante estado de alerta en el ESP32"
                enableVibration(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun detenerServicio() {
        mqttManager?.desconectar()
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttManager?.desconectar()
        _isServiceRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "canal_telemetria_servicio"
        const val CHANNEL_ALERT_ID = "canal_telemetria_alertas"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002

        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val EXTRA_DUPLA = "EXTRA_DUPLA"
        const val EXTRA_LAZO_CERRADO = "EXTRA_LAZO_CERRADO"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        fun iniciar(context: Context, dupla: String, lazoCerrado: Boolean) {
            val intent = Intent(context, TelemetriaForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
                putExtra(EXTRA_DUPLA, dupla)
                putExtra(EXTRA_LAZO_CERRADO, lazoCerrado)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun detener(context: Context) {
            val intent = Intent(context, TelemetriaForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
