package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TelemetriaUiState

/**
 * Tarjeta de Telemetría requerida por el usuario con Material 3.
 *
 * Requisitos:
 * 1. Muestra temperatura y humedad con tarjetas grandes.
 * 2. Si 'st' es 'alerta', pinta el fondo con acento rojo de alerta.
 * 3. Muestra un texto inferior: "Recibido hace X seg" calculado en tiempo real
 *    con detección de desconexión en terreno si supera el umbral de muestreo.
 */
@Composable
fun TarjetaTelemetria(
    state: TelemetriaUiState,
    modifier: Modifier = Modifier,
    dupla: String = "01",
    currentTimeMillis: Long = System.currentTimeMillis()
) {
    val esAlerta = state.estado.equals("alerta", ignoreCase = true) || state.st.equals("alerta", ignoreCase = true)
    val tieneDatos = state.timestamp > 0L
    val segundosTranscurridos = if (tieneDatos) {
        maxOf(0L, (currentTimeMillis - state.timestamp) / 1000L)
    } else 0L

    // Si pasan más de 10 segundos sin recibir paquetes, advertir posible desconexión de campo
    val estaDesconectadoCampo = tieneDatos && segundosTranscurridos >= 10L

    val containerColor by animateColorAsState(
        targetValue = when {
            esAlerta -> Color(0xFFFFEBEE) // Acento rojo de alerta
            estaDesconectadoCampo -> Color(0xFFFFF7ED) // Ámbar de aviso de campo inactivo
            else -> Color(0xFFF1F5F9) // Neutro azulado limpio
        },
        animationSpec = tween(durationMillis = 350),
        label = "TarjetaColorAnim"
    )

    val borderStroke = when {
        esAlerta -> BorderStroke(2.dp, Color(0xFFE53935))
        estaDesconectadoCampo -> BorderStroke(1.5.dp, Color(0xFFF59E0B))
        else -> BorderStroke(1.dp, Color(0xFFCBD5E1))
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .testTag("tarjeta_telemetria_principal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Cabecera de la tarjeta con Badge de Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TELEMETRÍA EN VIVO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (esAlerta) Color(0xFFC62828) else Color(0xFF475569),
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "ESP32 · Dupla $dupla",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }

                // Chip de estado
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        esAlerta -> Color(0xFFFFCDD2)
                        estaDesconectadoCampo -> Color(0xFFFED7AA)
                        tieneDatos -> Color(0xFFDCFCE7)
                        else -> Color(0xFFE2E8F0)
                    },
                    modifier = Modifier.testTag("badge_estado")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                esAlerta -> Icons.Default.Warning
                                estaDesconectadoCampo -> Icons.Default.SignalCellularConnectedNoInternet0Bar
                                tieneDatos -> Icons.Default.CheckCircle
                                else -> Icons.Default.Warning
                            },
                            contentDescription = "Estado",
                            tint = when {
                                esAlerta -> Color(0xFFB71C1C)
                                estaDesconectadoCampo -> Color(0xFFC2410C)
                                tieneDatos -> Color(0xFF15803D)
                                else -> Color(0xFF475569)
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                esAlerta -> "ALERTA"
                                estaDesconectadoCampo -> "CAMPO INACTIVO"
                                tieneDatos -> state.estado.uppercase()
                                else -> "SIN DATOS"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = when {
                                esAlerta -> Color(0xFFB71C1C)
                                estaDesconectadoCampo -> Color(0xFFC2410C)
                                tieneDatos -> Color(0xFF15803D)
                                else -> Color(0xFF475569)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tarjetas grandes de métricas (Temperatura y Humedad)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tarjeta grande de Temperatura
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tarjeta_temperatura"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (esAlerta) Color(0xFFFFCDD2).copy(alpha = 0.5f) else Color.White
                    ),
                    border = BorderStroke(1.dp, if (esAlerta) Color(0xFFEF9A9A) else Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Temperatura",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF334155),
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFEDD5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Thermostat,
                                    contentDescription = "Icono Temperatura",
                                    tint = Color(0xFFEA580C),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (tieneDatos) String.format(java.util.Locale.US, "%.1f", state.temp) else "--",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            color = if (esAlerta) Color(0xFFB71C1C) else Color(0xFF0F172A),
                            lineHeight = 44.sp
                        )

                        Text(
                            text = "°C",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Tarjeta grande de Humedad
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tarjeta_humedad"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (esAlerta) Color(0xFFFFCDD2).copy(alpha = 0.5f) else Color.White
                    ),
                    border = BorderStroke(1.dp, if (esAlerta) Color(0xFFEF9A9A) else Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Humedad",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF334155),
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0F2FE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = "Icono Humedad",
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (tieneDatos) "${state.hum}" else "--",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            color = if (esAlerta) Color(0xFFB71C1C) else Color(0xFF0F172A),
                            lineHeight = 44.sp
                        )

                        Text(
                            text = "% HR",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Texto inferior en tiempo real
            val textoInferior = when {
                !tieneDatos -> "Esperando datos..."
                estaDesconectadoCampo -> "Sin datos hace ${segundosTranscurridos}s (posible desconexión en terreno)"
                else -> "Recibido hace ${segundosTranscurridos}s"
            }

            val colorTextoInferior = when {
                !tieneDatos -> Color.Gray
                estaDesconectadoCampo -> Color(0xFFC2410C)
                esAlerta -> Color(0xFFC62828)
                else -> Color(0xFF475569)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("texto_recibido_hace"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicador de pulso
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !tieneDatos -> Color.Gray
                                estaDesconectadoCampo -> Color(0xFFF59E0B)
                                esAlerta -> Color(0xFFEF4444)
                                else -> Color(0xFF22C55E)
                            }
                        )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = textoInferior,
                    color = colorTextoInferior,
                    fontSize = 13.sp,
                    fontWeight = if (estaDesconectadoCampo || esAlerta) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
