package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetriaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(lectura: TelemetriaEntity): Long

    @Query("SELECT * FROM telemetria_lecturas WHERE dupla = :dupla ORDER BY timestamp DESC LIMIT 60")
    fun obtenerHistorial(dupla: String): Flow<List<TelemetriaEntity>>

    @Query("SELECT * FROM telemetria_lecturas WHERE dupla = :dupla ORDER BY timestamp DESC LIMIT 1")
    fun obtenerUltimaLectura(dupla: String): Flow<TelemetriaEntity?>

    @Query("DELETE FROM telemetria_lecturas WHERE dupla = :dupla")
    suspend fun borrarHistorialDupla(dupla: String)
}
