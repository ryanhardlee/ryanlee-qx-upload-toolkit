package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.native.PatchEngine
import java.io.File

object FpsMagicEngine {
    fun applyFpsMagicPatch(context: Context, inputUri: Uri, outputFile: File, multiplier: Int): Boolean {
        return PatchEngine.applyFpsMagicPatch(context, inputUri, outputFile, multiplier)
    }
}
