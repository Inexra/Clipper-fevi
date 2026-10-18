package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)
}

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY score DESC, startMs ASC")
    fun getClipsForProject(projectId: Long): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE id = :clipId LIMIT 1")
    suspend fun getClipById(clipId: String): ClipEntity?

    @Query("SELECT * FROM clips WHERE outputPath IS NOT NULL AND status = 'COMPLETED' ORDER BY startMs DESC")
    fun getAllExportedClips(): Flow<List<ClipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<ClipEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: ClipEntity)

    @Update
    suspend fun updateClip(clip: ClipEntity)

    @Query("DELETE FROM clips WHERE id = :clipId")
    suspend fun deleteClipById(clipId: String)

    @Query("DELETE FROM clips WHERE projectId = :projectId")
    suspend fun deleteClipsForProject(projectId: Long)
}

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts WHERE projectId = :projectId LIMIT 1")
    suspend fun getTranscriptForProject(projectId: Long): TranscriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: TranscriptEntity): Long

    @Query("DELETE FROM transcripts WHERE projectId = :projectId")
    suspend fun deleteTranscriptForProject(projectId: Long)
}
