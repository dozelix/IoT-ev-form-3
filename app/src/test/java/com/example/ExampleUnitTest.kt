package com.example

import com.example.data.TelemetriaState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parseoTelemetriaJsonNormal_isCorrect() {
        val payload = """{"temp":24.5, "hum":60, "st":"normal"}"""
        val json = JSONObject(payload)
        val temp = json.getDouble("temp").toFloat()
        val hum = json.getInt("hum")
        val st = json.optString("st", "normal")

        val state = TelemetriaState(
            temp = temp,
            hum = hum,
            estado = st,
            timestamp = System.currentTimeMillis()
        )

        assertEquals(24.5f, state.temp, 0.01f)
        assertEquals(60, state.hum)
        assertEquals("normal", state.estado)
        assertEquals("normal", state.st)
        assertTrue(state.timestamp > 0L)
    }

    @Test
    fun parseoTelemetriaJsonAlerta_isCorrect() {
        val payload = """{"temp":39.8, "hum":20, "st":"alerta"}"""
        val json = JSONObject(payload)
        val state = TelemetriaState(
            temp = json.getDouble("temp").toFloat(),
            hum = json.getInt("hum"),
            estado = json.optString("st", "normal"),
            timestamp = System.currentTimeMillis()
        )

        assertEquals(39.8f, state.temp, 0.01f)
        assertEquals(20, state.hum)
        assertEquals("alerta", state.estado)
        assertEquals("alerta", state.st)
    }

    @Test
    fun manejoTramaRota_noProvocaCrash() {
        val tramaCorrupta = """{"temp":24.5, "hum": """
        var exceptionCapturada = false
        var state = TelemetriaState(temp = 20.0f, hum = 50, estado = "normal", timestamp = 1000L)

        try {
            val json = JSONObject(tramaCorrupta)
            state = state.copy(
                temp = json.getDouble("temp").toFloat(),
                hum = json.getInt("hum"),
                estado = json.optString("st", "normal")
            )
        } catch (e: Throwable) {
            // Se descarta de forma segura
            exceptionCapturada = true
        }

        assertTrue(exceptionCapturada)
        // El estado permanece intacto y la app no crashea
        assertEquals(20.0f, state.temp, 0.01f)
        assertEquals(50, state.hum)
    }

    @Test
    fun topicosDinamicosDupla_sonCorrectos() {
        val topicoTelemetria01 = com.example.ui.TelemetriaViewModel.obtenerTopico("01")
        val topicoActuador01 = com.example.ui.TelemetriaViewModel.obtenerTopicoActuador("01")
        assertEquals("inacap/ti3042/dupla_01/telemetria", topicoTelemetria01)
        assertEquals("inacap/ti3042/dupla_01/actuador", topicoActuador01)

        val topicoTelemetria05 = com.example.ui.TelemetriaViewModel.obtenerTopico("5")
        val topicoActuador05 = com.example.ui.TelemetriaViewModel.obtenerTopicoActuador("5")
        assertEquals("inacap/ti3042/dupla_05/telemetria", topicoTelemetria05)
        assertEquals("inacap/ti3042/dupla_05/actuador", topicoActuador05)
    }
}
