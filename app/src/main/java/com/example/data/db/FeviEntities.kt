package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sourceVideoUri: String,
    val sourceVideoPath: String = "",
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 30f,
    val sizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE"
)

@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey
    val id: String,
    val projectId: Long,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val reasonWhySelected: String,
    val score: Float,
    val aspectRatio: String,
    val status: String,
    val outputPath: String? = null,
    val youtubeTitle: String = "",
    val youtubeDescription: String = "",
    val socialCaption: String = "",
    val hashtagsJson: String = "[]",
    val searchKeywordsJson: String = "[]",
    val thumbnailText: String = "",
    val pinnedComment: String = "",
    val summary: String = "",
    val srtContent: String? = null
)

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val segmentsJson: String,
    val fullText: String,
    val detectedLanguage: String = "en"
)
