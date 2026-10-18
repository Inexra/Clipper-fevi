package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ClipEntity
import com.example.data.db.ProjectEntity
import com.example.data.model.AspectRatio
import com.example.data.model.ClipCandidate
import com.example.data.model.ClipMetadata
import com.example.data.model.ClipStatus
import com.example.data.model.ContentStyle
import com.example.data.model.CustomLengthMode
import com.example.data.model.ExportResolution
import com.example.data.model.MetadataTone
import com.example.data.model.SubtitlePreset
import com.example.data.model.SubtitleStyleConfig
import com.example.data.model.TranscriptSegment
import com.example.data.model.VideoMetadata
import com.example.data.repository.ClipperRepository
import com.example.engine.AudioSpeechEngine
import com.example.engine.AutoClippingEngine
import com.example.engine.MetadataGeneratorEngine
import com.example.engine.SubtitleGenerator
import com.example.engine.VideoClipperEngine
import com.example.engine.VideoMetadataExtractor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

class ClipperViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClipperRepository(application)
    private val TAG = "ClipperViewModel"

    // Projects & Exports
    val projects: StateFlow<List<ProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exportedClips: StateFlow<List<ClipEntity>> = repository.allExportedClips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active project state
    private val _currentProject = MutableStateFlow<ProjectEntity?>(null)
    val currentProject = _currentProject.asStateFlow()

    private val _videoMetadata = MutableStateFlow<VideoMetadata?>(null)
    val videoMetadata = _videoMetadata.asStateFlow()

    private val _transcriptSegments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val transcriptSegments = _transcriptSegments.asStateFlow()

    private val _candidateClips = MutableStateFlow<List<ClipCandidate>>(emptyList())
    val candidateClips = _candidateClips.asStateFlow()

    private val _selectedClip = MutableStateFlow<ClipCandidate?>(null)
    val selectedClip = _selectedClip.asStateFlow()

    // Subtitle Style State
    private val _subtitleStyle = MutableStateFlow(SubtitleStyleConfig())
    val subtitleStyle = _subtitleStyle.asStateFlow()

    // Processing State
    data class ProcessingState(
        val isProcessing: Boolean = false,
        val stage: String = "",
        val progress: Float = 0f,
        val error: String? = null,
        val completedProjectId: Long? = null
    )

    private val _processingState = MutableStateFlow(ProcessingState())
    val processingState = _processingState.asStateFlow()

    private var activeJob: Job? = null

    // Settings
    val geminiApiKey = repository.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val defaultAspectRatio = repository.defaultAspectRatio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AspectRatio.VERTICAL_9_16)
    val defaultPreset = repository.defaultPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubtitlePreset.BOLD_YELLOW)
    val exportResolution = repository.exportResolution
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExportResolution.RES_1080P)

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes = _storageBytes.asStateFlow()

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _storageBytes.value = repository.getTempStorageSizeBytes()
        }
    }

    fun cleanTempFiles() {
        viewModelScope.launch {
            repository.cleanTempFiles()
            refreshStorage()
        }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch {
            repository.setGeminiApiKey(key)
        }
    }

    fun setDefaultAspectRatio(aspectRatio: AspectRatio) {
        viewModelScope.launch {
            repository.setDefaultAspectRatio(aspectRatio)
        }
    }

    fun setDefaultPreset(preset: SubtitlePreset) {
        viewModelScope.launch {
            repository.setDefaultPreset(preset)
            _subtitleStyle.value = _subtitleStyle.value.copy(
                preset = preset,
                textColorHex = preset.textColor,
                outlineColorHex = preset.outlineColor,
                backgroundColorHex = preset.backgroundColor,
                activeWordColorHex = preset.activeWordColor
            )
        }
    }

    fun updateSubtitleStyle(style: SubtitleStyleConfig) {
        _subtitleStyle.value = style
    }

    fun setExportResolution(resolution: ExportResolution) {
        viewModelScope.launch {
            repository.setExportResolution(resolution)
        }
    }

    // Video Import
    fun loadVideo(uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = VideoMetadataExtractor.extractMetadata(getApplication(), uri)
            if (result.isSuccess) {
                val meta = result.getOrNull()!!
                _videoMetadata.value = meta
                val pId = repository.createProject(meta)
                _currentProject.value = repository.getProjectById(pId)
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Could not open video")
            }
        }
    }

    fun selectProject(projectId: Long) {
        viewModelScope.launch {
            val project = repository.getProjectById(projectId) ?: return@launch
            _currentProject.value = project
            val meta = VideoMetadata(
                uri = project.sourceVideoUri,
                fileName = project.title + ".mp4",
                durationMs = project.durationMs,
                width = project.width,
                height = project.height,
                frameRate = project.frameRate,
                sizeBytes = project.sizeBytes
            )
            _videoMetadata.value = meta
            val segments = repository.getTranscriptSegments(projectId)
            _transcriptSegments.value = segments

            val clipEntities = repository.getClipsForProject(projectId).first()
            _candidateClips.value = clipEntities.map { entityToCandidate(it) }
        }
    }

    // AI Clipper Workflow
    fun startAutoClipping(
        projectId: Long,
        config: AutoClippingEngine.ClippingConfig
    ) {
        cancelProcessing()
        activeJob = viewModelScope.launch {
            _processingState.value = ProcessingState(
                isProcessing = true,
                stage = "Analyzing audio & dialogue...",
                progress = 0.1f
            )

            try {
                val project = repository.getProjectById(projectId)
                    ?: throw IllegalStateException("Project not found")

                val videoUri = Uri.parse(project.sourceVideoUri)
                val customKey = geminiApiKey.value

                // 1. Audio Extraction & Speech Recognition
                val speechResult = AudioSpeechEngine.transcribeVideoAudio(
                    context = getApplication(),
                    videoUri = videoUri,
                    durationMs = project.durationMs,
                    hasAudio = true,
                    customApiKey = customKey
                ) { stage, progress ->
                    _processingState.value = _processingState.value.copy(
                        stage = stage,
                        progress = progress * 0.5f
                    )
                }

                if (speechResult.isFailure) {
                    throw speechResult.exceptionOrNull()!!
                }

                val analysis = speechResult.getOrNull()!!
                _transcriptSegments.value = analysis.segments
                repository.saveTranscript(projectId, analysis.segments, analysis.fullTranscript)

                // 2. Discover Best Moments
                _processingState.value = _processingState.value.copy(
                    stage = "Detecting hooks and scoring viral moments...",
                    progress = 0.65f
                )

                val candidates = AutoClippingEngine.findBestMoments(
                    projectId = projectId,
                    totalDurationMs = project.durationMs,
                    segments = analysis.segments,
                    config = config
                )

                // 3. Generate Metadata for each candidate
                _processingState.value = _processingState.value.copy(
                    stage = "Generating YouTube Shorts titles & hashtags...",
                    progress = 0.85f
                )

                val enriched = candidates.mapIndexed { idx, clip ->
                    val transcriptForClip = clip.subtitles.joinToString(" ") { it.text }
                    val meta = MetadataGeneratorEngine.generateClipMetadata(
                        clipTitle = clip.title,
                        transcriptText = transcriptForClip,
                        tone = MetadataTone.VIRAL_HOOK,
                        customApiKey = customKey
                    )
                    clip.copy(metadata = meta)
                }

                repository.saveClips(enriched)
                _candidateClips.value = enriched

                _processingState.value = ProcessingState(
                    isProcessing = false,
                    stage = "Completed!",
                    progress = 1.0f,
                    completedProjectId = projectId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Clipping process failed", e)
                _processingState.value = ProcessingState(
                    isProcessing = false,
                    error = e.localizedMessage ?: "Processing error occurred"
                )
            }
        }
    }

    // Custom Length Workflow
    fun startCustomLengthClipping(
        projectId: Long,
        config: AutoClippingEngine.CustomDurationConfig
    ) {
        cancelProcessing()
        activeJob = viewModelScope.launch {
            _processingState.value = ProcessingState(
                isProcessing = true,
                stage = "Analyzing audio & dialogue...",
                progress = 0.1f
            )

            try {
                val project = repository.getProjectById(projectId)
                    ?: throw IllegalStateException("Project not found")

                val videoUri = Uri.parse(project.sourceVideoUri)
                val customKey = geminiApiKey.value

                var segments = repository.getTranscriptSegments(projectId)
                if (segments.isEmpty()) {
                    val speechResult = AudioSpeechEngine.transcribeVideoAudio(
                        context = getApplication(),
                        videoUri = videoUri,
                        durationMs = project.durationMs,
                        hasAudio = true,
                        customApiKey = customKey
                    ) { stage, progress ->
                        _processingState.value = _processingState.value.copy(
                            stage = stage,
                            progress = progress * 0.5f
                        )
                    }
                    if (speechResult.isFailure) throw speechResult.exceptionOrNull()!!
                    val analysis = speechResult.getOrNull()!!
                    segments = analysis.segments
                    repository.saveTranscript(projectId, segments, analysis.fullTranscript)
                }

                _processingState.value = _processingState.value.copy(
                    stage = "Formatting ${config.targetDurationSec}s clips...",
                    progress = 0.70f
                )

                val clips = AutoClippingEngine.createCustomDurationClips(
                    projectId = projectId,
                    totalDurationMs = project.durationMs,
                    segments = segments,
                    config = config
                )

                val enriched = clips.map { clip ->
                    val transcriptForClip = clip.subtitles.joinToString(" ") { it.text }
                    val meta = MetadataGeneratorEngine.generateClipMetadata(
                        clipTitle = clip.title,
                        transcriptText = transcriptForClip,
                        tone = MetadataTone.VIRAL_HOOK,
                        customApiKey = customKey
                    )
                    clip.copy(metadata = meta)
                }

                repository.saveClips(enriched)
                _candidateClips.value = enriched

                _processingState.value = ProcessingState(
                    isProcessing = false,
                    stage = "Ready!",
                    progress = 1.0f,
                    completedProjectId = projectId
                )
            } catch (e: Exception) {
                _processingState.value = ProcessingState(
                    isProcessing = false,
                    error = e.localizedMessage ?: "Custom length processing failed"
                )
            }
        }
    }

    fun selectClip(clipId: String) {
        val clip = _candidateClips.value.firstOrNull { it.id == clipId }
        _selectedClip.value = clip
    }

    fun updateClipBoundaries(clipId: String, newStartMs: Long, newEndMs: Long) {
        val current = _selectedClip.value ?: return
        if (current.id != clipId) return

        val updated = current.copy(
            startMs = newStartMs,
            endMs = newEndMs,
            durationMs = newEndMs - newStartMs
        )
        _selectedClip.value = updated
        _candidateClips.value = _candidateClips.value.map { if (it.id == clipId) updated else it }

        viewModelScope.launch {
            val entity = repository.getClipById(clipId)
            if (entity != null) {
                repository.updateClip(
                    entity.copy(
                        startMs = newStartMs,
                        endMs = newEndMs,
                        durationMs = newEndMs - newStartMs
                    )
                )
            }
        }
    }

    fun regenerateMetadata(clipId: String, tone: MetadataTone) {
        val clip = _selectedClip.value ?: return
        viewModelScope.launch {
            val transcriptText = clip.subtitles.joinToString(" ") { it.text }
            val newMeta = MetadataGeneratorEngine.generateClipMetadata(
                clipTitle = clip.title,
                transcriptText = transcriptText,
                tone = tone,
                customApiKey = geminiApiKey.value
            )
            val updated = clip.copy(metadata = newMeta)
            _selectedClip.value = updated
            _candidateClips.value = _candidateClips.value.map { if (it.id == clipId) updated else it }

            val entity = repository.getClipById(clipId)
            if (entity != null) {
                val hashtagsJson = JSONArray(newMeta.hashtags).toString()
                val kwJson = JSONArray(newMeta.searchKeywords).toString()
                repository.updateClip(
                    entity.copy(
                        youtubeTitle = newMeta.youtubeTitle,
                        youtubeDescription = newMeta.youtubeDescription,
                        socialCaption = newMeta.socialCaption,
                        hashtagsJson = hashtagsJson,
                        searchKeywordsJson = kwJson,
                        thumbnailText = newMeta.thumbnailText,
                        pinnedComment = newMeta.pinnedComment,
                        summary = newMeta.summary
                    )
                )
            }
        }
    }

    // Export Clip
    fun exportClip(
        clip: ClipCandidate,
        options: VideoClipperEngine.ExportOptions,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val project = _currentProject.value ?: return@launch
            val videoUri = Uri.parse(project.sourceVideoUri)

            val clipResult = VideoClipperEngine.clipVideo(
                context = getApplication(),
                sourceUri = videoUri,
                startMs = clip.startMs,
                endMs = clip.endMs,
                clipTitle = clip.title,
                options = options,
                onProgress = onProgress
            )

            if (clipResult.isSuccess) {
                val file = clipResult.getOrNull()!!
                val galleryResult = VideoClipperEngine.saveToGallery(
                    context = getApplication(),
                    renderedFile = file,
                    displayName = clip.title
                )

                val entity = repository.getClipById(clip.id)
                if (entity != null) {
                    repository.updateClip(
                        entity.copy(
                            status = ClipStatus.COMPLETED.name,
                            outputPath = file.absolutePath
                        )
                    )
                }

                refreshStorage()
                onComplete(true, "Saved to Gallery (Movies/FeviClipper)")
            } else {
                onComplete(false, clipResult.exceptionOrNull()?.message ?: "Export failed")
            }
        }
    }

    fun exportSrt(clip: ClipCandidate, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val srt = clip.srtContent ?: SubtitleGenerator.toSrt(clip.subtitles)
            val result = SubtitleGenerator.exportSrtFile(getApplication(), clip.title, srt)
            if (result.isSuccess) {
                onComplete(true, "SRT saved to Documents: ${result.getOrNull()?.name}")
            } else {
                onComplete(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun deleteClip(clipId: String) {
        viewModelScope.launch {
            repository.deleteClip(clipId)
            _candidateClips.value = _candidateClips.value.filter { it.id != clipId }
            if (_selectedClip.value?.id == clipId) {
                _selectedClip.value = null
            }
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            if (_currentProject.value?.id == projectId) {
                _currentProject.value = null
                _candidateClips.value = emptyList()
                _selectedClip.value = null
            }
        }
    }

    fun cancelProcessing() {
        activeJob?.cancel()
        activeJob = null
        _processingState.value = ProcessingState(isProcessing = false)
    }

    private fun entityToCandidate(entity: ClipEntity): ClipCandidate {
        val hashtags = mutableListOf<String>()
        val kw = mutableListOf<String>()
        try {
            val hArr = JSONArray(entity.hashtagsJson)
            for (i in 0 until hArr.length()) hashtags.add(hArr.getString(i))
            val kArr = JSONArray(entity.searchKeywordsJson)
            for (i in 0 until kArr.length()) kw.add(kArr.getString(i))
        } catch (ignored: Exception) {}

        val meta = ClipMetadata(
            youtubeTitle = entity.youtubeTitle,
            youtubeDescription = entity.youtubeDescription,
            socialCaption = entity.socialCaption,
            hashtags = hashtags,
            searchKeywords = kw,
            thumbnailText = entity.thumbnailText,
            pinnedComment = entity.pinnedComment,
            summary = entity.summary
        )

        val aspect = try {
            AspectRatio.valueOf(entity.aspectRatio)
        } catch (e: Exception) {
            AspectRatio.VERTICAL_9_16
        }

        val status = try {
            ClipStatus.valueOf(entity.status)
        } catch (e: Exception) {
            ClipStatus.DISCOVERED
        }

        return ClipCandidate(
            id = entity.id,
            projectId = entity.projectId,
            title = entity.title,
            startMs = entity.startMs,
            endMs = entity.endMs,
            durationMs = entity.durationMs,
            reasonWhySelected = entity.reasonWhySelected,
            score = entity.score,
            aspectRatio = aspect,
            status = status,
            outputPath = entity.outputPath,
            metadata = meta,
            srtContent = entity.srtContent
        )
    }
}
