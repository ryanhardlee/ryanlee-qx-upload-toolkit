package com.example

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TestLog
import com.example.data.TestLogRepository
import com.example.native.PatchEngine
import com.example.utils.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val repository = TestLogRepository(database.testLogDao())

    // Language State
    private val _language = MutableStateFlow(AppLanguage.EN)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    // Screen Navigation State
    private val _currentScreen = MutableStateFlow("dashboard") // "dashboard", "tiktok_upload"
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    // Video Selection State
    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    // Video Metadata State
    private val _videoMetadata = MutableStateFlow<PatchEngine.VideoMetadata?>(null)
    val videoMetadata: StateFlow<PatchEngine.VideoMetadata?> = _videoMetadata.asStateFlow()

    // Patch Mode Selected
    private val _patchMode = MutableStateFlow("Advanced") // "Advanced", "Classic", "No Patch"
    val patchMode: StateFlow<String> = _patchMode.asStateFlow()

    // FPS Magic State
    private val _fpsMultiplier = MutableStateFlow<Int?>(null) // 2, 3, 4
    val fpsMultiplier: StateFlow<Int?> = _fpsMultiplier.asStateFlow()

    // Action/Checklist Boolean States
    private val _isPatchCompleted = MutableStateFlow(false)
    val isPatchCompleted: StateFlow<Boolean> = _isPatchCompleted.asStateFlow()

    private val _isCaptionCopied = MutableStateFlow(false)
    val isCaptionCopied: StateFlow<Boolean> = _isCaptionCopied.asStateFlow()

    private val _isTikTokOpened = MutableStateFlow(false)
    val isTikTokOpened: StateFlow<Boolean> = _isTikTokOpened.asStateFlow()

    // Test Logs from Room
    val testLogs: StateFlow<List<TestLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current Action Message/Status
    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // New states for Repair 1 (Crash-safe patching and progress tracking)
    private val _patchProgress = MutableStateFlow<String?>(null) // "Preparing...", "Reading file...", "Applying patch...", "Exporting...", "Completed", "Failed"
    val patchProgress: StateFlow<String?> = _patchProgress.asStateFlow()

    private val _patchError = MutableStateFlow<String?>(null)
    val patchError: StateFlow<String?> = _patchError.asStateFlow()

    private val _patchSuccess = MutableStateFlow<Boolean?>(null)
    val patchSuccess: StateFlow<Boolean?> = _patchSuccess.asStateFlow()

    private val _patchErrorDetails = MutableStateFlow<Map<String, String>?>(null)
    val patchErrorDetails: StateFlow<Map<String, String>?> = _patchErrorDetails.asStateFlow()

    private val _lastExportedFile = MutableStateFlow<File?>(null)
    val lastExportedFile: StateFlow<File?> = _lastExportedFile.asStateFlow()

    // QX Upload Mode Toggle State (RyanLee Method: Active/Inactive)
    private val _qxUploadMode = MutableStateFlow(true)
    val qxUploadMode: StateFlow<Boolean> = _qxUploadMode.asStateFlow()

    fun setQxUploadMode(active: Boolean) {
        _qxUploadMode.value = active
    }

    // MediaStore Export Status State (OutputManager)
    private val _gallerySaveResult = MutableStateFlow<com.example.utils.MediaStoreExportService.ExportResult?>(null)
    val gallerySaveResult: StateFlow<com.example.utils.MediaStoreExportService.ExportResult?> = _gallerySaveResult.asStateFlow()

    fun resetGallerySaveResult() {
        android.util.Log.d("MainViewModel", "resetGallerySaveResult called")
        _gallerySaveResult.value = null
    }

    fun updateGallerySaveResult(result: com.example.utils.MediaStoreExportService.ExportResult) {
        _gallerySaveResult.value = result
    }

    private fun isValidPreparedVideoUri(uri: Uri): Boolean {
        if (!uri.scheme.equals("content", ignoreCase = true)) return false

        return try {
            val resolver = getApplication<Application>().contentResolver
            val mimeType = resolver.getType(uri)
            if (mimeType != null && !mimeType.startsWith("video/")) return false
            resolver.openInputStream(uri)?.use { it.read() != -1 } == true
        } catch (e: Exception) {
            Log.w(TAG, "Prepared upload URI validation failed", e)
            false
        }
    }

    suspend fun prepareWebUploadUri(): com.example.utils.MediaStoreExportService.ExportResult =
        withContext(Dispatchers.IO) {
            val existing = _gallerySaveResult.value
            val existingUri = existing?.savedUri
            if (existing?.success == true &&
                existing.verificationResult == "PASS" &&
                existingUri != null &&
                isValidPreparedVideoUri(existingUri)
            ) {
                Log.d(TAG, "Reusing verified MediaStore URI for WebView upload")
                return@withContext existing
            }

            val file = _lastExportedFile.value
            if (file == null || !file.exists() || file.length() <= 0L) {
                val failure = com.example.utils.MediaStoreExportService.ExportResult(
                    success = false,
                    errorType = "PreparedVideoUnavailable",
                    errorMessage = "No valid prepared video is available. Patch the video first.",
                    sourcePath = file?.absolutePath.orEmpty(),
                    sourceExists = file?.exists() == true,
                    sourceSize = file?.length() ?: 0L
                )
                _gallerySaveResult.value = failure
                Log.w(TAG, failure.errorMessage ?: "Prepared video unavailable")
                return@withContext failure
            }

            val isFpsOutput = file.name.contains("FPS", ignoreCase = true)
            val result = com.example.utils.MediaStoreExportService.saveVideoToGallery(
                context = getApplication(),
                cacheFile = file,
                prefix = if (isFpsOutput) "QX_FPS" else "QX_PREP",
                modeSuffix = _patchMode.value.replace(" ", "_")
            )

            val savedUri = result.savedUri
            val verifiedResult = if (
                result.success &&
                result.verificationResult == "PASS" &&
                savedUri != null &&
                isValidPreparedVideoUri(savedUri)
            ) {
                result
            } else {
                result.copy(
                    success = false,
                    errorType = result.errorType ?: "PreparedUriValidationFailed",
                    errorMessage = result.errorMessage
                        ?: "The saved video URI could not be verified for WebView upload.",
                    verificationResult = "FAIL"
                )
            }

            _gallerySaveResult.value = verifiedResult
            if (verifiedResult.success) {
                Log.d(
                    TAG,
                    "Prepared WebView upload URI verified: ${verifiedResult.savedDisplayName}"
                )
            } else {
                Log.e(TAG, "Could not prepare WebView upload URI: ${verifiedResult.errorMessage}")
            }
            verifiedResult
        }

    // Honest Patch Verification State for Issue 3
    private val _patchVerificationData = MutableStateFlow<Map<String, String>?>(null)
    val patchVerificationData: StateFlow<Map<String, String>?> = _patchVerificationData.asStateFlow()

    fun resetPatchStatus() {
        _patchProgress.value = null
        _patchError.value = null
        _patchSuccess.value = null
        _patchErrorDetails.value = null
        _lastExportedFile.value = null
        _gallerySaveResult.value = null
        _patchVerificationData.value = null
    }

    // MD5 Helper Functions
    private fun calculateMD5(uri: android.net.Uri): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val bytes = digest.digest()
            bytes.joinToString("") { "%02x".format(it) }.take(8).uppercase() + "..."
        } catch (e: Exception) {
            "MD5_ERR"
        }
    }

    private fun calculateFileMD5(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val bytes = digest.digest()
            bytes.joinToString("") { "%02x".format(it) }.take(8).uppercase() + "..."
        } catch (e: Exception) {
            "MD5_ERR"
        }
    }

    fun saveToGallery(prefix: String, modeSuffix: String) {
        val file = _lastExportedFile.value
        if (file == null || !file.exists()) {
            _patchError.value = "Cache file not found. Please patch the video first."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = com.example.utils.MediaStoreExportService.saveVideoToGallery(
                context = getApplication(),
                cacheFile = file,
                prefix = prefix,
                modeSuffix = modeSuffix
            )
            _gallerySaveResult.value = result
            if (!result.success) {
                _patchError.value = "Failed to save to Gallery: ${result.errorMessage}"
            }
        }
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
    }

    fun selectVideo(uri: Uri?) {
        _selectedVideoUri.value = uri
        _isPatchCompleted.value = false
        _videoMetadata.value = null
        _fpsMultiplier.value = null
        if (uri == null) {
            _statusMessage.value = "Video reset"
            return
        }
        _statusMessage.value = "Video selected: analyzing specs..."
        
        // Asynchronously inspect the video
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val metadata = PatchEngine.inspectVideo(getApplication(), uri)
                _videoMetadata.value = metadata
                _statusMessage.value = "Specs analyzed: ${metadata.resolution} @ ${metadata.fps.toInt()} FPS"
                
                // Pre-select logic for FPS multiplier if input is high FPS
                if (metadata.fps >= 59.0) {
                    _fpsMultiplier.value = 2
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error inspecting video specs", e)
                _statusMessage.value = "Inspect failed: ${e.message}"
            }
        }
    }

    fun setPatchMode(mode: String) {
        _patchMode.value = mode
    }

    fun setFpsMultiplier(multiplier: Int?) {
        _fpsMultiplier.value = multiplier
    }

    fun setCaptionCopied(copied: Boolean) {
        _isCaptionCopied.value = copied
    }

    fun setTikTokOpened(opened: Boolean) {
        _isTikTokOpened.value = opened
    }

    // Process QX Patch and export the file with robust error handling and fallback
    fun processQxPatch(onExportComplete: (File) -> Unit, onExportError: (String) -> Unit) {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            viewModelScope.launch(Dispatchers.Main) {
                onExportError("No video selected")
            }
            return
        }
        val mode = _patchMode.value
        _statusMessage.value = "Applying QE Patch: $mode..."
        _patchProgress.value = "Preparing..."
        _patchError.value = null
        _patchSuccess.value = null
        _patchErrorDetails.value = null
        _lastExportedFile.value = null
        _patchVerificationData.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val metadata = _videoMetadata.value
            var currentFile: File? = null
            try {
                // Ensure output directory exists
                _patchProgress.value = "Preparing..."
                val cacheDir = getApplication<Application>().cacheDir
                cacheDir.mkdirs()
                val outputName = "QX_PREP_${System.currentTimeMillis()}_${mode.replace(" ", "_")}.mp4"
                val outputFile = File(cacheDir, outputName)
                currentFile = outputFile

                // 1. Reading File / Loading
                _patchProgress.value = "Reading file..."
                
                // Calculate original MD5
                val originalMd5 = calculateMD5(uri)
                
                // 2. Applying Patch
                _patchProgress.value = "Applying patch..."
                
                var success = false
                var errorMsg: String? = null
                var finalModeApplied = mode
                
                if (mode == "No Patch") {
                    try {
                        val resolver = getApplication<Application>().contentResolver
                        resolver.openInputStream(uri)?.use { input ->
                            outputFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        success = true
                    } catch (e: Exception) {
                        errorMsg = "Direct copy failed: ${e.message}"
                        success = false
                    }
                } else if (mode == "Advanced") {
                    var tempInputFile: File? = null
                    try {
                        _patchProgress.value = "Reading video frames..."
                        tempInputFile = File(cacheDir, "QX_INPUT_${System.currentTimeMillis()}.mp4")
                        val resolver = getApplication<Application>().contentResolver
                        resolver.openInputStream(uri)?.use { input ->
                            tempInputFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        _patchProgress.value = "Injecting sample tables..."
                        val engineResult = com.example.native.SharkStyleMp4PatchEngine.patchAdvancedSharkStyle(tempInputFile, outputFile)
                        
                        if (engineResult.success) {
                            // Validate strict acceptance criteria
                            val passVerification = outputFile.exists() &&
                                    outputFile.length() > 0 &&
                                    engineResult.outputMd5 != engineResult.inputMd5 &&
                                    engineResult.outputSize != engineResult.inputSize &&
                                    engineResult.realSampleCount > 0 &&
                                    engineResult.fakeSampleCount == engineResult.realSampleCount * 9 &&
                                    engineResult.sttsEntryCountAfter == 2 &&
                                    engineResult.stszSampleCountAfter == engineResult.realSampleCount + engineResult.fakeSampleCount &&
                                    engineResult.mdhdTimescaleAfter == 90000L &&
                                    engineResult.mdhdDurationAfter == 2269500L &&
                                    engineResult.stcoBoxesShifted > 0 &&
                                    engineResult.videoStcoFakeOffsetsAdded > 0 &&
                                    engineResult.patchOperationsApplied > 0

                            if (passVerification) {
                                success = true
                                val verification = mapOf(
                                    "original_name" to (metadata?.fileName ?: "Selected Video"),
                                    "original_size" to "${String.format("%.2f", (metadata?.fileSize ?: 0L).toDouble() / 1024.0 / 1024.0)} MB",
                                    "output_name" to outputFile.name,
                                    "output_size" to "${String.format("%.2f", outputFile.length().toDouble() / 1024.0 / 1024.0)} MB",
                                    "original_hash" to engineResult.inputMd5,
                                    "output_hash" to engineResult.outputMd5,
                                    "patch_mode" to "Advanced (Shark Engine)",
                                    "engine_path" to "com.example.native.SharkStyleMp4PatchEngine",
                                    "real_samples" to "${engineResult.realSampleCount}",
                                    "fake_samples" to "${engineResult.fakeSampleCount}",
                                    "stsz_before_after" to "${engineResult.stszSampleCountBefore} -> ${engineResult.stszSampleCountAfter}",
                                    "stts_before_after" to "${engineResult.sttsEntryCountBefore} -> ${engineResult.sttsEntryCountAfter}",
                                    "mdhd_timescale_before_after" to "${engineResult.mdhdTimescaleBefore} -> ${engineResult.mdhdTimescaleAfter}",
                                    "mdhd_duration_before_after" to "${engineResult.mdhdDurationBefore} -> ${engineResult.mdhdDurationAfter}",
                                    "elst_mediatime_before_after" to "${engineResult.elstMediaTimeBefore} -> ${engineResult.elstMediaTimeAfter}",
                                    "stco_boxes_shifted" to "${engineResult.stcoBoxesShifted}",
                                    "fake_offsets_added" to "${engineResult.videoStcoFakeOffsetsAdded}",
                                    "ops_applied" to "${engineResult.patchOperationsApplied} (mdhd, elst, stts, stsz, stsc, stco)",
                                    "structures_modified" to "ftyp, moov, mdat, trak, mdia, minf, stbl, edts",
                                    "verification_status" to "PASS",
                                    "no_modification" to "false"
                                )
                                _patchVerificationData.value = verification
                            } else {
                                success = false
                                errorMsg = "Advanced Patch Verification FAIL: Acceptance criteria not met.\n" +
                                        "MD5 change: ${engineResult.outputMd5 != engineResult.inputMd5}\n" +
                                        "Size change: ${engineResult.outputSize != engineResult.inputSize}\n" +
                                        "stts entries: ${engineResult.sttsEntryCountAfter}\n" +
                                        "stsz samples: ${engineResult.stszSampleCountAfter}\n" +
                                        "mdhd timescale: ${engineResult.mdhdTimescaleAfter}\n" +
                                        "stco shifted: ${engineResult.stcoBoxesShifted}"
                            }
                        } else {
                            success = false
                            errorMsg = "Advanced Patch Engine Error: ${engineResult.errorMessage ?: "Unknown error"}"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Advanced patch failed with exception", e)
                        success = false
                        errorMsg = "Advanced patch engine error: ${e.message}"
                    } finally {
                        try {
                            tempInputFile?.delete()
                        } catch (de: Exception) {}
                    }
                } else {
                    success = PatchEngine.applyClassicPatch(getApplication(), uri, outputFile)
                    if (!success) {
                        errorMsg = "Classic patch returned false."
                    }
                }

                if (success && outputFile.exists()) {
                    // 3. Exporting / Completed
                    _patchProgress.value = "Exporting..."
                    _isPatchCompleted.value = true
                    _statusMessage.value = "QE Patch complete! Exported $outputName"
                    _patchSuccess.value = true
                    _patchProgress.value = "Completed"
                    _lastExportedFile.value = outputFile

                    // Calculate output MD5
                    val outputMd5 = calculateFileMD5(outputFile)

                    if (_patchVerificationData.value == null) {
                        // Generate Honest Patch Verification data for Classic or No Patch
                        val verification = mapOf(
                            "original_name" to (metadata?.fileName ?: "Selected Video"),
                            "original_size" to "${String.format("%.2f", (metadata?.fileSize ?: 0L).toDouble() / 1024.0 / 1024.0)} MB",
                            "output_name" to outputFile.name,
                            "output_size" to "${String.format("%.2f", outputFile.length().toDouble() / 1024.0 / 1024.0)} MB",
                            "original_hash" to originalMd5,
                            "output_hash" to outputMd5,
                            "patch_mode" to finalModeApplied,
                            "engine_path" to when (finalModeApplied) {
                                "Classic" -> "com.example.native.PatchEngine.applyClassicPatch"
                                "No Patch" -> "com.example.native.PatchEngine.directCopy"
                                else -> "com.example.native.PatchEngine.applyClassicPatch (Fallback)"
                            },
                            "ops_applied" to when (finalModeApplied) {
                                "Classic" -> "1 (Matrix set b=1 coefficient)"
                                "No Patch" -> "0 (File streamed to cache directly)"
                                else -> "1 (Matrix coefficient set b=1)"
                            },
                            "structures_modified" to when (finalModeApplied) {
                                "Classic" -> "mvhd (matrix coefficient b)"
                                "No Patch" -> "None"
                                else -> "mvhd (matrix coefficient)"
                            },
                            "sample_table_modified" to "No",
                            "no_modification" to if (finalModeApplied == "No Patch") "true" else "false"
                        )
                        _patchVerificationData.value = verification
                    }

                    // Automatically save a local Test Log record!
                    saveLogEntry(
                        sourceFileName = metadata?.fileName ?: "Selected Video",
                        patchMode = finalModeApplied,
                        fpsMode = _fpsMultiplier.value?.let { "x$it" } ?: "Normal",
                        exportResult = "Success: ${String.format("%.2f", outputFile.length().toDouble() / 1024.0 / 1024.0)}MB"
                    )

                    withContext(Dispatchers.Main) {
                        onExportComplete(outputFile)
                    }
                } else {
                    val finalErr = errorMsg ?: "Failed to write patched video file"
                    _patchProgress.value = "Failed"
                    _patchSuccess.value = false
                    _patchError.value = finalErr
                    
                    val details = mapOf(
                        "selected_file_uri" to uri.toString(),
                        "selected_patch_mode" to mode,
                        "output_path" to (outputFile.absolutePath ?: "N/A"),
                        "exception_type" to "PatchEngineError",
                        "exception_message" to finalErr,
                        "stack_trace_summary" to "Returned false from engine"
                    )
                    _patchErrorDetails.value = details

                    withContext(Dispatchers.Main) {
                        onExportError(finalErr)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during QE Patch export", e)
                _patchProgress.value = "Failed"
                _patchSuccess.value = false
                _patchError.value = e.message ?: "Unknown export error"

                val stackTraceStr = Log.getStackTraceString(e).take(500)
                val details = mapOf(
                    "selected_file_uri" to uri.toString(),
                    "selected_patch_mode" to mode,
                    "output_path" to (currentFile?.absolutePath ?: "N/A"),
                    "exception_type" to e.javaClass.name,
                    "exception_message" to (e.message ?: "Null exception message"),
                    "stack_trace_summary" to stackTraceStr
                )
                _patchErrorDetails.value = details

                withContext(Dispatchers.Main) {
                    onExportError(e.message ?: "Unknown export error")
                }
            }
        }
    }

    // Process Ryan FPS Magic timed timing-based timing adjustment and export
    fun processFpsMagic(onExportComplete: (File) -> Unit, onExportError: (String) -> Unit) {
        val uri = _selectedVideoUri.value
        if (uri == null) {
            viewModelScope.launch(Dispatchers.Main) {
                onExportError("No video selected")
            }
            return
        }
        val multiplier = _fpsMultiplier.value
        if (multiplier == null) {
            viewModelScope.launch(Dispatchers.Main) {
                onExportError("No timing multiplier selected")
            }
            return
        }
        _statusMessage.value = "Applying Ryan FPS Magic: x$multiplier timing shift..."
        _patchProgress.value = "Preparing..."
        _patchError.value = null
        _patchSuccess.value = null
        _patchErrorDetails.value = null
        _lastExportedFile.value = null
        _patchVerificationData.value = null

        viewModelScope.launch(Dispatchers.IO) {
            var currentFile: File? = null
            try {
                _patchProgress.value = "Preparing..."
                val cacheDir = getApplication<Application>().cacheDir
                cacheDir.mkdirs()
                val outputName = "QX_FPS_MAGIC_x${multiplier}_${System.currentTimeMillis()}.mp4"
                val outputFile = File(cacheDir, outputName)
                currentFile = outputFile

                _patchProgress.value = "Reading file..."
                val originalMd5 = calculateMD5(uri)

                _patchProgress.value = "Applying patch..."
                val success = PatchEngine.applyFpsMagicPatch(getApplication(), uri, outputFile, multiplier)

                if (success && outputFile.exists()) {
                    _patchProgress.value = "Exporting..."
                    _isPatchCompleted.value = true
                    _statusMessage.value = "FPS Magic timing adjustment applied successfully!"
                    _patchSuccess.value = true
                    _patchProgress.value = "Completed"
                    _lastExportedFile.value = outputFile

                    val outputMd5 = calculateFileMD5(outputFile)
                    val metadata = _videoMetadata.value

                    // Generate Honest FPS Magic Verification data
                    val verification = mapOf(
                        "original_name" to (metadata?.fileName ?: "Selected Video"),
                        "original_size" to "${String.format("%.2f", (metadata?.fileSize ?: 0L).toDouble() / 1024.0 / 1024.0)} MB",
                        "output_name" to outputFile.name,
                        "output_size" to "${String.format("%.2f", outputFile.length().toDouble() / 1024.0 / 1024.0)} MB",
                        "original_hash" to originalMd5,
                        "output_hash" to outputMd5,
                        "patch_mode" to "Ryan FPS Magic (x$multiplier Timing shift)",
                        "engine_path" to "com.example.native.PatchEngine.applyFpsMagicPatch",
                        "ops_applied" to "Modified timing delta intervals inside sample tables by a factor of x$multiplier",
                        "structures_modified" to "trak -> mdia -> minf -> stbl -> stts (time-to-sample atom)",
                        "sample_table_modified" to "Yes (stts deltas adjusted for x$multiplier frame rate emulation)",
                        "no_modification" to "false"
                    )
                    _patchVerificationData.value = verification

                    // Automatically save a local Test Log record!
                    saveLogEntry(
                        sourceFileName = metadata?.fileName ?: "Selected Video",
                        patchMode = "FPS Magic timed shift",
                        fpsMode = "x$multiplier",
                        exportResult = "Success: ${String.format("%.2f", outputFile.length().toDouble() / 1024.0 / 1024.0)} MB"
                    )

                    withContext(Dispatchers.Main) {
                        onExportComplete(outputFile)
                    }
                } else {
                    val finalErr = "FPS Magic timed patch failed to save"
                    _patchProgress.value = "Failed"
                    _patchSuccess.value = false
                    _patchError.value = finalErr

                    val details = mapOf(
                        "selected_file_uri" to uri.toString(),
                        "selected_patch_mode" to "FPS Magic timed shift",
                        "output_path" to (outputFile.absolutePath ?: "N/A"),
                        "exception_type" to "FpsMagicEngineError",
                        "exception_message" to finalErr,
                        "stack_trace_summary" to "Returned false from engine"
                    )
                    _patchErrorDetails.value = details

                    withContext(Dispatchers.Main) {
                        onExportError(finalErr)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during FPS Magic timed patch", e)
                _patchProgress.value = "Failed"
                _patchSuccess.value = false
                _patchError.value = e.message ?: "Unknown FPS Magic error"

                val stackTraceStr = Log.getStackTraceString(e).take(500)
                val details = mapOf(
                    "selected_file_uri" to uri.toString(),
                    "selected_patch_mode" to "FPS Magic timed shift",
                    "output_path" to (currentFile?.absolutePath ?: "N/A"),
                    "exception_type" to e.javaClass.name,
                    "exception_message" to (e.message ?: "Null exception message"),
                    "stack_trace_summary" to stackTraceStr
                )
                _patchErrorDetails.value = details

                withContext(Dispatchers.Main) {
                    onExportError(e.message ?: "Unknown FPS Magic error")
                }
            }
        }
    }

    // Save test log to Room
    fun saveLogEntry(
        sourceFileName: String,
        patchMode: String,
        fpsMode: String,
        exportResult: String,
        userNotes: String = "",
        qualityResult: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val log = TestLog(
                sourceFileName = sourceFileName,
                patchMode = patchMode,
                fpsMode = fpsMode,
                exportResult = exportResult,
                userNotes = userNotes,
                qualityResult = qualityResult
            )
            repository.insert(log)
            Log.d(TAG, "Saved test log: $log")
        }
    }

    // Update log entry (for manual quality and notes updates)
    fun updateLogEntry(log: TestLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.update(log)
            Log.d(TAG, "Updated test log: $log")
        }
    }

    // Delete a log entry
    fun deleteLog(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteById(id)
        }
    }
}
