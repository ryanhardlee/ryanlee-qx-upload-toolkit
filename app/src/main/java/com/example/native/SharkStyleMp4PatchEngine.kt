package com.example.native

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

object SharkStyleMp4PatchEngine {
    private const val TAG = "SharkStylePatchEngine"

    data class PatchResult(
        val success: Boolean,
        val inputPath: String,
        val outputPath: String,
        val inputSize: Long,
        val outputSize: Long,
        val inputMd5: String,
        val outputMd5: String,
        val realSampleCount: Int,
        val fakeSampleCount: Int,
        val fakeSampleSize: Int,
        val fakeOffset: Long,
        val stcoDelta: Long,
        val mdhdTimescaleBefore: Long,
        val mdhdTimescaleAfter: Long,
        val mdhdDurationBefore: Long,
        val mdhdDurationAfter: Long,
        val elstMediaTimeBefore: Long,
        val elstMediaTimeAfter: Long,
        val sttsEntryCountBefore: Int,
        val sttsEntryCountAfter: Int,
        val stszSampleCountBefore: Int,
        val stszSampleCountAfter: Int,
        val stcoBoxesShifted: Int,
        val videoStcoFakeOffsetsAdded: Int,
        val patchOperationsApplied: Int,
        val errorClass: String?,
        val errorMessage: String?
    )

    class Mp4Node(
        val type: String,
        val headerBytes: ByteArray,
        var payload: ByteArray,
        val children: MutableList<Mp4Node> = mutableListOf(),
        var offset: Int = 0
    ) {
        val size: Long
            get() {
                if (children.isNotEmpty()) {
                    var totalPayloadSize = 0L
                    for (child in children) {
                        totalPayloadSize += child.totalSize
                    }
                    return headerBytes.size + totalPayloadSize
                } else {
                    return headerBytes.size + payload.size.toLong()
                }
            }

        val totalSize: Long
            get() = size

        fun serialize(): ByteArray {
            val out = ByteArrayOutputStream()
            val computedSize = size
            val updatedHeader = headerBytes.clone()
            if (updatedHeader.size == 8) {
                ByteBuffer.wrap(updatedHeader).order(ByteOrder.BIG_ENDIAN).putInt(0, computedSize.toInt())
            } else if (updatedHeader.size == 16) {
                ByteBuffer.wrap(updatedHeader).order(ByteOrder.BIG_ENDIAN).putInt(0, 1)
                ByteBuffer.wrap(updatedHeader).order(ByteOrder.BIG_ENDIAN).putLong(8, computedSize)
            }
            out.write(updatedHeader)
            if (children.isNotEmpty()) {
                for (child in children) {
                    out.write(child.serialize())
                }
            } else {
                out.write(payload)
            }
            return out.toByteArray()
        }
    }

    private val CONTAINER_BOX_TYPES = setOf("moov", "trak", "mdia", "minf", "stbl", "edts")

    private fun parseBoxes(data: ByteArray, start: Int, end: Int): List<Mp4Node> {
        val nodes = mutableListOf<Mp4Node>()
        var offset = start
        while (offset + 8 <= end) {
            val sizeInt = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            val type = String(data, offset + 4, 4, Charsets.US_ASCII)

            var boxSize = sizeInt.toLong() and 0xFFFFFFFFL
            var headerSize = 8
            if (sizeInt == 1) {
                if (offset + 16 > end) break
                boxSize = ByteBuffer.wrap(data, offset + 8, 8).order(ByteOrder.BIG_ENDIAN).long
                headerSize = 16
            } else if (sizeInt == 0) {
                boxSize = (end - offset).toLong()
            }

            if (boxSize < headerSize || offset + boxSize > end) {
                break
            }

            val headerBytes = data.copyOfRange(offset, offset + headerSize)
            val payloadBytes = data.copyOfRange(offset + headerSize, (offset + boxSize).toInt())

            val node = Mp4Node(type, headerBytes, payloadBytes, offset = offset)

            if (CONTAINER_BOX_TYPES.contains(type)) {
                val children = parseBoxes(payloadBytes, 0, payloadBytes.size)
                node.children.addAll(children)
            }

            nodes.add(node)
            offset += boxSize.toInt()
        }
        return nodes
    }

    private fun findNode(root: Mp4Node, type: String): Mp4Node? {
        if (root.type == type) return root
        for (child in root.children) {
            val found = findNode(child, type)
            if (found != null) return found
        }
        return null
    }

    private fun findNodes(root: Mp4Node, type: String): List<Mp4Node> {
        val result = mutableListOf<Mp4Node>()
        if (root.type == type) {
            result.add(root)
        }
        for (child in root.children) {
            result.addAll(findNodes(child, type))
        }
        return result
    }

    private fun isVideoTrack(trak: Mp4Node): Boolean {
        val hdlr = findNode(trak, "hdlr") ?: return false
        if (hdlr.payload.size >= 12) {
            val handlerType = String(hdlr.payload, 8, 4, Charsets.US_ASCII)
            return handlerType == "vide"
        }
        return false
    }

    private fun getMd5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                md.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun patchAdvancedSharkStyle(inputFile: File, outputFile: File): PatchResult {
        val inputPath = inputFile.absolutePath
        val outputPath = outputFile.absolutePath
        val inputSize = inputFile.length()
        val inputMd5 = try { getMd5(inputFile) } catch (e: Exception) { "" }

        try {
            val data = inputFile.readBytes()
            val topNodes = parseBoxes(data, 0, data.size)

            val ftypNode = topNodes.find { it.type == "ftyp" }
                ?: throw IllegalArgumentException("Required ftyp box is missing from MP4 file.")
            val moovNode = topNodes.find { it.type == "moov" }
                ?: throw IllegalArgumentException("Required moov box is missing from MP4 file.")
            val mdatNode = topNodes.find { it.type == "mdat" }
                ?: throw IllegalArgumentException("Required mdat box is missing from MP4 file.")

            // Check co64
            val co64Boxes = findNodes(moovNode, "co64")
            if (co64Boxes.isNotEmpty()) {
                throw UnsupportedOperationException("co64 MP4 not supported by this patch method yet.")
            }

            // Find video track
            val traks = findNodes(moovNode, "trak")
            val videoTrack = traks.find { isVideoTrack(it) }
                ?: throw IllegalArgumentException("Required video track (trak where mdia/hdlr is 'vide') is missing.")

            // Validate and locate required video track boxes
            val mdhd = findNode(videoTrack, "mdhd")
                ?: throw IllegalArgumentException("Required mdhd box is missing from video track.")
            val elst = findNode(videoTrack, "elst")
                ?: throw IllegalArgumentException("Required edts/elst box is missing from video track.")
            val stts = findNode(videoTrack, "stts")
                ?: throw IllegalArgumentException("Required stts box is missing from video track.")
            val stsc = findNode(videoTrack, "stsc")
                ?: throw IllegalArgumentException("Required stsc box is missing from video track.")
            val stsz = findNode(videoTrack, "stsz")
                ?: throw IllegalArgumentException("Required stsz box is missing from video track.")
            val stco = findNode(videoTrack, "stco")
                ?: throw IllegalArgumentException("Required stco box is missing from video track.")

            // 1. Parse original sample sizes from stsz
            val stszBuffer = ByteBuffer.wrap(stsz.payload).order(ByteOrder.BIG_ENDIAN)
            val defaultSize = stszBuffer.getInt(4)
            val originalSampleCount = stszBuffer.getInt(8)
            val originalSizes = mutableListOf<Int>()
            if (defaultSize != 0) {
                for (i in 0 until originalSampleCount) {
                    originalSizes.add(defaultSize)
                }
            } else {
                var offset = 12
                for (i in 0 until originalSampleCount) {
                    if (offset + 4 <= stsz.payload.size) {
                        originalSizes.add(stszBuffer.getInt(offset))
                        offset += 4
                    } else {
                        break
                    }
                }
            }

            val realSampleCount = originalSizes.size
            if (realSampleCount <= 0) {
                throw IllegalArgumentException("stsz sample count must be greater than zero.")
            }
            val fakeSampleCount = realSampleCount * 9

            // 2. Rebuild mdhd
            val mdhdPayload = mdhd.payload
            val mdhdVersion = mdhdPayload[0].toInt()
            val mdhdTimescaleBefore: Long
            val mdhdDurationBefore: Long
            if (mdhdVersion == 0) {
                val buf = ByteBuffer.wrap(mdhdPayload).order(ByteOrder.BIG_ENDIAN)
                mdhdTimescaleBefore = buf.getInt(12).toLong() and 0xFFFFFFFFL
                mdhdDurationBefore = buf.getInt(16).toLong() and 0xFFFFFFFFL
                buf.putInt(12, 90000)
                buf.putInt(16, 2269500)
            } else if (mdhdVersion == 1) {
                val buf = ByteBuffer.wrap(mdhdPayload).order(ByteOrder.BIG_ENDIAN)
                mdhdTimescaleBefore = buf.getInt(20).toLong() and 0xFFFFFFFFL
                mdhdDurationBefore = buf.getLong(24)
                buf.putInt(20, 90000)
                buf.putLong(24, 2269500L)
            } else {
                throw UnsupportedOperationException("Unsupported mdhd version: $mdhdVersion")
            }

            // 3. Rebuild elst
            val elstPayload = elst.payload
            val elstVersion = elstPayload[0].toInt()
            val elstEntryCount = ByteBuffer.wrap(elstPayload, 4, 4).order(ByteOrder.BIG_ENDIAN).int
            if (elstEntryCount < 1) {
                throw IllegalArgumentException("elst entry count must be at least 1")
            }
            val elstMediaTimeBefore: Long
            if (elstVersion == 0) {
                val buf = ByteBuffer.wrap(elstPayload).order(ByteOrder.BIG_ENDIAN)
                elstMediaTimeBefore = buf.getInt(12).toLong()
                buf.putInt(12, 0)
            } else if (elstVersion == 1) {
                val buf = ByteBuffer.wrap(elstPayload).order(ByteOrder.BIG_ENDIAN)
                elstMediaTimeBefore = buf.getLong(16)
                buf.putLong(16, 0L)
            } else {
                throw UnsupportedOperationException("Unsupported elst version: $elstVersion")
            }

            // 4. Rebuild stts
            val originalSttsEntryCount = ByteBuffer.wrap(stts.payload, 4, 4).order(ByteOrder.BIG_ENDIAN).int
            val newSttsPayload = ByteArray(16 + 8)
            val sttsBuf = ByteBuffer.wrap(newSttsPayload).order(ByteOrder.BIG_ENDIAN)
            sttsBuf.put(stts.payload.copyOfRange(0, 4))
            sttsBuf.putInt(2)
            sttsBuf.putInt(realSampleCount)
            sttsBuf.putInt(1500)
            sttsBuf.putInt(fakeSampleCount)
            sttsBuf.putInt(1500)
            stts.payload = newSttsPayload

            // 5. Rebuild stsz
            val totalSampleCount = realSampleCount + fakeSampleCount
            val newStszPayload = ByteArray(12 + totalSampleCount * 4)
            val stszBuf = ByteBuffer.wrap(newStszPayload).order(ByteOrder.BIG_ENDIAN)
            stszBuf.put(stsz.payload.copyOfRange(0, 4))
            stszBuf.putInt(0)
            stszBuf.putInt(totalSampleCount)
            for (size in originalSizes) {
                stszBuf.putInt(size)
            }
            for (i in 0 until fakeSampleCount) {
                stszBuf.putInt(8)
            }
            stsz.payload = newStszPayload

            // 6. Parse all track original offsets and prepare stco boxes
            val allStcoNodes = findNodes(moovNode, "stco")
            val originalOffsetsMap = mutableMapOf<Mp4Node, List<Long>>()
            for (node in allStcoNodes) {
                val buf = ByteBuffer.wrap(node.payload).order(ByteOrder.BIG_ENDIAN)
                val count = buf.getInt(4)
                val offsets = mutableListOf<Long>()
                var offset = 8
                for (i in 0 until count) {
                    if (offset + 4 <= node.payload.size) {
                        offsets.add(buf.getInt(offset).toLong() and 0xFFFFFFFFL)
                        offset += 4
                    } else {
                        break
                    }
                }
                originalOffsetsMap[node] = offsets
            }

            val originalVideoOffsets = originalOffsetsMap[stco]
                ?: throw IllegalArgumentException("Could not parse original offsets from video stco.")
            val originalVideoChunkCount = originalVideoOffsets.size

            // 7. Rebuild stsc
            val stscBuffer = ByteBuffer.wrap(stsc.payload).order(ByteOrder.BIG_ENDIAN)
            val stscEntryCount = stscBuffer.getInt(4)
            val stscEntries = mutableListOf<Triple<Int, Int, Int>>()
            var stscOffset = 8
            for (i in 0 until stscEntryCount) {
                if (stscOffset + 12 <= stsc.payload.size) {
                    val firstChunk = stscBuffer.getInt(stscOffset)
                    val samplesPerChunk = stscBuffer.getInt(stscOffset + 4)
                    val sampleDescriptionIndex = stscBuffer.getInt(stscOffset + 8)
                    stscEntries.add(Triple(firstChunk, samplesPerChunk, sampleDescriptionIndex))
                    stscOffset += 12
                } else {
                    break
                }
            }
            val lastSamplesPerChunk = stscEntries.lastOrNull()?.second ?: 0
            if (stscEntries.isEmpty() || lastSamplesPerChunk != 1) {
                stscEntries.add(Triple(originalVideoChunkCount + 1, 1, 1))
            }
            val newStscPayload = ByteArray(8 + stscEntries.size * 12)
            val newStscBuf = ByteBuffer.wrap(newStscPayload).order(ByteOrder.BIG_ENDIAN)
            newStscBuf.put(stsc.payload.copyOfRange(0, 4))
            newStscBuf.putInt(stscEntries.size)
            for (entry in stscEntries) {
                newStscBuf.putInt(entry.first)
                newStscBuf.putInt(entry.second)
                newStscBuf.putInt(entry.third)
            }
            stsc.payload = newStscPayload

            // Compute old mdat start and size
            val oldMdatHeaderSize = mdatNode.headerBytes.size
            val oldMdatPayloadStart = mdatNode.offset + oldMdatHeaderSize
            val oldMdatPayloadSize = mdatNode.payload.size

            // ITERATION 1: Build placeholder moov with zero offsets
            for (node in allStcoNodes) {
                val orig = originalOffsetsMap[node]!!
                val isVideo = (node == stco)
                val newOffsets = mutableListOf<Long>()
                for (o in orig) {
                    newOffsets.add(0L)
                }
                if (isVideo) {
                    for (i in 0 until fakeSampleCount) {
                        newOffsets.add(0L)
                    }
                }
                val placeholderPayload = ByteArray(8 + newOffsets.size * 4)
                val buf = ByteBuffer.wrap(placeholderPayload).order(ByteOrder.BIG_ENDIAN)
                buf.put(node.payload.copyOfRange(0, 4))
                buf.putInt(newOffsets.size)
                for (offsetVal in newOffsets) {
                    buf.putInt(offsetVal.toInt())
                }
                node.payload = placeholderPayload
            }

            // Calculate placeholder moov size
            val placeholderMoovSize = moovNode.totalSize

            // Other top-level nodes sizes
            var otherTopNodesSize = 0L
            for (node in topNodes) {
                if (node.type != "ftyp" && node.type != "moov" && node.type != "mdat") {
                    otherTopNodesSize += node.totalSize
                }
            }

            // Calculate actual stcoDelta and fakeSampleOffset based on placeholder moov size
            var currentMoovSize = placeholderMoovSize
            var newMdatOffset = ftypNode.totalSize + currentMoovSize + otherTopNodesSize
            var newMdatPayloadStart = newMdatOffset + 8 // rebuilt mdat header size is always 8 (32-bit size)
            var stcoDelta = newMdatPayloadStart - oldMdatPayloadStart
            var fakeSampleOffset = newMdatPayloadStart + oldMdatPayloadSize

            // ITERATION 2: Rebuild with actual offsets and check again
            for (node in allStcoNodes) {
                val orig = originalOffsetsMap[node]!!
                val isVideo = (node == stco)
                val newOffsets = mutableListOf<Long>()
                for (o in orig) {
                    newOffsets.add(o + stcoDelta)
                }
                if (isVideo) {
                    for (i in 0 until fakeSampleCount) {
                        newOffsets.add(fakeSampleOffset)
                    }
                }
                val actualPayload = ByteArray(8 + newOffsets.size * 4)
                val buf = ByteBuffer.wrap(actualPayload).order(ByteOrder.BIG_ENDIAN)
                buf.put(node.payload.copyOfRange(0, 4))
                buf.putInt(newOffsets.size)
                for (offsetVal in newOffsets) {
                    buf.putInt(offsetVal.toInt())
                }
                node.payload = actualPayload
            }

            // Verify if moov size changed (it won't because stco size is unchanged)
            val finalMoovSize = moovNode.totalSize
            if (finalMoovSize != currentMoovSize) {
                currentMoovSize = finalMoovSize
                newMdatOffset = ftypNode.totalSize + currentMoovSize + otherTopNodesSize
                newMdatPayloadStart = newMdatOffset + 8
                stcoDelta = newMdatPayloadStart - oldMdatPayloadStart
                fakeSampleOffset = newMdatPayloadStart + oldMdatPayloadSize

                for (node in allStcoNodes) {
                    val orig = originalOffsetsMap[node]!!
                    val isVideo = (node == stco)
                    val newOffsets = mutableListOf<Long>()
                    for (o in orig) {
                        newOffsets.add(o + stcoDelta)
                    }
                    if (isVideo) {
                        for (i in 0 until fakeSampleCount) {
                            newOffsets.add(fakeSampleOffset)
                        }
                    }
                    val actualPayload = ByteArray(8 + newOffsets.size * 4)
                    val buf = ByteBuffer.wrap(actualPayload).order(ByteOrder.BIG_ENDIAN)
                    buf.put(node.payload.copyOfRange(0, 4))
                    buf.putInt(newOffsets.size)
                    for (offsetVal in newOffsets) {
                        buf.putInt(offsetVal.toInt())
                    }
                    node.payload = actualPayload
                }
            }

            // Rebuild mdat payload: preserve original, append FAKE_SAMPLE_BYTES
            val fakeBytes = byteArrayOf(0, 0, 0, 4, 0, 0, 0, 0)
            val newMdatPayloadSize = oldMdatPayloadSize + fakeBytes.size
            val mdatHeader = ByteArray(8)
            ByteBuffer.wrap(mdatHeader).order(ByteOrder.BIG_ENDIAN).putInt(0, newMdatPayloadSize + 8)
            System.arraycopy("mdat".toByteArray(Charsets.US_ASCII), 0, mdatHeader, 4, 4)

            // Write output file
            FileOutputStream(outputFile).use { fos ->
                fos.write(ftypNode.serialize())
                fos.write(moovNode.serialize())
                for (node in topNodes) {
                    if (node.type != "ftyp" && node.type != "moov" && node.type != "mdat") {
                        fos.write(node.serialize())
                    }
                }
                fos.write(mdatHeader)
                fos.write(mdatNode.payload)
                fos.write(fakeBytes)
            }

            val outputSize = outputFile.length()
            val outputMd5 = try { getMd5(outputFile) } catch (e: Exception) { "" }

            return PatchResult(
                success = true,
                inputPath = inputPath,
                outputPath = outputPath,
                inputSize = inputSize,
                outputSize = outputSize,
                inputMd5 = inputMd5,
                outputMd5 = outputMd5,
                realSampleCount = realSampleCount,
                fakeSampleCount = fakeSampleCount,
                fakeSampleSize = 8,
                fakeOffset = fakeSampleOffset,
                stcoDelta = stcoDelta,
                mdhdTimescaleBefore = mdhdTimescaleBefore,
                mdhdTimescaleAfter = 90000,
                mdhdDurationBefore = mdhdDurationBefore,
                mdhdDurationAfter = 2269500,
                elstMediaTimeBefore = elstMediaTimeBefore,
                elstMediaTimeAfter = 0,
                sttsEntryCountBefore = originalSttsEntryCount,
                sttsEntryCountAfter = 2,
                stszSampleCountBefore = realSampleCount,
                stszSampleCountAfter = totalSampleCount,
                stcoBoxesShifted = allStcoNodes.size,
                videoStcoFakeOffsetsAdded = fakeSampleCount,
                patchOperationsApplied = 6,
                errorClass = null,
                errorMessage = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error applying Advanced Shark-style MP4 patch", e)
            return PatchResult(
                success = false,
                inputPath = inputPath,
                outputPath = outputPath,
                inputSize = inputSize,
                outputSize = 0,
                inputMd5 = inputMd5,
                outputMd5 = "",
                realSampleCount = 0,
                fakeSampleCount = 0,
                fakeSampleSize = 0,
                fakeOffset = 0L,
                stcoDelta = 0L,
                mdhdTimescaleBefore = 0L,
                mdhdTimescaleAfter = 0L,
                mdhdDurationBefore = 0L,
                mdhdDurationAfter = 0L,
                elstMediaTimeBefore = 0L,
                elstMediaTimeAfter = 0L,
                sttsEntryCountBefore = 0,
                sttsEntryCountAfter = 0,
                stszSampleCountBefore = 0,
                stszSampleCountAfter = 0,
                stcoBoxesShifted = 0,
                videoStcoFakeOffsetsAdded = 0,
                patchOperationsApplied = 0,
                errorClass = e.javaClass.simpleName,
                errorMessage = e.message
            )
        }
    }
}
