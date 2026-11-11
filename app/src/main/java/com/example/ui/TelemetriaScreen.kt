package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.HistorialItem
import com.example.data.MqttConnectionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetriaScreen(vm: TelemetriaViewModel) {
    val context = LocalContext.current

    val state by vm.uiState.collectAsState()
    val connectionStatus by vm.connectionStatus.collectAsState()
    val statusDetail by vm.statusDetail.collectAsState()
    val historial by vm.historial.collectAsState()
    val currentTimeMillis by vm.currentTimeMillis.collectAsState()
    val duplaNumero by vm.duplaNumero.collectAsState()
    val currentTopic by vm.currentTopic.collectAsState()
    val topicoActuador by vm.topicoActuador.collectAsState()
    val tramasDescartadas by vm.tramasDescartadas.collectAsState()

    val actuadorEncendido by vm.actuadorEncendido.collectAsState()
    val modoLazoCerrado by vm.modoLazoCerrado.collectAsState()
    val formatoActuador by vm.formatoActuador.collectAsState()
    val ultimoComandoEnviado by vm.ultimoComandoEnviado.collectAsState()
    val isSegundoPlanoActivo by vm.isSegundoPlanoActivo.collectAsState()

    var showDialogCambiarDupla by remember { mutableStateOf(false) }
    var nuevaDuplaTexto by remember { mutableStateOf(duplaNumero) }
    var showMenuFormato by remember { mutableStateOf(false) }

    // Launcher para solicitar permiso de notificaciones en Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            vm.alternarSegundoPlano(context)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionStatus) {
                                        MqttConnectionStatus.CONECTADO -> Color(0xFF22C55E)
                                        MqttConnectionStatus.CONECTANDO -> Color(0xFFF59E0B)
                                        else -> Color(0xFFEF4444)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Telemetría ESP32 · Dupla $duplaNumero",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.reconectarMqtt() },
                        modifier = Modifier.testTag("boton_reconectar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reconectar MQTT"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Banner de Estado del Broker y Tópicos MQTT (Solo HiveMQ)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("banner_broker")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = "Broker",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "broker.hivemq.com:1883",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Chip de estado de conexión
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (connectionStatus) {
                                    MqttConnectionStatus.CONECTADO -> Color(0xFF166534)
                                    MqttConnectionStatus.CONECTANDO -> Color(0xFF854D0E)
                                    else -> Color(0xFF991B1B)
                                }
                            ) {
                                Text(
                                    text = when (connectionStatus) {
                                        MqttConnectionStatus.CONECTADO -> "CONECTADO"
                                        MqttConnectionStatus.CONECTANDO -> "CONECTANDO"
                                        else -> "DESCONECTADO"
                                    },
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Fila de Tópico MQTT con botón de edición rápida de Dupla
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E293B))
                                .clickable {
                                    nuevaDuplaTexto = duplaNumero
                                    showDialogCambiarDupla = true
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Tópico Telemetría (Suscrito):",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = currentTopic,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar Dupla",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Tópico Actuador (Publica): $topicoActuador",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        if (tramasDescartadas > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tramas con ruido descartadas sin crash: $tramasDescartadas",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 2. TarjetaTelemetria principal con Dupla DINÁMICA (NO hardcodeada)
            item {
                TarjetaTelemetria(
                    state = state,
                    dupla = duplaNumero,
                    currentTimeMillis = currentTimeMillis,
                    modifier = Modifier.testTag("tarjeta_telemetria_componente")
                )
            }

            // 3. SECCIÓN SEGUNDO PLANO Y PERSISTENCIA (Room + Foreground Service)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSegundoPlanoActivo) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (isSegundoPlanoActivo) Color(0xFF22C55E) else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tarjeta_segundo_plano_persistencia")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isSegundoPlanoActivo) Color(0xFFDCFCE7) else Color(0xFFE2E8F0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Segundo plano",
                                        tint = if (isSegundoPlanoActivo) Color(0xFF16A34A) else Color(0xFF64748B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "SEGUNDO PLANO & BASE DE DATOS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = if (isSegundoPlanoActivo) Color(0xFF15803D) else Color(0xFF64748B)
                                    )
                                    Text(
                                        text = "Monitoreo en Background",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Switch(
                                checked = isSegundoPlanoActivo,
                                onCheckedChange = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (!hasPermission && !isSegundoPlanoActivo) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            vm.alternarSegundoPlano(context)
                                        }
                                    } else {
                                        vm.alternarSegundoPlano(context)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF16A34A)
                                ),
                                modifier = Modifier.testTag("switch_segundo_plano")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (isSegundoPlanoActivo)
                                "Servicio en primer plano activo con notificación persistente: la app sigue recibiendo telemetría y ejecutando el lazo cerrado aunque apagues la pantalla o minimices."
                            else
                                "Activa el switch para seguir recibiendo datos del ESP32 con la app cerrada y almacenar lecturas continuamente en SQLite (Room).",
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Fila de estado de la Base de Datos Room
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFE2E8F0).copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = "SQLite Room",
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Base de Datos SQLite: ${historial.size} lecturas guardadas",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF334155)
                                )
                            }

                            if (historial.isNotEmpty()) {
                                TextButton(
                                    onClick = { vm.limpiarHistorial() },
                                    modifier = Modifier.testTag("boton_limpiar_historial")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Limpiar",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Limpiar", fontSize = 11.sp, color = Color(0xFFDC2626))
                                }
                            }
                        }
                    }
                }
            }

            // 4. SECCIÓN BIDIRECCIONAL: Control del Actuador y Lazo Cerrado
            item {
                val actuadorColorAnim by animateColorAsState(
                    targetValue = if (actuadorEncendido) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                    animationSpec = tween(durationMillis = 300),
                    label = "ActuadorColorAnim"
                )

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = actuadorColorAnim),
                    border = BorderStroke(
                        1.5.dp,
                        if (actuadorEncendido) Color(0xFF10B981) else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tarjeta_actuador_bidireccional")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (actuadorEncendido) Color(0xFFD1FAE5) else Color(0xFFE2E8F0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = "Actuador",
                                        tint = if (actuadorEncendido) Color(0xFF059669) else Color(0xFF64748B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "CONTROL BIDIRECCIONAL",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = if (actuadorEncendido) Color(0xFF047857) else Color(0xFF475569)
                                    )
                                    Text(
                                        text = "Actuador ESP32",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Badge de estado actual del actuador
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (actuadorEncendido) Color(0xFF10B981) else Color(0xFFE2E8F0)
                            ) {
                                Text(
                                    text = if (actuadorEncendido) "ACTIVADO" else "APAGADO",
                                    color = if (actuadorEncendido) Color.White else Color(0xFF475569),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Banner de Lazo Cerrado Funcionando
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (modoLazoCerrado) Color(0xFFEFF6FF) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (modoLazoCerrado) Color(0xFF93C5FD) else Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Loop,
                                        contentDescription = "Lazo cerrado",
                                        tint = if (modoLazoCerrado) Color(0xFF2563EB) else Color(0xFF64748B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (modoLazoCerrado) "Lazo cerrado funcionando" else "Lazo cerrado desactivado",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (modoLazoCerrado) Color(0xFF1D4ED8) else Color(0xFF475569)
                                        )
                                        Text(
                                            text = "Activa automáticamente el actuador si cruza umbral de alerta",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                Switch(
                                    checked = modoLazoCerrado,
                                    onCheckedChange = { vm.setModoLazoCerrado(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF2563EB)
                                    ),
                                    modifier = Modifier.testTag("switch_lazo_cerrado")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Botón de activación manual del actuador
                        Button(
                            onClick = { vm.alternarActuador() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (actuadorEncendido) Color(0xFFDC2626) else Color(0xFF059669)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("boton_alternar_actuador")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (actuadorEncendido) "DESACTIVAR ACTUADOR" else "ACTIVAR ACTUADOR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Selector de formato de comando y último enviado
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Último comando: $ultimoComandoEnviado",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )

                            Box {
                                TextButton(
                                    onClick = { showMenuFormato = true },
                                    modifier = Modifier.testTag("boton_formato_actuador")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Formato",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(formatoActuador.etiqueta.take(8) + "...", fontSize = 11.sp)
                                }

                                DropdownMenu(
                                    expanded = showMenuFormato,
                                    onDismissRequest = { showMenuFormato = false }
                                ) {
                                    FormatoComandoActuador.values().forEach { formato ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(formato.etiqueta, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("Envía: ${formato.ejemplo}", fontSize = 11.sp, color = Color.Gray)
                                                }
                                            },
                                            onClick = {
                                                vm.setFormatoActuador(formato)
                                                showMenuFormato = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Herramientas de Laboratorio / Pruebas
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tarjeta_herramientas_lab")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Pruebas",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pruebas de Laboratorio (Simulador)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Envía una lectura al broker HiveMQ para verificar la reactividad y el guardado en base de datos:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { vm.enviarLecturaPrueba(alerta = false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("boton_simular_normal")
                            ) {
                                Text("Simular Normal", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { vm.enviarLecturaPrueba(alerta = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("boton_simular_alerta")
                            ) {
                                Text("Simular Alerta", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // 6. Historial de Lecturas Recientes
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Historial",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Historial Persistido (${historial.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Guardado en SQLite Room",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                }
            }

            if (historial.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = "Sin lecturas",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Sin lecturas registradas aún",
                                    fontSize = 14.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Esperando paquetes desde broker.hivemq.com...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            } else {
                items(historial, key = { it.id }) { item ->
                    FilaHistorialItem(item = item)
                }
            }
        }
    }

    // Modal para cambiar número de Dupla
    if (showDialogCambiarDupla) {
        AlertDialog(
            onDismissRequest = { showDialogCambiarDupla = false },
            title = { Text("Configurar Dupla") },
            text = {
                Column {
                    Text(
                        text = "Ingresa el número de tu dupla (ej: 01, 02, 10). Se actualizarán los tópicos en HiveMQ a:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Telemetría: " + TelemetriaViewModel.obtenerTopico(nuevaDuplaTexto),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Actuador: " + TelemetriaViewModel.obtenerTopicoActuador(nuevaDuplaTexto),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF059669)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = nuevaDuplaTexto,
                        onValueChange = { nuevaDuplaTexto = it },
                        label = { Text("Número de Dupla") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_numero_dupla")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.actualizarDupla(nuevaDuplaTexto)
                        showDialogCambiarDupla = false
                    },
                    modifier = Modifier.testTag("boton_confirmar_dupla")
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialogCambiarDupla = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun FilaHistorialItem(item: HistorialItem) {
    val esAlerta = item.estado.equals("alerta", ignoreCase = true)
    val horaFormato = remember(item.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (esAlerta) Color(0xFFFFEBEE) else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (esAlerta) Color(0xFFEF9A9A) else Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_historial_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = horaFormato,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Estado: ${item.estado}",
                    fontSize = 11.sp,
                    color = if (esAlerta) Color(0xFFC62828) else Color(0xFF64748B),
                    fontWeight = if (esAlerta) FontWeight.Bold else FontWeight.Normal
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", item.temp)} °C",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (esAlerta) Color(0xFFC62828) else Color(0xFF0F172A)
                    )
                    Text(
                        text = "Temp",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${item.hum}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (esAlerta) Color(0xFFC62828) else Color(0xFF0F172A)
                    )
                    Text(
                        text = "Hum",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

