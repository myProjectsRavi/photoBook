package com.photobook.app.ml

import android.content.Context
import android.graphics.Bitmap
import com.photobook.app.util.LocalDiagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.exp
import kotlin.math.ln
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import org.tensorflow.lite.support.metadata.MetadataExtractor
import org.tensorflow.lite.support.metadata.schema.ProcessUnitOptions
import org.tensorflow.lite.support.metadata.schema.ScoreCalibrationOptions
import org.tensorflow.lite.support.metadata.schema.ScoreTransformationType

data class LocalSemanticLabel(
    val label: String,
    val confidence: Float,
    val isPreparedFood: Boolean = false,
)

/**
 * Runs the pinned image-label model locally without packaging ML Kit's larger native pipeline.
 *
 * The model bytes are generated into the app asset set from the pinned ML Kit artifact at build
 * time. The standalone LiteRT runtime performs inference on-device; if that local runtime is not
 * available, this class returns no semantic labels so Archive Food fails closed.
 */
@Singleton
class LocalSemanticImageLabeler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val initializationMutex = Mutex()
    private val inferenceMutex = Mutex()
    private var initializationAttempted = false
    private var interpreter: InterpreterApi? = null
    private var modelSemantics: ModelSemantics? = null

    suspend fun labels(bitmap: Bitmap): List<LocalSemanticLabel> {
        val activeInterpreter = ensureInterpreter() ?: return emptyList()
        val semantics = modelSemantics ?: return emptyList()

        return inferenceMutex.withLock {
            try {
                val scaledBitmap = if (bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
                    null
                } else {
                    Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
                }
                try {
                    val input = bitmapToInput(scaledBitmap ?: bitmap)
                    val output = ByteArray(OUTPUT_LABEL_COUNT)
                    activeInterpreter.run(input, output)
                    semantics.decode(output)
                } finally {
                    scaledBitmap?.recycle()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                LocalDiagnostics.record(
                    context = context,
                    area = "ml-labeling",
                    message = "Local semantic image labeling failed",
                    throwable = error,
                )
                emptyList()
            } catch (error: LinkageError) {
                LocalDiagnostics.record(
                    context = context,
                    area = "ml-labeling",
                    message = "Local semantic image labeling runtime failed to link",
                    throwable = error,
                )
                emptyList()
            }
        }
    }

    private suspend fun ensureInterpreter(): InterpreterApi? {
        initializationMutex.withLock {
            if (initializationAttempted) return interpreter
            initializationAttempted = true

            try {
                val modelBuffer = loadModelBuffer()
                modelSemantics = ModelSemantics.from(modelBuffer.duplicate())
                interpreter = InterpreterApi.create(
                    modelBuffer,
                    InterpreterApi.Options()
                        .setRuntime(TfLiteRuntime.FROM_APPLICATION_ONLY)
                        .setNumThreads(1),
                )
            } catch (error: Exception) {
                if (error is CancellationException) {
                    initializationAttempted = false
                    throw error
                }
                LocalDiagnostics.record(
                    context = context,
                    area = "ml-labeling",
                    message = "Local semantic runtime is unavailable; Food archive is disabled for this pass",
                    throwable = error,
                )
                interpreter = null
                modelSemantics = null
            } catch (error: LinkageError) {
                LocalDiagnostics.record(
                    context = context,
                    area = "ml-labeling",
                    message = "Local semantic runtime failed to link; Food archive is disabled for this pass",
                    throwable = error,
                )
                interpreter = null
                modelSemantics = null
            }
            return interpreter
        }
    }

    private fun loadModelBuffer(): ByteBuffer {
        val bytes = context.assets.open(MODEL_ASSET).use { input -> input.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(bytes)
                flip()
            }
    }

    private fun bitmapToInput(bitmap: Bitmap): ByteArray {
        val input = ByteArray(INPUT_SIZE * INPUT_SIZE * CHANNEL_COUNT)
        var offset = 0
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val color = bitmap.getPixel(x, y)
                input[offset++] = ((color shr 16) and 0xff).toByte()
                input[offset++] = ((color shr 8) and 0xff).toByte()
                input[offset++] = (color and 0xff).toByte()
            }
        }
        return input
    }

    private data class ModelSemantics(
        val labelIndexes: Map<Int, ModelLabel>,
        val calibrations: Map<Int, Calibration>,
        val defaultScore: Float,
        val scoreTransformation: Byte,
        val outputScale: Float,
        val outputZeroPoint: Int,
    ) {
        fun decode(output: ByteArray): List<LocalSemanticLabel> {
            val labels = mutableMapOf<String, ScoredLabel>()
            output.forEachIndexed { index, value ->
                val modelLabel = labelIndexes[index] ?: return@forEachIndexed
                val rawScore = ((value.toInt() and 0xff) - outputZeroPoint) * outputScale
                val calibrated = calibrate(index, rawScore)
                val current = labels[modelLabel.canonical]
                if (current == null || current.confidence < calibrated) {
                    labels[modelLabel.canonical] = ScoredLabel(
                        confidence = calibrated,
                        isPreparedFood = modelLabel.isPreparedFood || current?.isPreparedFood == true,
                    )
                } else if (modelLabel.isPreparedFood && !current.isPreparedFood) {
                    labels[modelLabel.canonical] = current.copy(isPreparedFood = true)
                }
            }
            return labels.map { (label, confidence) ->
                LocalSemanticLabel(
                    label = label,
                    confidence = confidence.confidence,
                    isPreparedFood = confidence.isPreparedFood,
                )
            }
        }

        private fun calibrate(index: Int, rawScore: Float): Float {
            val calibration = calibrations[index] ?: return rawScore.coerceIn(0f, 1f)
            if (calibration.minUncalibratedScore != null &&
                rawScore < calibration.minUncalibratedScore
            ) {
                return defaultScore
            }

            val safeScore = rawScore.coerceIn(MIN_SCORE, 1f - MIN_SCORE)
            val transformed = when (scoreTransformation) {
                ScoreTransformationType.INVERSE_LOGISTIC ->
                    ln(safeScore) - ln(1f - safeScore)
                ScoreTransformationType.LOG -> ln(safeScore)
                else -> safeScore
            }
            val shifted = transformed * calibration.slope + calibration.offset
            val sigmoid = if (shifted >= 0f) {
                1f / (1f + exp(-shifted))
            } else {
                val exponent = exp(shifted)
                exponent / (1f + exponent)
            }
            return (calibration.scale * sigmoid).coerceIn(0f, calibration.scale)
        }

        companion object {
            fun from(modelBuffer: ByteBuffer): ModelSemantics {
                val metadata = MetadataExtractor(modelBuffer.order(ByteOrder.LITTLE_ENDIAN))
                val outputQuantization = metadata.getOutputTensorQuantizationParams(0)
                val outputMetadata = metadata.getOutputTensorMetadata(0)
                    ?: error("Bundled label model has no output metadata")
                val calibrationUnit = (0 until outputMetadata.processUnitsLength())
                    .asSequence()
                    .map { index -> outputMetadata.processUnits(index) }
                    .firstOrNull { unit ->
                        unit.optionsType() == ProcessUnitOptions.ScoreCalibrationOptions
                    }
                val calibrationOptions = calibrationUnit
                    ?.options(null) as? ScoreCalibrationOptions
                val associatedFileNames = (0 until outputMetadata.associatedFilesLength())
                    .map { index -> outputMetadata.associatedFiles(index).name() }
                val labels = associatedFileNames
                    .firstOrNull { name -> name.contains("labels-en", ignoreCase = true) }
                    ?.let { name -> readAssociatedFile(metadata, name) }
                    .orEmpty()
                    .lines()
                val calibrationLines = associatedFileNames
                    .firstOrNull { name -> name.contains("score-calibration", ignoreCase = true) }
                    ?.let { name -> readAssociatedFile(metadata, name) }
                    .orEmpty()
                    .lines()

                val labelIndexes = labels
                    .mapIndexedNotNull { index, label ->
                        LabelMapping.map(label)?.let { canonical ->
                            if (canonical == "food" ||
                                canonical in LIVE_CANONICAL_LABELS
                            ) {
                                ModelLabel(
                                    canonical = canonical,
                                    isPreparedFood = LabelMapping.isPreparedFoodLabel(label),
                                ) to index
                            } else {
                                null
                            }
                        }
                    }
                    .associate { (canonical, index) -> index to canonical }

                val calibrations = calibrationLines.mapIndexedNotNull { index, line ->
                    val values = line.split(',').mapNotNull { value -> value.toFloatOrNull() }
                    if (values.size < 3) {
                        null
                    } else {
                        index to Calibration(
                            scale = values[0],
                            slope = values[1],
                            offset = values[2],
                            minUncalibratedScore = values.getOrNull(3),
                        )
                    }
                }.toMap()

                return ModelSemantics(
                    labelIndexes = labelIndexes,
                    calibrations = calibrations,
                    defaultScore = calibrationOptions?.defaultScore() ?: 0f,
                    scoreTransformation = calibrationOptions?.scoreTransformation()
                        ?: ScoreTransformationType.IDENTITY,
                    outputScale = outputQuantization.getScale(),
                    outputZeroPoint = outputQuantization.getZeroPoint(),
                )
            }

            private fun readAssociatedFile(
                metadata: MetadataExtractor,
                name: String,
            ): String {
                val input = metadata.getAssociatedFile(name)
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                input.use {
                    while (true) {
                        val count = try {
                            it.read(buffer)
                        } catch (_: IndexOutOfBoundsException) {
                            break
                        }
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                    }
                }
                return output.toString(Charsets.UTF_8.name())
                    .trimEnd('\n', '\r')
            }

            private val LIVE_CANONICAL_LABELS = setOf(
                "animal",
                "bird",
                "people",
                "pet",
            )

            private data class ModelLabel(
                val canonical: String,
                val isPreparedFood: Boolean,
            )

            private data class ScoredLabel(
                val confidence: Float,
                val isPreparedFood: Boolean,
            )
        }
    }

    private data class Calibration(
        val scale: Float,
        val slope: Float,
        val offset: Float,
        val minUncalibratedScore: Float?,
    )

    private companion object {
        private const val MODEL_ASSET = "photobook/food_live_label_model.tflite"
        private const val INPUT_SIZE = 224
        private const val CHANNEL_COUNT = 3
        private const val OUTPUT_LABEL_COUNT = 447
        private const val MIN_SCORE = 1e-6f
    }
}
