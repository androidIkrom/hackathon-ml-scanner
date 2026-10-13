package com.nungil.scan

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.nungil.contract.Compute
import com.nungil.contract.ModelChoice
import com.nungil.core.scan.DetectorPlan
import com.nungil.core.scan.ModelShelf
import com.nungil.items.ItemEmbedder
import com.nungil.items.ItemSegmenter
import com.nungil.people.FaceEmbedder

/**
 * The app's models, one loaded instance per kind (stage 4a, spec 2026-10-13-shared-models-design). A screen takes a
 * lease and closes the lease when it is done; the model itself stays loaded [ModelShelf.KEEP_MS] for the next screen,
 * then is closed on a thread of its own. Every model lets one call in at a time.
 */
object SharedModels {
    private val shelf = ModelShelf({ SystemClock.elapsedRealtime() })
    private val sweeper: Handler by lazy { Handler(HandlerThread("model sweeper").apply { start() }.looper) }
    private val sweep = Runnable {
        shelf.sweep()
        if (shelf.loaded > 0) sweepLater()
    }

    private fun sweepLater() {
        sweeper.removeCallbacks(sweep)
        sweeper.postDelayed(sweep, SWEEP_EVERY_MS)
    }

    private fun <M : java.io.Closeable> take(kind: String, key: String, build: () -> M): ModelShelf.Lease<M> {
        val lease = shelf.take(kind, key, build)
        sweepLater()
        return lease
    }

    /** How often models kept for the next screen are looked at; one is closed 30-35 s after its last screen left. */
    private const val SWEEP_EVERY_MS = 5_000L

    /**
     * The detector of [modelFile]; YOLO26 is exported here (tools/yolo26/export.py), not downloaded, so a build without
     * it uses EfficientDet-Lite2 instead.
     */
    fun detector(context: Context, modelFile: String, compute: Compute): ModelShelf.Lease<SharedDetector> {
        val file = if (DetectorPlanFiles.isYolo(modelFile) && !hasAsset(context, modelFile)) {
            Log.i("Nungil", "$modelFile is not in this build: EfficientDet-Lite2 instead (tools/yolo26/export.py)")
            DetectorPlan.modelFile(ModelChoice.ACCURATE)
        } else {
            modelFile
        }
        return take("detector", "$file $compute") { SharedDetector.create(context.applicationContext, file, compute) }
    }

    private fun hasAsset(context: Context, file: String): Boolean =
        runCatching { context.assets.openFd(file).close() }.isSuccess

    fun classifier(context: Context) = take("classifier", "") { SceneClassifier(context.applicationContext) }

    fun itemEmbedder(context: Context) = take("item embedder", "") { ItemEmbedder(context.applicationContext) }

    fun itemSegmenter(context: Context) = take("item segmenter", "") { ItemSegmenter(context.applicationContext) }

    fun faceEmbedder(context: Context) = take("face embedder", "") { FaceEmbedder(context.applicationContext) }
}
