package com.example.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainViewModel
import com.example.R
import com.example.ui.theme.*
import com.example.utils.AppLanguage
import com.example.utils.LanguageManager
import com.example.utils.TikTokUploadHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val language by viewModel.language.collectAsStateWithLifecycle()
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsStateWithLifecycle()
    val metadata by viewModel.videoMetadata.collectAsStateWithLifecycle()
    val patchMode by viewModel.patchMode.collectAsStateWithLifecycle()
    val fpsMultiplier by viewModel.fpsMultiplier.collectAsStateWithLifecycle()
    val isPatchCompleted by viewModel.isPatchCompleted.collectAsStateWithLifecycle()
    val isCaptionCopied by viewModel.isCaptionCopied.collectAsStateWithLifecycle()
    val isTikTokOpened by viewModel.isTikTokOpened.collectAsStateWithLifecycle()
    val testLogs by viewModel.testLogs.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val patchProgress by viewModel.patchProgress.collectAsStateWithLifecycle()
    val patchError by viewModel.patchError.collectAsStateWithLifecycle()
    val patchSuccess by viewModel.patchSuccess.collectAsStateWithLifecycle()
    val patchErrorDetails by viewModel.patchErrorDetails.collectAsStateWithLifecycle()
    val lastExportedFile by viewModel.lastExportedFile.collectAsStateWithLifecycle()
    val qxUploadMode by viewModel.qxUploadMode.collectAsStateWithLifecycle()
    val gallerySaveResult by viewModel.gallerySaveResult.collectAsStateWithLifecycle()
    val patchVerificationData by viewModel.patchVerificationData.collectAsStateWithLifecycle()

    var shareDebugInfo by remember { mutableStateOf<Map<String, String>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.selectVideo(uri)
    }

    fun handleQxChromeUpload() {
        val result = TikTokUploadHelper.openChromeCustomTabUpload(context)
        if (result.first) {
            viewModel.setTikTokOpened(true)
            Toast.makeText(context, "QX Chrome Upload launched: ${result.second}. When TikTok Studio opens, select your prepared video from Gallery.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Failed to launch browser: ${result.second}", Toast.LENGTH_LONG).show()
        }
    }

    fun handleTikTokUploadApp() {
        android.util.Log.d("DashboardScreen", "handleTikTokUploadApp called")
        coroutineScope.launch {
            // 1. Copy caption to clipboard
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("TikTok Caption", "Upload method by @ryanhardlee")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Caption copied: 'Upload method by @ryanhardlee'", Toast.LENGTH_LONG).show()

            // 2. First ensure the prepared MP4 is saved to MediaStore.
            var currentSaveRes = gallerySaveResult
            if (currentSaveRes == null || !currentSaveRes.success) {
                val file = lastExportedFile
                if (file == null || !file.exists()) {
                    Toast.makeText(context, "No prepared video file available to upload", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                Toast.makeText(context, "Saving video to MediaStore...", Toast.LENGTH_SHORT).show()
                val isFps = file.name.contains("FPS")
                val prefix = if (isFps) "QX_FPS" else "QX_PREP"
                val modeSuffix = patchMode.replace(" ", "_")
                
                val result = withContext(Dispatchers.IO) {
                    com.example.utils.MediaStoreExportService.saveVideoToGallery(
                        context = context,
                        cacheFile = file,
                        prefix = prefix,
                        modeSuffix = modeSuffix
                    )
                }
                viewModel.updateGallerySaveResult(result)
                currentSaveRes = result
            }

            if (currentSaveRes != null && currentSaveRes.success && currentSaveRes.savedUri != null) {
                val videoUri = currentSaveRes.savedUri
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, videoUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri("Video", videoUri)
                }

                val pm = context.packageManager
                val packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
                var launched = false
                val errorSb = StringBuilder()

                for (pkg in packages) {
                    try {
                        pm.getPackageInfo(pkg, 0)
                        val ttIntent = Intent(intent).apply {
                            setPackage(pkg)
                        }
                        context.startActivity(ttIntent)
                        launched = true
                        shareDebugInfo = mapOf(
                            "Action" to "Upload to TikTok App",
                            "Package Selected" to pkg,
                            "URI Shared" to videoUri.toString(),
                            "Result" to "SUCCESS: TikTok App opened directly."
                        )
                        break
                    } catch (e: Exception) {
                        errorSb.append("$pkg: ${e.message}; ")
                    }
                }

                if (!launched) {
                    try {
                        val chooser = Intent.createChooser(intent, "Share to TikTok")
                        context.startActivity(chooser)
                        shareDebugInfo = mapOf(
                            "Action" to "Upload to TikTok App",
                            "Package Selected" to "Chooser (Fallback)",
                            "URI Shared" to videoUri.toString(),
                            "Result" to "SUCCESS: System chooser opened. TikTok packages not detected. Details: $errorSb"
                        )
                    } catch (e: Exception) {
                        shareDebugInfo = mapOf(
                            "Action" to "Upload to TikTok App",
                            "Result" to "FAIL: ${e.message}"
                        )
                    }
                }
            } else {
                Toast.makeText(context, "Could not save video to MediaStore gallery", Toast.LENGTH_SHORT).show()
                shareDebugInfo = mapOf(
                    "Action" to "Upload to TikTok App",
                    "Result" to "FAIL: MediaStore gallery save failed before handoff.",
                    "Save Error" to (currentSaveRes?.errorMessage ?: "Unknown save error")
                )
            }
        }
    }

    fun getString(key: String): String = LanguageManager.getString(key, language)

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val alphaGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Scaffold(
        containerColor = CyberDark,
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CyberNavy,
                border = BorderStroke(1.dp, CyberLine)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (!qxUploadMode) CyberLine else if (selectedVideoUri != null) CyberGlowGreen else CyberMuted
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (!qxUploadMode) "QX MODE DISABLED" else if (selectedVideoUri != null) getString("ready") else getString("not_ready"),
                            color = if (!qxUploadMode) CyberMuted else if (selectedVideoUri != null) CyberGlowGreen else CyberMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = statusMessage,
                        color = CyberText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            // 1. Language Toggle & Brand Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(CyberNavy, RoundedCornerShape(12.dp))
                            .border(1.dp, CyberLine, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_qx_logo),
                            contentDescription = "QX Logo",
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Quality Extreme",
                            color = CyberGlowCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier
                            .background(CyberNavy, RoundedCornerShape(20.dp))
                            .border(1.dp, CyberLine, RoundedCornerShape(20.dp))
                            .padding(2.dp)
                    ) {
                        Text(
                            text = "EN",
                            color = if (language == AppLanguage.EN) CyberText else CyberMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (language == AppLanguage.EN) CyberGlowPurple else Color.Transparent)
                                .clickable { viewModel.setLanguage(AppLanguage.EN) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        Text(
                            text = "ID",
                            color = if (language == AppLanguage.ID) CyberText else CyberMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (language == AppLanguage.ID) CyberGlowPurple else Color.Transparent)
                                .clickable { viewModel.setLanguage(AppLanguage.ID) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 2. Creator Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberNavy),
                    border = BorderStroke(1.dp, CyberLine)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .border(2.dp, CyberGlowBlue, CircleShape)
                                .padding(3.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_ryan_avatar),
                                contentDescription = "RyanLee Avatar",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UPLOAD TOOLKIT BY",
                                color = CyberGlowCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "ETER-RyanLee QX",
                                color = CyberText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.graphicsLayer(alpha = alphaGlow),
                                style = LocalTextStyle.current.copy(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = CyberGlowPurple,
                                        blurRadius = 15f
                                    )
                                )
                            )
                            Text(
                                text = "@ryanhardlee",
                                color = CyberGlowCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "linktr.ee/ryanhardlee",
                                color = CyberMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://linktr.ee/ryanhardlee"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .background(CyberNavyLight, RoundedCornerShape(12.dp))
                                .border(1.dp, CyberLine, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Linktree",
                                tint = CyberGlowCyan
                            )
                        }
                    }
                }
            }

            // 2b. RyanLee Method / QX Upload Mode Toggle Card
            item {
                val qxUploadMode by viewModel.qxUploadMode.collectAsStateWithLifecycle()
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (qxUploadMode) CyberNavy else CyberNavy.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, if (qxUploadMode) CyberGlowPurple else CyberLine)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (qxUploadMode) CyberGlowPurple.copy(alpha = 0.15f) else CyberLine.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (qxUploadMode) Icons.Default.Check else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (qxUploadMode) CyberGlowPurple else CyberMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "RYANLEE METHOD",
                                    color = if (qxUploadMode) CyberGlowCyan else CyberMuted,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (qxUploadMode) "ACTIVE: QX Workflow Mode" else "INACTIVE: Manual Tools Only",
                                    color = CyberText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (qxUploadMode) "Inspect → Patch/FPS → Save → Copy Caption → Browser" else "Checklist & upload status indicators disabled",
                                    color = CyberMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Switch(
                            checked = qxUploadMode,
                            onCheckedChange = { viewModel.setQxUploadMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberText,
                                checkedTrackColor = CyberGlowPurple,
                                uncheckedThumbColor = CyberMuted,
                                uncheckedTrackColor = CyberNavyLight
                            )
                        )
                    }
                }
            }

            // 3. Compact Quick Actions Bar
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberNavy),
                    border = BorderStroke(1.dp, CyberLine)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "QUICK ACTIONS",
                            color = CyberMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { videoPickerLauncher.launch("video/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGlowPurple),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = CyberText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = getString("select_video"),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberText,
                                        maxLines = 1
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("Upload method by @ryanhardlee"))
                                    viewModel.setCaptionCopied(true)
                                    Toast.makeText(context, getString("copied"), Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGlowBlue),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = CyberText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = getString("copy_caption"),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberText,
                                        maxLines = 1
                                    )
                                }
                            }

                            Button(
                                onClick = { handleQxChromeUpload() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberNavyLight),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, CyberLine),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = CyberGlowCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "TikTok",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberGlowCyan,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. QX Video Inspector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberNavy),
                    border = BorderStroke(1.dp, CyberLine)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(CyberGlowBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = CyberGlowBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = getString("inspect_video").uppercase(),
                                    color = CyberText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "QX Video Inspector",
                                    color = CyberMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (metadata == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberDark.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .border(1.dp, CyberLine, RoundedCornerShape(12.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = CyberMuted,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = getString("inspector_desc"),
                                        color = CyberMuted,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { videoPickerLauncher.launch("video/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberNavyLight),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, CyberGlowBlue)
                                    ) {
                                        Text(text = getString("select_video"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberGlowCyan)
                                    }
                                }
                            }
                        } else {
                            val meta = metadata!!
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = getString("readiness_status"), color = CyberMuted, fontSize = 11.sp)
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                text = if (!qxUploadMode) "DISABLED" else if (meta.isReady) getString("ready").uppercase() else getString("not_ready").uppercase(),
                                                color = if (!qxUploadMode) CyberMuted else if (meta.isReady) CyberGlowGreen else CyberGlowRed,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = (if (!qxUploadMode) CyberLine else if (meta.isReady) CyberGlowGreen else CyberGlowRed).copy(alpha = 0.15f)
                                        ),
                                        border = BorderStroke(1.dp, if (!qxUploadMode) CyberLine else if (meta.isReady) CyberGlowGreen else CyberGlowRed)
                                    )
                                }

                                HorizontalDivider(color = CyberLine, thickness = 1.dp)

                                val specs = listOf(
                                    getString("file_name") to meta.fileName,
                                    getString("file_size") to "${String.format("%.2f", meta.fileSize / 1024.0 / 1024.0)} MB",
                                    getString("duration") to "${String.format("%.1f", meta.durationSeconds)}s",
                                    getString("resolution") to meta.resolution,
                                    getString("aspect_ratio") to meta.aspectRatio,
                                    getString("fps") to if (meta.fpsSource.contains("VFR")) {
                                        "${String.format("%.2f", meta.fps)} FPS (Estimated/VFR)"
                                    } else if (meta.fpsSource.contains("Sample")) {
                                        "${String.format("%.2f", meta.fps)} FPS (Estimated)"
                                    } else if (meta.fps <= 0.0 || meta.fpsSource == "Unavailable" || meta.fpsSource == "Error/Unavailable") {
                                        "Unavailable"
                                    } else {
                                        val isWhole = meta.fps % 1.0 == 0.0
                                        if (isWhole) "${meta.fps.toInt()} FPS" else "${String.format("%.3f", meta.fps)} FPS"
                                    },
                                    getString("video_codec") to meta.videoCodec,
                                    getString("audio_codec") to meta.audioCodec,
                                    getString("estimated_bitrate") to "${meta.estimatedBitrateKbps} kbps",
                                    getString("container_format") to meta.containerFormat
                                )

                                specs.chunked(2).forEach { rowSpecs ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowSpecs.forEach { spec ->
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .background(CyberDark, RoundedCornerShape(8.dp))
                                                    .border(1.dp, CyberLine, RoundedCornerShape(8.dp))
                                                    .padding(8.dp)
                                            ) {
                                                Text(text = spec.first, color = CyberMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                Text(text = spec.second, color = CyberText, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                            }
                                        }
                                        if (rowSpecs.size < 2) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                var showFpsDebug by remember { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberDark.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .border(1.dp, CyberLine.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showFpsDebug = !showFpsDebug },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ADVANCED FPS DEBUG METRICS",
                                            color = CyberGlowCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Icon(
                                            imageVector = if (showFpsDebug) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = CyberGlowCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    if (showFpsDebug) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val debugFields = listOf(
                                            "FPS Source" to meta.fpsSource,
                                            "Metadata FPS" to "${meta.metadataFps} FPS",
                                            "Calculated FPS" to "${String.format("%.4f", meta.calculatedFps)} FPS",
                                            "Sample Count Used" to "${meta.sampleCountUsed}",
                                            "Duration Used" to "${String.format("%.4f", meta.durationUsed)}s",
                                            "Confidence Level" to meta.confidenceLevel
                                        )
                                        debugFields.forEach { (label, value) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text = label, color = CyberMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                Text(text = value, color = CyberText, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // NEW Progress and Error Debug Console for Repair 1
            if (patchProgress != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberNavy),
                        border = BorderStroke(
                            1.dp,
                            when (patchSuccess) {
                                true -> CyberGlowGreen
                                false -> CyberGlowRed
                                else -> CyberGlowPurple
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (patchSuccess) {
                                                    true -> CyberGlowGreen
                                                    false -> CyberGlowRed
                                                    else -> CyberGlowPurple
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "QX PROCESSING CONSOLE",
                                        color = CyberText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                Text(
                                    text = when (patchProgress) {
                                        "Preparing..." -> "PREPARING"
                                        "Reading file..." -> "READING"
                                        "Applying patch..." -> "PATCHING"
                                        "Exporting..." -> "EXPORTING"
                                        "Completed" -> "SUCCESS"
                                        "Failed" -> "FAILED"
                                        else -> patchProgress?.uppercase() ?: "IDLE"
                                    },
                                    color = when (patchSuccess) {
                                        true -> CyberGlowGreen
                                        false -> CyberGlowRed
                                        else -> CyberGlowPurple
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Progress indicator
                            if (patchSuccess == null) {
                                val animProgress = when (patchProgress) {
                                    "Preparing..." -> 0.15f
                                    "Reading file..." -> 0.4f
                                    "Applying patch..." -> 0.7f
                                    "Exporting..." -> 0.9f
                                    else -> 0.5f
                                }
                                Column {
                                    LinearProgressIndicator(
                                        progress = { animProgress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = CyberGlowPurple,
                                        trackColor = CyberDark
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Operation in progress: $patchProgress",
                                        color = CyberMuted,
                                        fontSize = 10.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            } else if (patchSuccess == true) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "✔ STATUS: EXPORT COMPLETED",
                                        color = CyberGlowGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    // Display cached file details
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(CyberDark, RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = "Cache File: ${lastExportedFile?.name ?: "N/A"}",
                                            color = CyberText,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "Path: ${lastExportedFile?.absolutePath ?: "N/A"}",
                                            color = CyberMuted,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (lastExportedFile != null && lastExportedFile!!.exists()) {
                                            Text(
                                                text = "Size: ${String.format("%.2f", lastExportedFile!!.length() / 1024.0 / 1024.0)} MB",
                                                color = CyberMuted,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    // Display Gallery Save status if available
                                    if (gallerySaveResult != null) {
                                        val saveRes = gallerySaveResult!!
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = if (saveRes.success) CyberNavyLight else CyberNavy),
                                            border = BorderStroke(1.dp, if (saveRes.success) CyberGlowGreen.copy(alpha = 0.5f) else CyberGlowRed.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = if (saveRes.success) "⬇ SAVED TO GALLERY: VERIFIED" else "❌ GALLERY SAVE FAILED",
                                                    color = if (saveRes.success) CyberGlowGreen else CyberGlowRed,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))

                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    if (saveRes.success) {
                                                        listOf(
                                                            "Source Cache Path" to saveRes.sourcePath,
                                                            "Source Cache Exists" to saveRes.sourceExists.toString().uppercase(),
                                                            "Source Cache Size" to "${String.format("%.2f", saveRes.sourceSize.toDouble() / 1024.0 / 1024.0)} MB",
                                                            "Saved Display Name" to (saveRes.savedDisplayName ?: saveRes.filename),
                                                            "Saved Content URI" to (saveRes.savedUriString ?: "N/A"),
                                                            "Saved Query Size" to "${String.format("%.2f", (saveRes.savedSizeFromQuery ?: 0L).toDouble() / 1024.0 / 1024.0)} MB",
                                                            "Relative Path" to (saveRes.savedRelativePath ?: saveRes.folder),
                                                            "IS_PENDING Final" to (saveRes.savedIsPending?.toString() ?: "0"),
                                                            "Verification Result" to saveRes.verificationResult
                                                        ).forEach { row ->
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(text = "${row.first}:", color = CyberMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                Text(text = row.second ?: "N/A", color = if (row.first == "Verification Result") CyberGlowGreen else CyberText, fontSize = 9.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(10.dp))

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Button(
                                                                onClick = {
                                                                    try {
                                                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                            setDataAndType(saveRes.savedUri, "video/mp4")
                                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                        }
                                                                        context.startActivity(intent)
                                                                    } catch (e: Exception) {
                                                                        Toast.makeText(context, "Error opening video: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = CyberGlowCyan),
                                                                shape = RoundedCornerShape(6.dp),
                                                                modifier = Modifier.weight(1.1f)
                                                            ) {
                                                                Text("Open Saved Video", color = CyberDark, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                                            }

                                                            Button(
                                                                onClick = {
                                                                    Toast.makeText(context, "Saved video location: Movies/RyanLee QX/ on your external storage", Toast.LENGTH_LONG).show()
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = CyberNavy),
                                                                shape = RoundedCornerShape(6.dp),
                                                                modifier = Modifier.weight(0.9f),
                                                                border = BorderStroke(1.dp, CyberLine)
                                                            ) {
                                                                Text("Open Folder Info", color = CyberText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    } else {
                                                        listOf(
                                                            "Exception Class" to (saveRes.errorType ?: "NullErrorClass"),
                                                            "Exception Message" to (saveRes.errorMessage ?: "Null error message"),
                                                            "Source Path" to saveRes.sourcePath,
                                                            "Destination File" to saveRes.filename,
                                                            "Destination Folder" to saveRes.destFolder
                                                        ).forEach { row ->
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(text = "${row.first}:", color = CyberMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                Text(text = row.second ?: "N/A", color = CyberText, fontSize = 9.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
                                                            }
                                                        }

                                                        if (saveRes.stackTrace != null) {
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(text = "Stack Trace Summary:", color = CyberMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(CyberDark, RoundedCornerShape(4.dp))
                                                                    .padding(6.dp)
                                                            ) {
                                                                Text(text = saveRes.stackTrace!!, color = CyberGlowRed, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Detailed Patch Verification Table
                                    if (patchVerificationData != null) {
                                        val v = patchVerificationData!!
                                        var showVerifyConsole by remember { mutableStateOf(true) }

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = CyberDark),
                                            border = BorderStroke(1.dp, CyberLine)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(CyberGlowCyan)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "QX PATCH VERIFICATION ATOMS",
                                                            color = CyberGlowCyan,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Black,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                    Text(
                                                        text = if (showVerifyConsole) "[HIDE]" else "[SHOW]",
                                                        color = CyberMuted,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.clickable { showVerifyConsole = !showVerifyConsole }
                                                    )
                                                }

                                                if (showVerifyConsole) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    if (v["no_modification"] == "true") {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(CyberNavy, RoundedCornerShape(4.dp))
                                                                .padding(8.dp)
                                                        ) {
                                                            Text(
                                                                text = "No structural patch applied; file copied only.",
                                                                color = androidx.compose.ui.graphics.Color(0xFFFFB300),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                    }

                                                    val isAdvanced = v["patch_mode"]?.contains("Advanced") == true
                                                    val rows = if (isAdvanced) {
                                                        listOf(
                                                            "Input Size" to v["original_size"],
                                                            "Output Size" to v["output_size"],
                                                            "Input MD5" to v["original_hash"],
                                                            "Output MD5" to v["output_hash"],
                                                            "Real Samples" to v["real_samples"],
                                                            "Fake Samples" to v["fake_samples"],
                                                            "stsz Before/After" to v["stsz_before_after"],
                                                            "stts Before/After" to v["stts_before_after"],
                                                            "mdhd Timescale Before/After" to v["mdhd_timescale_before_after"],
                                                            "mdhd Duration Before/After" to v["mdhd_duration_before_after"],
                                                            "elst Media Time Before/After" to v["elst_mediatime_before_after"],
                                                            "stco Boxes Shifted" to v["stco_boxes_shifted"],
                                                            "Fake Offsets Added" to v["fake_offsets_added"],
                                                            "Result" to v["verification_status"]
                                                        )
                                                    } else {
                                                        listOf(
                                                            "Original Video" to v["original_name"],
                                                            "Original Size" to v["original_size"],
                                                            "Original MD5" to v["original_hash"],
                                                            "Prepared Video" to v["output_name"],
                                                            "Prepared Size" to v["output_size"],
                                                            "Prepared MD5" to v["output_hash"],
                                                            "Applied Engine" to v["engine_path"],
                                                            "Ops Counter" to v["ops_applied"],
                                                            "Atoms Modified" to v["structures_modified"],
                                                            "Sample Table Edit" to v["sample_table_modified"]
                                                        )
                                                    }

                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        rows.forEach { row ->
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(text = "${row.first}:", color = CyberMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                                val isResult = row.first == "Result"
                                                                val isPass = row.second == "PASS"
                                                                val valueColor = if (isResult) {
                                                                    if (isPass) CyberGlowCyan else androidx.compose.ui.graphics.Color.Red
                                                                } else {
                                                                    CyberText
                                                                }
                                                                val valueWeight = if (isResult) FontWeight.Black else FontWeight.Normal
                                                                Text(
                                                                    text = row.second ?: "N/A",
                                                                    color = valueColor,
                                                                    fontWeight = valueWeight,
                                                                    fontSize = 9.sp,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    textAlign = TextAlign.End
                                                                )
                                                            }
                                                        }
                                                        if (isAdvanced) {
                                                            Spacer(modifier = Modifier.height(10.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(CyberNavy, RoundedCornerShape(4.dp))
                                                                    .padding(8.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Patch is metadata/container-level. Visual playback may look identical. Verify by sample table, hash, and upload behavior.",
                                                                    color = CyberMuted,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Normal,
                                                                    fontFamily = FontFamily.Monospace
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Redesigned Action Buttons Row & Grid (Dynamic Flow Active)
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val isSaved = gallerySaveResult?.success == true
                                        if (isSaved) {
                                             // 5 Buttons Layout for Successful Save
                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.spacedBy(8.dp)
                                             ) {
                                                 // 1. Open Saved Video
                                                 Button(
                                                     onClick = {
                                                         try {
                                                             val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                 setDataAndType(gallerySaveResult!!.savedUri, "video/mp4")
                                                                 addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                             }
                                                             context.startActivity(intent)
                                                         } catch (e: Exception) {
                                                             Toast.makeText(context, "Error opening video: ${e.message}", Toast.LENGTH_SHORT).show()
                                                         }
                                                     },
                                                     colors = ButtonDefaults.buttonColors(containerColor = CyberGlowCyan),
                                                     shape = RoundedCornerShape(8.dp),
                                                     modifier = Modifier.weight(1f)
                                                 ) {
                                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                                         Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = CyberDark, modifier = Modifier.size(12.dp))
                                                         Spacer(modifier = Modifier.width(2.dp))
                                                         Text("Open Saved", color = CyberDark, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                                     }
                                                 }

                                                 // 2. Upload TikTok App
                                                 Button(
                                                     onClick = {
                                                         handleTikTokUploadApp() } /*
                                                         val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                         val clip = android.content.ClipData.newPlainText("TikTok Caption", "Upload method by @ryanhardlee")
                                                         clipboard.setPrimaryClip(clip)
                                                         Toast.makeText(context, "Caption copied: 'Upload method by @ryanhardlee'", Toast.LENGTH_LONG).show()

                                                         val videoUri = gallerySaveResult?.savedUri ?: if (lastExportedFile != null && lastExportedFile!!.exists()) {
                                                             try {
                                                                 androidx.core.content.FileProvider.getUriForFile(
                                                                     context,
                                                                     "${context.packageName}.fileprovider",
                                                                     lastExportedFile!!
                                                                 )
                                                             } catch (e: Exception) {
                                                                 null
                                                             }
                                                         } else null

                                                         if (videoUri != null) {
                                                             val intent = Intent(Intent.ACTION_SEND).apply {
                                                                 type = "video/mp4"
                                                                 putExtra(Intent.EXTRA_STREAM, videoUri)
                                                                 addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                 clipData = android.content.ClipData.newRawUri("Video", videoUri)
                                                             }

                                                             val pm = context.packageManager
                                                             val packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
                                                             var launched = false
                                                             val errorSb = StringBuilder()

                                                             for (pkg in packages) {
                                                                 try {
                                                                     pm.getPackageInfo(pkg, 0)
                                                                     val ttIntent = Intent(intent).apply {
                                                                         setPackage(pkg)
                                                                     }
                                                                     context.startActivity(ttIntent)
                                                                     launched = true
                                                                     shareDebugInfo = mapOf(
                                                                         "Action" to "Share to TikTok App",
                                                                         "Package Selected" to pkg,
                                                                         "URI Shared" to videoUri.toString(),
                                                                         "Result" to "SUCCESS: TikTok App opened directly."
                                                                     )
                                                                     break
                                                                 } catch (e: Exception) {
                                                                     errorSb.append("$pkg: ${e.message}; ")
                                                                 }
                                                             }

                                                             if (!launched) {
                                                                 try {
                                                                     val chooser = Intent.createChooser(intent, "Share to TikTok")
                                                                     context.startActivity(chooser)
                                                                     shareDebugInfo = mapOf(
                                                                         "Action" to "Share to TikTok App",
                                                                         "Package Selected" to "Chooser (Fallback)",
                                                                         "URI Shared" to videoUri.toString(),
                                                                         "Result" to "SUCCESS: System chooser opened. TikTok packages not detected. Details: $errorSb"
                                                                     )
                                                                 } catch (e: Exception) {
                                                                     shareDebugInfo = mapOf(
                                                                         "Action" to "Share to TikTok App",
                                                                         "Result" to "FAIL: ${e.message}"
                                                                     )
                                                                 }
                                                             }
                                                         } else {
                                                             Toast.makeText(context, "No video available to share", Toast.LENGTH_SHORT).show()
                                                         }
                                                     },
                                                     */, colors = ButtonDefaults.buttonColors(containerColor = CyberGlowPurple),
                                                     shape = RoundedCornerShape(8.dp),
                                                     modifier = Modifier.weight(1.2f)
                                                 ) {
                                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                                         Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = CyberText, modifier = Modifier.size(12.dp))
                                                         Spacer(modifier = Modifier.width(2.dp))
                                                         Text("Upload TikTok App", color = CyberText, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                                     }
                                                 }

                                                 // 3. Share Video (System share sheet)
                                                 Button(
                                                     onClick = {
                                                         val videoUri = gallerySaveResult?.savedUri ?: if (lastExportedFile != null && lastExportedFile!!.exists()) {
                                                             try {
                                                                 androidx.core.content.FileProvider.getUriForFile(
                                                                     context,
                                                                     "${context.packageName}.fileprovider",
                                                                     lastExportedFile!!
                                                                 )
                                                             } catch (e: Exception) {
                                                                 null
                                                             }
                                                         } else null

                                                         if (videoUri != null) {
                                                             try {
                                                                 val intent = Intent(Intent.ACTION_SEND).apply {
                                                                     type = "video/mp4"
                                                                     putExtra(Intent.EXTRA_STREAM, videoUri)
                                                                     addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                     clipData = android.content.ClipData.newRawUri("Video", videoUri)
                                                                 }
                                                                 context.startActivity(Intent.createChooser(intent, "Share Video"))
                                                                 shareDebugInfo = mapOf(
                                                                     "Action" to "Share Video",
                                                                     "URI Shared" to videoUri.toString(),
                                                                     "Result" to "SUCCESS: System share sheet opened."
                                                                 )
                                                             } catch (e: Exception) {
                                                                 shareDebugInfo = mapOf(
                                                                     "Action" to "Share Video",
                                                                     "Result" to "EXCEPTION: ${e.message}"
                                                                 )
                                                             }
                                                         }
                                                     },
                                                     colors = ButtonDefaults.buttonColors(containerColor = CyberGlowGreen),
                                                     shape = RoundedCornerShape(8.dp),
                                                     modifier = Modifier.weight(1f)
                                                 ) {
                                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                                         Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = CyberDark, modifier = Modifier.size(12.dp))
                                                         Spacer(modifier = Modifier.width(2.dp))
                                                         Text("Share Video", color = CyberDark, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                                     }
                                                 }
                                             }

                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.spacedBy(8.dp)
                                             ) {
                                                 // 4. Open QX TikTok Studio Browser
                                                 Button(
                                                     onClick = { handleQxChromeUpload() },
                                                     colors = ButtonDefaults.buttonColors(containerColor = CyberNavyLight),
                                                     shape = RoundedCornerShape(8.dp),
                                                     border = BorderStroke(1.dp, CyberGlowCyan.copy(alpha = 0.4f)),
                                                     modifier = Modifier.weight(1.2f)
                                                 ) {
                                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                                         Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = CyberGlowCyan, modifier = Modifier.size(12.dp))
                                                         Spacer(modifier = Modifier.width(2.dp))
                                                         Text("QX TikTok Studio Browser", color = CyberGlowCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                                     }
                                                 }

                                                 // 5. Dismiss
                                                 Button(
                                                     onClick = {
                                                         viewModel.resetPatchStatus()
                                                         shareDebugInfo = null
                                                     },
                                                     colors = ButtonDefaults.buttonColors(containerColor = CyberNavy),
                                                     shape = RoundedCornerShape(8.dp),
                                                     border = BorderStroke(1.dp, CyberLine),
                                                     modifier = Modifier.weight(0.8f)
                                                 ) {
                                                     Text("Dismiss", color = CyberText, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                                 }
                                             }
                                        } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // 1. Save to Gallery
                                            Button(
                                                onClick = {
                                                    val isFps = lastExportedFile?.name?.contains("FPS") == true
                                                    viewModel.saveToGallery(
                                                        prefix = if (isFps) "QX_FPS" else "QX_PREP",
                                                        modeSuffix = patchMode.replace(" ", "_")
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = CyberGlowCyan),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Done, contentDescription = null, tint = CyberDark, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Save Gallery", color = CyberDark, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                                }
                                            }

                                             // 1.5 Upload to TikTok App
                                             Button(
                                                 onClick = {
                                                     handleTikTokUploadApp()
                                                 },
                                                 colors = ButtonDefaults.buttonColors(containerColor = CyberGlowPurple),
                                                 shape = RoundedCornerShape(8.dp),
                                                 modifier = Modifier.weight(1.3f)
                                             ) {
                                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                                     Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = CyberText, modifier = Modifier.size(12.dp))
                                                     Spacer(modifier = Modifier.width(2.dp))
                                                     Text("Upload TikTok App", color = CyberText, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                                 }
                                             }

                                            // 2. Share Video
                                            Button(
                                                onClick = {
                                                    val authority = "${context.packageName}.fileprovider"
                                                    val exportFile = lastExportedFile
                                                    val info = mutableMapOf<String, String>()
                                                    info["Authority Used"] = authority

                                                    // Verify Provider in Manifest
                                                    try {
                                                        val pm = context.packageManager
                                                        val packageInfo = pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PROVIDERS)
                                                        val providerExists = packageInfo.providers?.any { it.authority == authority } ?: false
                                                        info["Provider in Manifest"] = providerExists.toString().uppercase()
                                                    } catch (e: Exception) {
                                                        info["Provider in Manifest"] = "VERIFY FAILED: ${e.message}"
                                                    }

                                                    try {
                                                        if (exportFile != null && exportFile.exists()) {
                                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                                context,
                                                                authority,
                                                                exportFile
                                                            )
                                                            info["Generated Content URI"] = uri.toString()

                                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "video/mp4"
                                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                // Use ClipData for robust permission delegation across platforms
                                                                clipData = android.content.ClipData.newRawUri("Video", uri)
                                                            }
                                                            context.startActivity(Intent.createChooser(intent, "Share Prepared Video"))
                                                            info["Result"] = "SUCCESS: Android share sheet opened with FLAG_GRANT_READ_URI_PERMISSION and ClipData"
                                                        } else {
                                                            info["Result"] = "FAIL: Exported file is null or does not exist"
                                                            Toast.makeText(context, "Exported file not found", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        val sw = java.io.StringWriter()
                                                        e.printStackTrace(java.io.PrintWriter(sw))
                                                        val stack = sw.toString().lines().take(3).joinToString("\n")

                                                        info["Result"] = "EXCEPTION"
                                                        info["Exception Class"] = e.javaClass.name
                                                        info["Exception Message"] = e.message ?: "Null message"
                                                        info["Stack Trace Summary"] = stack
                                                        Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                    shareDebugInfo = info
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = CyberGlowGreen),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = CyberDark, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Share Video", color = CyberDark, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // 3. Open QX TikTok Studio Browser
                                            Button(
                                                onClick = { handleQxChromeUpload() },
                                                colors = ButtonDefaults.buttonColors(containerColor = CyberGlowPurple),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1.4f)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = CyberText, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("QX Browser", color = CyberText, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                                }
                                            }

                                            // 4. Dismiss
                                            Button(
                                                onClick = {
                                                    viewModel.resetPatchStatus()
                                                    shareDebugInfo = null
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = CyberNavyLight),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(0.8f)
                                            ) {
                                                Text("Dismiss", color = CyberText, fontSize = 10.sp, fontWeight = FontWeight.Black) } } } } /*
                                            }
                                        }
                                        }
                                            }
                                        }

                                        */ // Share Diagnostics Visual Panel
                                        if (shareDebugInfo != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            val info = shareDebugInfo!!
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = CyberNavy),
                                                border = BorderStroke(1.dp, CyberGlowGreen.copy(alpha = 0.3f))
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        text = "ℹ️ SHARE DIAGNOSTICS & VERIFICATION",
                                                        color = CyberGlowGreen,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    info.forEach { (k, v) ->
                                                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                                                            Text(text = k, color = CyberMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                            Text(text = v, color = if (k == "Result" && v.startsWith("SUCCESS")) CyberGlowGreen else if (v.startsWith("FAIL") || v.startsWith("EXCEPTION")) CyberGlowRed else CyberText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column {
                                    Text(
                                        text = "❌ STATUS: EXPORT FAILED",
                                        color = CyberGlowRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = patchError ?: "An unexpected exception force-close risk was caught and handled.",
                                        color = CyberText,
                                        fontSize = 11.sp
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    var showDebugDetails by remember { mutableStateOf(false) }
                                    
                                    Button(
                                        onClick = { showDebugDetails = !showDebugDetails },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberNavyLight),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (showDebugDetails) "Hide Debug Reports" else "View Debug Reports",
                                            color = CyberGlowCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    if (showDebugDetails) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CyberDark, RoundedCornerShape(8.dp))
                                                .border(1.dp, CyberLine, RoundedCornerShape(8.dp))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val details = patchErrorDetails ?: emptyMap()
                                            
                                            // Render details
                                            val debugLines = listOf(
                                                "Selected File URI" to (details["selected_file_uri"] ?: "N/A"),
                                                "Selected Patch Mode" to (details["selected_patch_mode"] ?: "N/A"),
                                                "Output Path" to (details["output_path"] ?: "N/A"),
                                                "Exception Type" to (details["exception_type"] ?: "N/A"),
                                                "Exception Message" to (details["exception_message"] ?: "N/A")
                                            )
                                            
                                            debugLines.forEach { (label, value) ->
                                                Column {
                                                    Text(text = label.uppercase(), color = CyberGlowCyan, fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                                    androidx.compose.foundation.text.selection.SelectionContainer {
                                                        Text(text = value, color = CyberText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }
                                            }
                                            
                                            Text(text = "STACK TRACE SUMMARY", color = CyberGlowCyan, fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                            androidx.compose.foundation.text.selection.SelectionContainer {
                                                Text(
                                                    text = details["stack_trace_summary"] ?: "N/A",
                                                    color = CyberMuted,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Button(
                                        onClick = { viewModel.resetPatchStatus() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberGlowRed),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Dismiss", color = CyberText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. QX Patch
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberNavy),
                    border = BorderStroke(1.dp, CyberLine)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(CyberGlowPurple.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = CyberGlowPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = getString("patch_video").uppercase(),
                                    color = CyberText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "QX Patch Engine",
                                    color = CyberMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val modes = listOf("Advanced", "Classic", "No Patch")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            modes.forEach { mode ->
                                val isSelected = patchMode == mode
                                val desc = when (mode) {
                                    "Advanced" -> getString("advanced_desc")
                                    "Classic" -> getString("classic_desc")
                                    else -> getString("no_patch_desc")
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) CyberGlowPurple.copy(alpha = 0.1f) else CyberDark)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) CyberGlowPurple else CyberLine,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.setPatchMode(mode) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setPatchMode(mode) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = CyberGlowPurple,
                                            unselectedColor = CyberMuted
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (mode == "No Patch") getString("no_patch") else if (mode == "Classic") getString("classic") else getString("advanced"),
                                            color = if (isSelected) CyberText else CyberMuted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = desc,
                                            color = CyberMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                viewModel.processQxPatch(
                                    onExportComplete = {
                                        Toast.makeText(context, getString("patch_completed"), Toast.LENGTH_LONG).show()
                                    },
                                    onExportError = { err ->
                                        Toast.makeText(context, "${getString("error")}: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = selectedVideoUri != null,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGlowPurple),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = getString("export_btn"), fontWeight = FontWeight.Bold, color = CyberText)
                        }
                    }
                }
            }

            // 6. Ryan FPS Magic
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberNavy),
                    border = BorderStroke(1.dp, CyberLine)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(CyberGlowCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = CyberGlowCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = getString("fps_magic").uppercase(),
                                    color = CyberText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Ryan FPS Magic timing preparation",
                                    color = CyberMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberDark, RoundedCornerShape(12.dp))
                                .border(1.dp, CyberLine, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = getString("detected_fps"), color = CyberMuted, fontSize = 12.sp)
                            Text(
                                text = metadata?.let { "${it.fps.toInt()} FPS" } ?: "-",
                                color = CyberGlowCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = getString("available_modes"),
                            color = CyberMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(2, 3, 4).forEach { mode ->
                                val isSelected = fpsMultiplier == mode
                                val isLogical = metadata == null || (metadata!!.fps / mode) >= 29.9
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) CyberGlowCyan.copy(alpha = 0.15f)
                                            else if (isLogical) CyberDark
                                            else CyberDark.copy(alpha = 0.4f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) CyberGlowCyan else if (isLogical) CyberLine else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable(enabled = isLogical) {
                                            viewModel.setFpsMultiplier(if (isSelected) null else mode)
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "x$mode",
                                            color = if (isSelected) CyberText else if (isLogical) CyberMuted else CyberMuted.copy(alpha = 0.5f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (metadata != null && isLogical) {
                                            Text(
                                                text = "${(metadata!!.fps / mode).toInt()} fps",
                                                color = CyberMuted,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = getString("output_behavior") + ": " + (fpsMultiplier?.let { "Timing adjusted (x$it length stretching)" } ?: "-"),
                            color = CyberMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberGlowRed.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .border(1.dp, CyberGlowRed.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = CyberGlowRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = getString("fps_warning"),
                                color = CyberMuted,
                                fontSize = 9.5.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                viewModel.processFpsMagic(
                                    onExportComplete = {
                                        Toast.makeText(context, getString("patch_completed"), Toast.LENGTH_LONG).show()
                                    },
                                    onExportError = { err ->
                                        Toast.makeText(context, "${getString("error")}: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = selectedVideoUri != null && fpsMultiplier != null,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGlowCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Apply FPS Magic",
                                fontWeight = FontWeight.Bold,
                                color = CyberDark
                            )
                        }
                    }
                }
            }

            // 7. Caption Tool Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberNavy),
                    border = BorderStroke(1.dp, CyberLine)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(CyberGlowBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = CyberGlowBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = getString("caption_title").uppercase(),
                                    color = CyberText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Copy manual caption credit",
                                    color = CyberMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberDark, RoundedCornerShape(10.dp))
                                .border(1.dp, CyberLine, RoundedCornerShape(10.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = getString("caption_text"),
                                color = CyberGlowCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(getString("caption_text")))
                                viewModel.setCaptionCopied(true)
                                Toast.makeText(context, getString("copied"), Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isCaptionCopied) CyberGlowGreen else CyberGlowBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isCaptionCopied) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isCaptionCopied) getString("copied") else getString("not_copied"),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 8. Upload Checklist Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberNavy),
                    border = BorderStroke(1.dp, CyberLine)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(CyberGlowGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = CyberGlowGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = getString("upload_checklist").uppercase(),
                                    color = CyberText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "TikTok upload readiness checklist",
                                    color = CyberMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!qxUploadMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberDark, RoundedCornerShape(8.dp))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "RyanLee QX Upload Mode is inactive.\nToggle RyanLee Method ON to enable the upload status checklist and readiness verification flow.",
                                    color = CyberMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        } else {
                            val checklist = listOf(
                                (selectedVideoUri != null) to getString("checklist_selected"),
                                (metadata != null) to getString("checklist_inspected"),
                                (isPatchCompleted) to getString("checklist_patched"),
                                (isCaptionCopied) to getString("checklist_caption"),
                                (isTikTokOpened) to getString("checklist_tiktok")
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                checklist.forEach { item ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(CyberDark, RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (item.first) CyberGlowGreen else CyberMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = item.second,
                                            color = if (item.first) CyberText else CyberMuted,
                                            fontSize = 12.sp,
                                            fontWeight = if (item.first) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { handleQxChromeUpload() },
                            enabled = qxUploadMode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (qxUploadMode) CyberGlowGreen else CyberNavyLight,
                                disabledContainerColor = CyberNavyLight
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = getString("open_tiktok"),
                                fontWeight = FontWeight.Bold,
                                color = if (qxUploadMode) CyberDark else CyberMuted
                            )
                        }
                    }
                }
            }

            // 9. Local Test Log List and Inputs
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberNavy),
                    border = BorderStroke(1.dp, CyberLine)
                ) {
                    var showAddLogForm by remember { mutableStateOf(false) }
                    var userNotesInput by remember { mutableStateOf("") }
                    var ratingSelected by remember { mutableStateOf("Excellent") }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(CyberGlowCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = CyberGlowCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = getString("test_log_title").uppercase(),
                                        color = CyberText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Quality result tracking",
                                        color = CyberMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showAddLogForm = !showAddForm@ showAddLogForm },
                                modifier = Modifier
                                    .background(CyberNavyLight, RoundedCornerShape(10.dp))
                                    .border(1.dp, CyberLine, RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    imageVector = if (showAddLogForm) Icons.Default.KeyboardArrowUp else Icons.Default.Add,
                                    contentDescription = "Toggle Log Form",
                                    tint = CyberGlowCyan
                                )
                            }
                        }

                        AnimatedVisibility(visible = showAddLogForm) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .fillMaxWidth()
                                    .background(CyberDark, RoundedCornerShape(12.dp))
                                    .border(1.dp, CyberLine, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(text = getString("add_log").uppercase(), color = CyberGlowCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

                                Text(text = getString("log_quality"), color = CyberMuted, fontSize = 11.sp)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf("Excellent", "Good", "Compressed", "Flop").forEach { rate ->
                                        val isRatingSelected = ratingSelected == rate
                                        Text(
                                            text = rate,
                                            color = if (isRatingSelected) CyberText else CyberMuted,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isRatingSelected) CyberGlowPurple else CyberNavy)
                                                .border(1.dp, if (isRatingSelected) CyberGlowPurple else CyberLine, RoundedCornerShape(8.dp))
                                                .clickable { ratingSelected = rate }
                                                .padding(vertical = 8.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = userNotesInput,
                                    onValueChange = { userNotesInput = it },
                                    placeholder = { Text(text = "Add manual user notes...", fontSize = 11.sp, color = CyberMuted) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = CyberText,
                                        unfocusedTextColor = CyberText,
                                        focusedBorderColor = CyberGlowCyan,
                                        unfocusedBorderColor = CyberLine,
                                        focusedContainerColor = CyberNavy,
                                        unfocusedContainerColor = CyberNavy
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                                )

                                Button(
                                    onClick = {
                                        viewModel.saveLogEntry(
                                            sourceFileName = metadata?.fileName ?: "Manual Input",
                                            patchMode = patchMode,
                                            fpsMode = fpsMultiplier?.let { "x$it" } ?: "Normal",
                                            exportResult = "Success",
                                            userNotes = userNotesInput,
                                            qualityResult = ratingSelected
                                        )
                                        userNotesInput = ""
                                        showAddLogForm = false
                                        Toast.makeText(context, "Log entry saved", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberGlowCyan),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = getString("add_log"), fontWeight = FontWeight.Bold, color = CyberDark, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (testLogs.isEmpty()) {
                            Text(
                                text = getString("no_logs_yet"),
                                color = CyberMuted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                testLogs.forEach { log ->
                                    var isExpanded by remember { mutableStateOf(false) }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CyberDark)
                                            .border(1.dp, CyberLine, RoundedCornerShape(12.dp))
                                            .clickable { isExpanded = !isExpanded }
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = log.sourceFileName,
                                                    color = CyberText,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = "${getString("log_patch")}: ${log.patchMode} • ${getString("log_fps")}: ${log.fpsMode}",
                                                    color = CyberMuted,
                                                    fontSize = 10.sp
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (log.qualityResult.isNotEmpty()) {
                                                    val badgeColor = when (log.qualityResult) {
                                                        "Excellent" -> CyberGlowGreen
                                                        "Good" -> CyberGlowCyan
                                                        "Compressed" -> CyberGlowPurple
                                                        else -> CyberGlowRed
                                                    }
                                                    Text(
                                                        text = log.qualityResult,
                                                        color = badgeColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier
                                                            .background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                            .border(1.dp, badgeColor, RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                IconButton(
                                                    onClick = { viewModel.deleteLog(log.id) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = getString("delete"),
                                                        tint = CyberGlowRed,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        AnimatedVisibility(visible = isExpanded) {
                                            Column(
                                                modifier = Modifier
                                                    .padding(top = 10.dp)
                                                    .fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Divider(color = CyberLine)
                                                Text(
                                                    text = "${getString("log_result")}: ${log.exportResult}",
                                                    color = CyberText,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                if (log.userNotes.isNotEmpty()) {
                                                    Text(
                                                        text = "${getString("log_notes")}: ${log.userNotes}",
                                                        color = CyberMuted,
                                                        fontSize = 11.sp
                                                    )
                                                }

                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    listOf("Excellent", "Good", "Compressed", "Flop").forEach { rate ->
                                                        val isSel = log.qualityResult == rate
                                                        Text(
                                                            text = rate,
                                                            color = if (isSel) CyberText else CyberMuted,
                                                            fontSize = 9.sp,
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(if (isSel) CyberGlowPurple else CyberNavy)
                                                                .clickable {
                                                                    viewModel.updateLogEntry(log.copy(qualityResult = rate))
                                                                }
                                                                .padding(vertical = 4.dp),
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Quality Extreme QX",
                        color = CyberGlowPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = getString("creator_credit") + " • ETER-RyanLee QX",
                        color = CyberMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
