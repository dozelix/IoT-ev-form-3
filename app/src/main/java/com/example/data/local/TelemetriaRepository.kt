package com.example.data.local

import android.content.Context
import com.example.data.HistorialItem
import com.example.data.TelemetriaState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TelemetriaRepository(private val dao: TelemetriaDao) {

    fun obtenerHistorial(dupla: String): Flow<List<HistorialItem>> {
        return dao.obtenerHistorial(dupla).map { lista ->
            lista.map {
                HistorialItem(
                    id = it.id,
                    temp = it.temp,
                    hum = it.hum,
                    estado = it.estado,
                    timestamp = it.timestamp
                )
            }
        }
    }

    fun obtenerUltimaLectura(dupla: String): Flow<TelemetriaState?> {
        return dao.obtenerUltimaLectura(dupla).map { entity ->
            entity?.let {
                TelemetriaState(
                    temp = it.temp,
                    hum = it.hum,
                    estado = it.estado,
                    timestamp = it.timestamp
                )
            }
        }
    }

    suspend fun guardarLectura(dupla: String, temp: Float, hum: Int, estado: String, timestamp: Long) {
        val entity = TelemetriaEntity(
            temp = temp,
            hum = hum,
            estado = estado,
            dupla = dupla,
            timestamp = timestamp
        )
        dao.insertar(entity)
    }

    suspend fun limpiarHistorial(dupla: String) {
        dao.borrarHistorialDupla(dupla)
    }

    companion object {
        @Volatile
        private var INSTANCE: TelemetriaRepository? = null

        fun getInstance(context: Context): TelemetriaRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getDatabase(context)
                val instance = TelemetriaRepository(db.telemetriaDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
