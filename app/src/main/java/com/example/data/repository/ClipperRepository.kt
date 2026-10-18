package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.db.ClipEntity
import com.example.data.db.FeviDatabase
import com.example.data.db.ProjectEntity
import com.example.data.db.TranscriptEntity
import com.example.data.model.AspectRatio
import com.example.data.model.ClipCandidate
import com.example.data.model.ClipMetadata
import com.example.data.model.ClipStatus
import com.example.data.model.ExportResolution
import com.example.data.model.SubtitlePreset
import com.example.data.model.SubtitleStyleConfig
import com.example.data.model.TranscriptSegment
import com.example.data.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "fevi_settings")

class ClipperRepository(private val context: Context) {
    private val db = FeviDatabase.getDatabase(context)
    private val projectDao = db.projectDao()
    private val clipDao = db.clipDao()
    private val transcriptDao = db.transcriptDao()

    companion object {
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_DEFAULT_ASPECT = stringPreferencesKey("default_aspect_ratio")
        private val KEY_DEFAULT_PRESET = stringPreferencesKey("default_subtitle_preset")
        private val KEY_DEFAULT_DURATION = stringPreferencesKey("default_clip_duration")
        private val KEY_EXPORT_RESOLUTION = stringPreferencesKey("export_resolution")
    }

    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()
    val allExportedClips: Flow<List<ClipEntity>> = clipDao.getAllExportedClips()

    fun getClipsForProject(projectId: Long): Flow<List<ClipEntity>> = clipDao.getClipsForProject(projectId)

    suspend fun getProjectById(projectId: Long): ProjectEntity? = projectDao.getProjectById(projectId)

    suspend fun getClipById(clipId: String): ClipEntity? = clipDao.getClipById(clipId)

    suspend fun createProject(metadata: VideoMetadata): Long = withContext(Dispatchers.IO) {
        val entity = ProjectEntity(
            title = metadata.fileName.substringBeforeLast("."),
            sourceVideoUri = metadata.uri,
            durationMs = metadata.durationMs,
            width = metadata.width,
            height = metadata.height,
            frameRate = metadata.frameRate,
            sizeBytes = metadata.sizeBytes
        )
        projectDao.insertProject(entity)
    }

    suspend fun saveClips(clips: List<ClipCandidate>) = withContext(Dispatchers.IO) {
        val entities = clips.map { c ->
            val hashtagsJson = JSONArray(c.metadata?.hashtags ?: emptyList<String>()).toString()
            val kwJson = JSONArray(c.metadata?.searchKeywords ?: emptyList<String>()).toString()
            ClipEntity(
                id = c.id,
                projectId = c.projectId,
                title = c.title,
                startMs = c.startMs,
                endMs = c.endMs,
                durationMs = c.durationMs,
                reasonWhySelected = c.reasonWhySelected,
                score = c.score,
                aspectRatio = c.aspectRatio.name,
                status = c.status.name,
                outputPath = c.outputPath,
                youtubeTitle = c.metadata?.youtubeTitle ?: "",
                youtubeDescription = c.metadata?.youtubeDescription ?: "",
                socialCaption = c.metadata?.socialCaption ?: "",
                hashtagsJson = hashtagsJson,
                searchKeywordsJson = kwJson,
                thumbnailText = c.metadata?.thumbnailText ?: "",
                pinnedComment = c.metadata?.pinnedComment ?: "",
                summary = c.metadata?.summary ?: "",
                srtContent = c.srtContent
            )
        }
        clipDao.insertClips(entities)
    }

    suspend fun updateClip(clip: ClipEntity) = withContext(Dispatchers.IO) {
        clipDao.updateClip(clip)
    }

    suspend fun deleteClip(clipId: String) = withContext(Dispatchers.IO) {
        clipDao.deleteClipById(clipId)
    }

    suspend fun deleteProject(projectId: Long) = withContext(Dispatchers.IO) {
        clipDao.deleteClipsForProject(projectId)
        transcriptDao.deleteTranscriptForProject(projectId)
        projectDao.deleteProjectById(projectId)
    }

    suspend fun saveTranscript(
        projectId: Long,
        segments: List<TranscriptSegment>,
        fullText: String,
        lang: String = "en"
    ) = withContext(Dispatchers.IO) {
        val jsonArray = JSONArray()
        segments.forEach { seg ->
            val obj = JSONObject().apply {
                put("id", seg.id)
                put("startMs", seg.startMs)
                put("endMs", seg.endMs)
                put("text", seg.text)
                put("hookScore", seg.hookScore)
                put("speechEnergy", seg.speechEnergy)
            }
            jsonArray.put(obj)
        }
        transcriptDao.insertTranscript(
            TranscriptEntity(
                projectId = projectId,
                segmentsJson = jsonArray.toString(),
                fullText = fullText,
                detectedLanguage = lang
            )
        )
    }

    suspend fun getTranscriptSegments(projectId: Long): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        val entity = transcriptDao.getTranscriptForProject(projectId) ?: return@withContext emptyList()
        val list = mutableListOf<TranscriptSegment>()
        try {
            val jsonArray = JSONArray(entity.segmentsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    TranscriptSegment(
                        id = obj.optInt("id", i + 1),
                        startMs = obj.optLong("startMs"),
                        endMs = obj.optLong("endMs"),
                        text = obj.optString("text"),
                        hookScore = obj.optDouble("hookScore", 60.0).toFloat(),
                        speechEnergy = obj.optDouble("speechEnergy", 0.5).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            // fallback
        }
        list
    }

    // Settings DataStore
    val geminiApiKey: Flow<String> = context.dataStore.data.map { it[KEY_GEMINI_API_KEY] ?: "" }
    val defaultAspectRatio: Flow<AspectRatio> = context.dataStore.data.map {
        try {
            AspectRatio.valueOf(it[KEY_DEFAULT_ASPECT] ?: AspectRatio.VERTICAL_9_16.name)
        } catch (e: Exception) {
            AspectRatio.VERTICAL_9_16
        }
    }
    val defaultPreset: Flow<SubtitlePreset> = context.dataStore.data.map {
        try {
            SubtitlePreset.valueOf(it[KEY_DEFAULT_PRESET] ?: SubtitlePreset.BOLD_YELLOW.name)
        } catch (e: Exception) {
            SubtitlePreset.BOLD_YELLOW
        }
    }
    val defaultDurationSec: Flow<Int> = context.dataStore.data.map {
        it[KEY_DEFAULT_DURATION]?.toIntOrNull() ?: 30
    }
    val exportResolution: Flow<ExportResolution> = context.dataStore.data.map {
        try {
            ExportResolution.valueOf(it[KEY_EXPORT_RESOLUTION] ?: ExportResolution.RES_1080P.name)
        } catch (e: Exception) {
            ExportResolution.RES_1080P
        }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { it[KEY_GEMINI_API_KEY] = key.trim() }
    }

    suspend fun setDefaultAspectRatio(aspectRatio: AspectRatio) {
        context.dataStore.edit { it[KEY_DEFAULT_ASPECT] = aspectRatio.name }
    }

    suspend fun setDefaultPreset(preset: SubtitlePreset) {
        context.dataStore.edit { it[KEY_DEFAULT_PRESET] = preset.name }
    }

    suspend fun setDefaultDurationSec(sec: Int) {
        context.dataStore.edit { it[KEY_DEFAULT_DURATION] = sec.toString() }
    }

    suspend fun setExportResolution(resolution: ExportResolution) {
        context.dataStore.edit { it[KEY_EXPORT_RESOLUTION] = resolution.name }
    }

    // Cache management
    fun getTempStorageSizeBytes(): Long {
        val dirs = listOf(
            File(context.cacheDir, "fevi_videos"),
            File(context.cacheDir, "fevi_rendered")
        )
        var total = 0L
        dirs.forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().forEach { file ->
                    if (file.isFile) total += file.length()
                }
            }
        }
        return total
    }

    fun cleanTempFiles(): Long {
        val dirs = listOf(
            File(context.cacheDir, "fevi_videos"),
            File(context.cacheDir, "fevi_rendered")
        )
        var freed = 0L
        dirs.forEach { dir ->
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    val len = file.length()
                    if (file.delete()) freed += len
                }
            }
        }
        return freed
    }
}
