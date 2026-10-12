package com.nungil.bench

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.nungil.contract.Compute
import com.nungil.contract.ScanSettings
import com.nungil.core.people.FaceQuality
import com.nungil.core.scan.DetectionFilter
import com.nungil.people.FaceEmbedder
import com.nungil.people.FaceFinder
import com.nungil.scan.DetectorFactory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min

/**
 * Measures the app's own detector and face pipeline on the real phone, over public datasets pushed to
 * `Android/data/com.nungil/files/bench/` (see `bench/prepare.py`). Nothing is asserted: every raw result goes
 * to `bench/out/` and `bench/analyze.py` turns it into the numbers. Run it with `am instrument`, never with
 * `connectedAndroidTest`, which uninstalls the app and with it the saved people and the dataset.
 */
@RunWith(AndroidJUnit4::class)
class DeviceBench {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.getExternalFilesDir(null), "bench")
    private val out = File(root, "out").apply { mkdirs() }
    private var screen: Activity? = null

    /** The run needs a visible activity: a background process is frozen by the phone's power manager. */
    @Before
    fun stayInFront() {
        val intent = Intent(context, BenchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        screen = InstrumentationRegistry.getInstrumentation().startActivitySync(intent)
    }

    @After
    fun leave() {
        screen?.finish()
    }

    /** Every model on every delegate it can use, over the pictures in `bench/coco`. A finished configuration is not run again. */
    @Test
    fun detection() {
        val images = File(root, "coco").listFiles { file -> file.extension == "jpg" }?.sortedBy { it.name }.orEmpty()
        assumeTrue("no images in ${root.path}/coco", images.isNotEmpty())
        for ((modelFile, compute) in CONFIGS) {
            val done = File(out, "det-${modelFile.substringBeforeLast('.')}-$compute.jsonl")
            if (done.exists()) continue
            val built = DetectorFactory(context).create(modelFile, compute)
            val partial = File(out, done.name + ".part")
            partial.bufferedWriter().use { writer ->
                writer.appendLine(header().put("model", modelFile).put("asked", compute.name).put("ran", built.compute.name).toString())
                for ((index, file) in images.withIndex()) {
                    if (index % PROGRESS_EVERY == 0) Log.i(TAG, "Bench $modelFile on ${built.compute}: $index of ${images.size}")
                    val bitmap = BitmapFactory.decodeFile(file.path) ?: continue
                    val image = BitmapImageBuilder(bitmap).build()
                    val started = SystemClock.elapsedRealtimeNanos()
                    val result = built.detector.detect(image)
                    val ms = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
                    val detections = JSONArray()
                    for (detection in result.detections()) {
                        val category = detection.categories().firstOrNull() ?: continue
                        val label = category.categoryName() ?: continue
                        val box = detection.boundingBox()
                        detections.put(
                            JSONObject()
                                .put("label", label)
                                .put("score", category.score().toDouble())
                                .put("kept", category.score() >= DetectionFilter.minScoreFor(label, ScanSettings.DEFAULT_MIN_SCORE))
                                .put("box", JSONArray(listOf(box.left, box.top, box.width(), box.height()))),
                        )
                    }
                    writer.appendLine(JSONObject().put("image", file.nameWithoutExtension).put("ms", ms).put("dets", detections).toString())
                    writer.flush()
                    bitmap.recycle()
                }
            }
            built.detector.close()
            partial.renameTo(done)
        }
    }

    /**
     * The face path the app uses (ML Kit finder, quality gate, levelled crop, FaceNet twice) over
     * the pictures in `bench/lfw/<person>`. Writes one line per image and the embeddings as little-endian floats;
     * matching is replayed on the laptop with the rule from `FaceMatcher`.
     */
    @Test
    fun faces() {
        val people = File(root, "lfw").listFiles { file -> file.isDirectory }?.sortedBy { it.name }.orEmpty()
        assumeTrue("no people in ${root.path}/lfw", people.isNotEmpty())
        val meta = File(out, "faces.jsonl")
        if (meta.exists()) return
        val finder = FaceFinder()
        val embedder = FaceEmbedder(context)
        val partial = File(out, meta.name + ".part")
        var row = 0
        FileOutputStream(File(out, "faces.f32")).buffered().use { vectors ->
            partial.bufferedWriter().use { writer ->
                writer.appendLine(header().toString())
                for (person in people) {
                    for (file in person.listFiles { file -> file.extension == "jpg" }?.sortedBy { it.name }.orEmpty()) {
                        val bitmap = BitmapFactory.decodeFile(file.path) ?: continue
                        val line = JSONObject().put("person", person.name).put("file", file.name)
                        val findStarted = SystemClock.elapsedRealtimeNanos()
                        val faces = finder.find(bitmap)
                        line.put("findMs", (SystemClock.elapsedRealtimeNanos() - findStarted) / 1e6).put("faces", faces.size)
                        // The labelled person is the face in the middle of an LFW picture; others are bystanders.
                        val face = faces.minByOrNull {
                            abs(it.boundingBox.exactCenterX() - bitmap.width / 2f) + abs(it.boundingBox.exactCenterY() - bitmap.height / 2f)
                        }
                        if (face != null) {
                            val box = face.boundingBox
                            val usable = FaceQuality.usable(min(box.width(), box.height()), face.headEulerAngleY, face.headEulerAngleX)
                            line.put("usable", usable)
                            val embedStarted = SystemClock.elapsedRealtimeNanos()
                            val vector = if (usable) embedder.embed(bitmap, face) else null
                            if (vector != null) {
                                line.put("embedMs", (SystemClock.elapsedRealtimeNanos() - embedStarted) / 1e6).put("row", row++)
                                val bytes = ByteBuffer.allocate(4 * vector.size).order(ByteOrder.LITTLE_ENDIAN)
                                bytes.asFloatBuffer().put(vector)
                                vectors.write(bytes.array())
                            }
                        }
                        writer.appendLine(line.toString())
                        bitmap.recycle()
                    }
                }
            }
        }
        finder.close()
        embedder.close()
        partial.renameTo(meta)
    }

    private fun header(): JSONObject = JSONObject()
        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        .put("soc", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE)
        .put("android", Build.VERSION.RELEASE)

    private companion object {
        const val TAG = "Nungil"
        const val PROGRESS_EVERY = 100
        val CONFIGS = listOf(
            "efficientdet-lite2.tflite" to Compute.GPU,
            "efficientdet-lite2.tflite" to Compute.CPU,
            "efficientdet-lite0.tflite" to Compute.GPU,
            "efficientdet-lite0.tflite" to Compute.CPU,
            "ssd-mobilenet-v2.tflite" to Compute.GPU,
            "ssd-mobilenet-v2.tflite" to Compute.CPU,
            "efficientdet-lite0-int8.tflite" to Compute.CPU,
        )
    }
}
