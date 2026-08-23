package ca.gmode.triprecorder

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Small software 3D renderer for the cockpit aperture.
 *
 * The models are real three-dimensional meshes: attitude changes rotate the mesh around its
 * longitudinal and lateral axes, while camera orbit changes which faces are visible. Keeping this
 * renderer local avoids a large game-engine dependency for five intentionally low-poly vehicles.
 */
internal class Vehicle3DRenderer {
    private data class V3(val x: Float, val y: Float, val z: Float) {
        operator fun plus(other: V3) = V3(x + other.x, y + other.y, z + other.z)
        operator fun minus(other: V3) = V3(x - other.x, y - other.y, z - other.z)
        operator fun times(value: Float) = V3(x * value, y * value, z * value)
    }

    private data class Face(val points: List<V3>, val color: Int)
    private data class Segment(val start: V3, val end: V3, val color: Int, val width: Float)
    private data class Model(
        val faces: List<Face>,
        val segments: List<Segment>,
        val scale: Float = 1f,
    )

    private data class CameraPoint(val screen: PointF, val depth: Float, val camera: V3)
    private data class ProjectedFace(val points: List<PointF>, val depth: Float, val color: Int, val light: Float)
    private data class ProjectedSegment(val start: PointF, val end: PointF, val depth: Float, val color: Int, val width: Float)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val models by lazy {
        mapOf(
            "sxs" to buildSxs(),
            "sand_rail" to buildSandRail(),
            "truck" to buildTruck(),
            "mini_jet_boat" to buildMiniJetBoat(),
            "snowmobile" to buildSnowmobile(),
        )
    }

    fun draw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        apertureRadius: Float,
        vehicleId: String,
        pitchDegrees: Float,
        rollDegrees: Float,
        cameraYawDegrees: Float,
        cameraElevationDegrees: Float,
        accentColor: Int,
    ) {
        val model = models[vehicleId] ?: models.getValue("sxs")
        val transform = Projection(
            centerX = centerX,
            centerY = centerY + apertureRadius * 0.12f,
            scale = apertureRadius * 0.52f * model.scale,
            pitchDegrees = pitchDegrees,
            rollDegrees = rollDegrees,
            cameraYawDegrees = cameraYawDegrees,
            cameraElevationDegrees = cameraElevationDegrees,
        )

        drawShadow(canvas, centerX, centerY + apertureRadius * 0.48f, apertureRadius, rollDegrees)

        val projectedFaces = model.faces.mapNotNull { face ->
            val points = face.points.map { transform.project(it) }
            if (points.any { it == null }) return@mapNotNull null
            val visible = points.filterNotNull()
            val normal = faceNormal(visible.map { it.camera })
            val light = (0.43f + 0.57f * max(0f, dot(normal, LIGHT))).coerceIn(0.28f, 1f)
            ProjectedFace(
                points = visible.map { it.screen },
                depth = visible.map { it.depth }.average().toFloat(),
                color = if (face.color == ACCENT_MARKER) accentColor else face.color,
                light = light,
            )
        }.sortedByDescending { it.depth }

        projectedFaces.forEach { face ->
            if (face.points.size < 3) return@forEach
            path.reset()
            path.moveTo(face.points.first().x, face.points.first().y)
            face.points.drop(1).forEach { path.lineTo(it.x, it.y) }
            path.close()
            fillPaint.color = shade(face.color, face.light)
            canvas.drawPath(path, fillPaint)
            edgePaint.color = Color.argb(155, 5, 5, 5)
            edgePaint.strokeWidth = max(0.75f, apertureRadius * 0.006f)
            canvas.drawPath(path, edgePaint)
        }

        model.segments.mapNotNull { segment ->
            val start = transform.project(segment.start) ?: return@mapNotNull null
            val end = transform.project(segment.end) ?: return@mapNotNull null
            ProjectedSegment(
                start = start.screen,
                end = end.screen,
                depth = (start.depth + end.depth) / 2f,
                color = if (segment.color == ACCENT_MARKER) accentColor else segment.color,
                width = segment.width,
            )
        }.sortedByDescending { it.depth }.forEach { segment ->
            edgePaint.color = segment.color
            edgePaint.strokeWidth = max(1f, segment.width * apertureRadius / 170f)
            edgePaint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(segment.start.x, segment.start.y, segment.end.x, segment.end.y, edgePaint)
        }
        edgePaint.strokeCap = Paint.Cap.BUTT
    }

    internal fun supports(vehicleId: String): Boolean = vehicleId in models

    private inner class Projection(
        private val centerX: Float,
        private val centerY: Float,
        private val scale: Float,
        pitchDegrees: Float,
        rollDegrees: Float,
        cameraYawDegrees: Float,
        cameraElevationDegrees: Float,
    ) {
        private val pitch = radians(pitchDegrees)
        private val roll = radians(rollDegrees)
        private val yaw = radians(cameraYawDegrees)
        private val elevation = radians(cameraElevationDegrees)

        fun project(source: V3): CameraPoint? {
            // Vehicle attitude: positive pitch raises the nose (+Z), positive roll raises the left.
            val rolled = V3(
                source.x * cos(roll) - source.y * sin(roll),
                source.x * sin(roll) + source.y * cos(roll),
                source.z,
            )
            val pitched = V3(
                rolled.x,
                rolled.y * cos(pitch) + rolled.z * sin(pitch),
                -rolled.y * sin(pitch) + rolled.z * cos(pitch),
            )
            val orbited = V3(
                pitched.x * cos(yaw) - pitched.z * sin(yaw),
                pitched.y,
                pitched.x * sin(yaw) + pitched.z * cos(yaw),
            )
            val camera = V3(
                orbited.x,
                orbited.y * cos(elevation) + orbited.z * sin(elevation),
                -orbited.y * sin(elevation) + orbited.z * cos(elevation),
            )
            val depth = CAMERA_DISTANCE + camera.z
            if (depth <= 1f) return null
            val perspective = FOCAL_LENGTH / depth
            return CameraPoint(
                screen = PointF(
                    centerX + camera.x * perspective * scale,
                    centerY - camera.y * perspective * scale,
                ),
                depth = depth,
                camera = camera,
            )
        }
    }

    private class Builder {
        val faces = mutableListOf<Face>()
        val segments = mutableListOf<Segment>()

        fun box(center: V3, half: V3, color: Int) {
            val p = listOf(
                V3(center.x - half.x, center.y - half.y, center.z - half.z),
                V3(center.x + half.x, center.y - half.y, center.z - half.z),
                V3(center.x + half.x, center.y + half.y, center.z - half.z),
                V3(center.x - half.x, center.y + half.y, center.z - half.z),
                V3(center.x - half.x, center.y - half.y, center.z + half.z),
                V3(center.x + half.x, center.y - half.y, center.z + half.z),
                V3(center.x + half.x, center.y + half.y, center.z + half.z),
                V3(center.x - half.x, center.y + half.y, center.z + half.z),
            )
            listOf(
                listOf(0, 1, 2, 3), listOf(5, 4, 7, 6), listOf(4, 0, 3, 7),
                listOf(1, 5, 6, 2), listOf(3, 2, 6, 7), listOf(4, 5, 1, 0),
            ).forEach { indices -> faces += Face(indices.map { p[it] }, color) }
        }

        fun wheel(center: V3, radius: Float, halfWidth: Float) {
            val left = mutableListOf<V3>()
            val right = mutableListOf<V3>()
            repeat(10) { index ->
                val angle = (index / 10f) * (PI * 2).toFloat()
                left += V3(center.x - halfWidth, center.y + cos(angle) * radius, center.z + sin(angle) * radius)
                right += V3(center.x + halfWidth, center.y + cos(angle) * radius, center.z + sin(angle) * radius)
            }
            repeat(10) { index ->
                val next = (index + 1) % 10
                faces += Face(listOf(left[index], right[index], right[next], left[next]), TIRE)
            }
            faces += Face(left.reversed(), HUB)
            faces += Face(right, HUB)
        }

        fun segment(start: V3, end: V3, color: Int = FRAME, width: Float = 4f) {
            segments += Segment(start, end, color, width)
        }

        fun wedge(points: List<V3>, color: Int) {
            require(points.size == 8)
            listOf(
                listOf(0, 1, 2, 3), listOf(4, 7, 6, 5), listOf(0, 4, 5, 1),
                listOf(1, 5, 6, 2), listOf(2, 6, 7, 3), listOf(3, 7, 4, 0),
            ).forEach { indices -> faces += Face(indices.map { points[it] }, color) }
        }

        fun model(scale: Float = 1f) = Model(faces.toList(), segments.toList(), scale)
    }

    private fun buildSxs(): Model = Builder().apply {
        box(V3(0f, -0.12f, 0f), V3(1.05f, 0.28f, 1.48f), BODY)
        box(V3(0f, 0.26f, -0.72f), V3(0.92f, 0.22f, 0.50f), DARK_BODY)
        box(V3(0f, 0.28f, 0.92f), V3(0.92f, 0.22f, 0.46f), BODY)
        listOf(-0.82f to -0.92f, 0.82f to -0.92f, -0.82f to 0.96f, 0.82f to 0.96f).forEach { (x, z) ->
            wheel(V3(x, -0.45f, z), 0.48f, 0.25f)
        }
        val lowerRearL = V3(-0.78f, 0.18f, -0.74f)
        val lowerRearR = V3(0.78f, 0.18f, -0.74f)
        val lowerFrontL = V3(-0.74f, 0.18f, 0.82f)
        val lowerFrontR = V3(0.74f, 0.18f, 0.82f)
        val topRearL = V3(-0.68f, 1.20f, -0.58f)
        val topRearR = V3(0.68f, 1.20f, -0.58f)
        val topFrontL = V3(-0.62f, 1.14f, 0.64f)
        val topFrontR = V3(0.62f, 1.14f, 0.64f)
        listOf(
            lowerRearL to topRearL, lowerRearR to topRearR, lowerFrontL to topFrontL, lowerFrontR to topFrontR,
            topRearL to topRearR, topFrontL to topFrontR, topRearL to topFrontL, topRearR to topFrontR,
        ).forEach { segment(it.first, it.second, FRAME, 5f) }
        segment(V3(-0.76f, 0.02f, -1.49f), V3(0.76f, 0.02f, -1.49f), ACCENT_MARKER, 5f)
    }.model()

    private fun buildSandRail(): Model = Builder().apply {
        box(V3(0f, -0.18f, 0.05f), V3(0.72f, 0.18f, 1.36f), DARK_BODY)
        listOf(-0.90f to -0.92f, 0.90f to -0.92f).forEach { (x, z) -> wheel(V3(x, -0.40f, z), 0.58f, 0.28f) }
        listOf(-0.78f to 0.98f, 0.78f to 0.98f).forEach { (x, z) -> wheel(V3(x, -0.42f, z), 0.40f, 0.20f) }
        val cage = listOf(
            V3(-0.64f, 0.05f, -0.72f), V3(0.64f, 0.05f, -0.72f),
            V3(-0.62f, 0.05f, 0.70f), V3(0.62f, 0.05f, 0.70f),
            V3(-0.55f, 1.08f, -0.52f), V3(0.55f, 1.08f, -0.52f),
            V3(-0.48f, 0.98f, 0.54f), V3(0.48f, 0.98f, 0.54f),
        )
        listOf(0 to 4, 1 to 5, 2 to 6, 3 to 7, 4 to 5, 6 to 7, 4 to 6, 5 to 7, 0 to 1, 2 to 3).forEach {
            segment(cage[it.first], cage[it.second], ACCENT_MARKER, 5f)
        }
        segment(V3(-0.58f, 0.52f, -0.66f), V3(0.58f, 0.52f, -0.66f), FRAME, 7f)
    }.model(1.04f)

    private fun buildTruck(): Model = Builder().apply {
        box(V3(0f, -0.10f, 0f), V3(1.06f, 0.28f, 1.78f), BODY)
        box(V3(0f, 0.52f, 0.82f), V3(0.94f, 0.58f, 0.72f), BODY)
        box(V3(0f, 0.20f, -0.82f), V3(0.92f, 0.20f, 0.72f), DARK_BODY)
        box(V3(0f, 0.68f, 0.34f), V3(0.82f, 0.32f, 0.05f), GLASS)
        listOf(-0.94f to -1.12f, 0.94f to -1.12f, -0.94f to 1.10f, 0.94f to 1.10f).forEach { (x, z) ->
            wheel(V3(x, -0.43f, z), 0.48f, 0.24f)
        }
        segment(V3(-0.78f, 0.02f, -1.79f), V3(0.78f, 0.02f, -1.79f), ACCENT_MARKER, 6f)
    }.model(0.92f)

    private fun buildMiniJetBoat(): Model = Builder().apply {
        wedge(
            listOf(
                V3(-1.04f, -0.26f, -1.30f), V3(1.04f, -0.26f, -1.30f), V3(0.66f, -0.38f, 1.52f), V3(-0.66f, -0.38f, 1.52f),
                V3(-1.18f, 0.22f, -1.30f), V3(1.18f, 0.22f, -1.30f), V3(0.10f, 0.30f, 1.72f), V3(-0.10f, 0.30f, 1.72f),
            ),
            BODY,
        )
        box(V3(0f, 0.34f, -0.16f), V3(0.68f, 0.18f, 0.56f), DARK_BODY)
        box(V3(0f, 0.58f, 0.28f), V3(0.62f, 0.24f, 0.06f), GLASS)
        segment(V3(-0.88f, 0.24f, -1.28f), V3(0.88f, 0.24f, -1.28f), ACCENT_MARKER, 6f)
        segment(V3(-0.94f, 0.10f, -1.36f), V3(-1.12f, 0.02f, -1.58f), FRAME, 4f)
        segment(V3(0.94f, 0.10f, -1.36f), V3(1.12f, 0.02f, -1.58f), FRAME, 4f)
    }.model(1.02f)

    private fun buildSnowmobile(): Model = Builder().apply {
        box(V3(0f, -0.30f, -0.58f), V3(0.58f, 0.28f, 1.02f), TIRE)
        wedge(
            listOf(
                V3(-0.62f, -0.12f, -0.55f), V3(0.62f, -0.12f, -0.55f), V3(0.30f, -0.08f, 1.42f), V3(-0.30f, -0.08f, 1.42f),
                V3(-0.52f, 0.36f, -0.48f), V3(0.52f, 0.36f, -0.48f), V3(0.12f, 0.58f, 1.34f), V3(-0.12f, 0.58f, 1.34f),
            ),
            BODY,
        )
        box(V3(0f, 0.54f, -0.28f), V3(0.42f, 0.16f, 0.62f), DARK_BODY)
        segment(V3(-0.36f, 0.56f, 0.62f), V3(0.36f, 0.56f, 0.62f), FRAME, 5f)
        segment(V3(-0.22f, -0.32f, 1.20f), V3(-0.72f, -0.42f, 1.78f), FRAME, 5f)
        segment(V3(0.22f, -0.32f, 1.20f), V3(0.72f, -0.42f, 1.78f), FRAME, 5f)
        segment(V3(-0.86f, -0.44f, 1.52f), V3(-0.48f, -0.44f, 2.02f), ACCENT_MARKER, 6f)
        segment(V3(0.86f, -0.44f, 1.52f), V3(0.48f, -0.44f, 2.02f), ACCENT_MARKER, 6f)
    }.model(0.98f)

    private fun drawShadow(canvas: Canvas, cx: Float, cy: Float, radius: Float, rollDegrees: Float) {
        canvas.save()
        canvas.rotate(rollDegrees * 0.18f, cx, cy)
        fillPaint.color = Color.argb(105, 0, 0, 0)
        canvas.drawOval(
            cx - radius * 0.56f,
            cy - radius * 0.10f,
            cx + radius * 0.56f,
            cy + radius * 0.10f,
            fillPaint,
        )
        canvas.restore()
    }

    private fun faceNormal(points: List<V3>): V3 {
        if (points.size < 3) return V3(0f, 1f, 0f)
        val a = points[1] - points[0]
        val b = points[2] - points[0]
        val cross = V3(
            a.y * b.z - a.z * b.y,
            a.z * b.x - a.x * b.z,
            a.x * b.y - a.y * b.x,
        )
        val length = kotlin.math.sqrt(cross.x * cross.x + cross.y * cross.y + cross.z * cross.z).coerceAtLeast(0.0001f)
        return cross * (1f / length)
    }

    private fun dot(a: V3, b: V3): Float = a.x * b.x + a.y * b.y + a.z * b.z

    private fun shade(color: Int, factor: Float): Int = Color.rgb(
        min(255, (Color.red(color) * factor).toInt()),
        min(255, (Color.green(color) * factor).toInt()),
        min(255, (Color.blue(color) * factor).toInt()),
    )

    private fun radians(degrees: Float): Float = degrees / 180f * PI.toFloat()

    companion object {
        private const val CAMERA_DISTANCE = 8.2f
        private const val FOCAL_LENGTH = 7.1f
        private const val ACCENT_MARKER = 1
        private val BODY = Color.rgb(212, 216, 220)
        private val DARK_BODY = Color.rgb(39, 43, 47)
        private val FRAME = Color.rgb(220, 224, 226)
        private val TIRE = Color.rgb(18, 19, 20)
        private val HUB = Color.rgb(95, 99, 103)
        private val GLASS = Color.rgb(51, 89, 110)
        private val LIGHT = V3(-0.35f, 0.78f, -0.52f)
        val SUPPORTED_VEHICLES = setOf("sxs", "sand_rail", "truck", "mini_jet_boat", "snowmobile")
    }
}
