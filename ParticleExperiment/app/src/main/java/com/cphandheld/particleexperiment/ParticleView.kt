package com.cphandheld.particleexperiment

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.lang.Integer.min
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

class ParticleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null): View(context, attrs) {

    private val circlePaint: Paint = Paint()
    private val linePaint: Paint = Paint()
    private var particleVariance: Float
    private var particleAlphaMin: Float? = null
    private var lineDistance: Float
    private val maxSpeed: Float
    private val minSpeed: Float
    private val strongLineConnection: Boolean
    private val particleManager: ParticleManager = ParticleManager()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var tickRate = 10L

    init {

        circlePaint.style = Paint.Style.FILL
        linePaint.style = Paint.Style.FILL_AND_STROKE

        context.obtainStyledAttributes(attrs, R.styleable.ParticleView).apply {
            try {
                val count = getInteger(R.styleable.ParticleView_particleCount, 0)
                particleManager.setParticleCount(count)
                particleManager.setParticleRadius(getDimension(R.styleable.ParticleView_particleRadius, 10f))
                circlePaint.color = getColor(R.styleable.ParticleView_particleColor, Color.WHITE)
                linePaint.color = getColor(R.styleable.ParticleView_lineColor, Color.WHITE)
                linePaint.strokeWidth = getDimension(R.styleable.ParticleView_lineThickness, 1f)
                lineDistance = getDimension(R.styleable.ParticleView_lineDistance, 0f).dpToPx
                maxSpeed = getFloat(R.styleable.ParticleView_maxSpeed, 10f)
                minSpeed = getFloat(R.styleable.ParticleView_minSpeed, 1f)
                strongLineConnection = getBoolean(R.styleable.ParticleView_strongLineConnection, false)

                particleVariance = getFloat(R.styleable.ParticleView_particleRadiusVariance, 0f)

                val gottenAlphaMin = getFloat(R.styleable.ParticleView_alphaMin, -1f)
                if (gottenAlphaMin > 0) {
                    particleAlphaMin = if (gottenAlphaMin > 1f) {
                        1f
                    } else {
                        gottenAlphaMin
                    }
                }

            } finally {
                recycle()
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        particleManager.setBounds(width.toFloat(), height.toFloat(), lineDistance)
        particleManager.initialize(particleVariance, particleAlphaMin, minSpeed, maxSpeed)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Brute Force. Max particles before slowdown is 150
        // lineDistance of 30dp
//        val elapsed = measureTimeMillis {
//            particleManager.particles.forEach { particle ->
//                particleManager.particles.forEach { otherParticle ->
//                    val dist = particle.distance(otherParticle)
//                    if (dist < lineDistance.dpToPx) {
//                        if (!strongLineConnection) {
//                            val strength = ((1f - dist / lineDistance.dpToPx) * 255).toInt()
//                            linePaint.alpha = strength
//                        }
//                        canvas.drawLine(particle.xPos, particle.yPos, otherParticle.xPos, otherParticle.yPos, linePaint)
//                    }
//                }
//                circlePaint.alpha = particle.alpha ?: 255
//                canvas.drawCircle(particle.xPos, particle.yPos, particle.radius.dpToPx, circlePaint)
//            }
//        }

        // Quadrant Connections
        // lineDist of 30dp
        // Slowdown at 425 particles
        val elapsed = measureTimeMillis {
            particleManager.particles.forEach { particle ->
                circlePaint.alpha = particle.alpha ?: 255
                canvas.drawCircle(particle.xPos, particle.yPos, particle.radius.dpToPx, circlePaint)
            }
            particleManager.lines.forEach { line ->
                if (!strongLineConnection) {
                    val strength = ((1f - line.distance / lineDistance) * 255).toInt()
                    linePaint.alpha = strength
                }
                canvas.drawLine(line.x1, line.y1, line.x2, line.y2, linePaint)
            }
        }

        if (isRunning) {
            tick(elapsed)
        }

    }

    fun start() {
        if (!isRunning) {
            isRunning = true
            tick()
        }
    }

    fun stop() {
        isRunning = false
    }

    fun isRunning(): Boolean {
        return isRunning
    }

    private fun tick(timeElapsed: Long = tickRate) {
        val diff = tickRate - timeElapsed
        if (diff <= 0) {
            particleManager.step()
            invalidate()
            return
        }
        mainHandler.postDelayed({
            particleManager.step()
            invalidate()
        }, diff)
    }

    companion object {
        private const val TAG = "ParticleView"
    }


}

class ParticleManager {

    private val TAG = "ParticleManager"

    private var width = 0f
    private var height = 0f
    private var lineDist = 0f
    var radius = 10f
    private var particleCount = 0

    private lateinit var quadrants: Array<Array<ArrayList<Particle>>>
    private var connections: HashMap<Particle, Particle> = HashMap()
    var particles = arrayListOf<Particle>()
    var lines = arrayListOf<Line>()

    fun setParticleCount(count: Int) {
        particleCount = count
    }

    fun setBounds(width: Float, height: Float, lineDist: Float) {
        this.width = width
        this.height = height
        this.lineDist = lineDist
        quadrants = if (lineDist > 0) {
            val cols = (width / lineDist).toInt()
            val rows = (height / lineDist).toInt()
            Array(rows) { Array(cols) { ArrayList() } }
        } else {
            Array(1) { Array(1) { ArrayList() } }
        }
    }

    fun setParticleRadius(radius: Float) {
        this.radius = radius
    }

    fun step() {
        // Organize particles
        val newQuads = Array(quadrants.size) { Array(quadrants[0].size) { ArrayList<Particle>() } }
        quadrants.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, particles ->
                particles.forEach { particle ->
                    particle.move()
                    if (!particle.validate(width, height)) {
                        particle.reset(width, height)
                    }
                    val newCol = min(((particle.xPos / (width + (3 * particle.radius))) * quadrants[0].size).toInt(), quadrants[0].size - 1)
                    val newRow = min(((particle.yPos / (height + (3 * particle.radius))) * quadrants.size).toInt(), quadrants.size - 1)
                    newQuads[newRow][newCol].add(particle)
                }
            }
        }
        quadrants = newQuads
        // Create Connections
        connections.clear()
        lines.clear()
        quadrants.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, particles ->
                particles.forEach { particle ->
                    for (i in -1..1) {
                        for (j in -1..1) {
                            val r = quadrants.getOrNull(row + i)
                            val c = r?.getOrNull(col + j)
                            c?.let {
                                it.forEach { other ->
                                    val dist = particle.distance(other)
                                    if (dist > 0.0f && dist < lineDist) {
                                        val name = "${particle.name} <-> ${other.name}"
                                        connections[particle] = other
                                        val line = Line(name, particle.xPos, particle.yPos, other.xPos, other.yPos)
                                        if (!connections.containsKey(other)) {
                                            lines.add(line)
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

    fun initialize(radiusVariance: Float, alphaMin: Float? = null, maxSpeed: Float, minSpeed: Float) {

        for (i in 0 until particleCount) {
            val variance = radius * radiusVariance
            val radiusMin = radius - variance
            val radiusMax = radius + variance
            val calcRadius = radiusMin + Math.random().toFloat() * (radiusMax - radiusMin)

            val alpha: Int? = ((alphaMin?.plus(Math.random().toFloat() * 1f))?.times(255))?.toInt()

            val particle = Particle(Util.names.random(), calcRadius, alpha, maxSpeed, minSpeed)
            particle.xPos = Math.random().toFloat() * width
            particle.yPos = Math.random().toFloat() * height
            val col = ((particle.xPos / width) * quadrants[0].size).toInt()
            val row = ((particle.yPos / height) * quadrants.size).toInt()
            quadrants[row][col].add(particle)
            particles.add(particle)
        }
    }

    class Line(
        val name: String,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float
    ) {
        val distance = sqrt((y2 - y1) * (y2 - y1) + (x2 - x1) * (x2 - x1))
    }

    class Particle(
        val name: String,
        val radius: Float,
        val alpha: Int? = null,
        minSpeed: Float = 1f,
        maxSpeed: Float = 10f
    ) {

        enum class Sides {
            Top, Right, Bottom, Left
        }

        var xPos: Float = 0f
        var yPos: Float = 0f
        var xVel: Float = (-1 + Math.random() * 2).toFloat()
        var yVel: Float = (-1 + Math.random() * 2).toFloat()
        private val speed = (minSpeed + Math.random() * maxSpeed).toFloat()

        fun move() {
            xPos += xVel * speed
            yPos += yVel * speed
        }

        fun validate(xBounds: Float, yBounds: Float): Boolean {
            return !((xPos < -radius * 3) || (xPos > xBounds + (radius * 3)) || (yPos < -radius * 3) || (yPos > yBounds + (radius * 3)))
        }

        fun reset(xBounds: Float, yBounds: Float) {
            when (Sides.values().random()) {
                Sides.Top -> {
                    xPos = Math.random().toFloat() * xBounds
                    yPos = -radius * 1.5f
                    xVel = (-1 + Math.random() * 2).toFloat()
                    yVel = Math.random().toFloat()
                }
                Sides.Right -> {
                    xPos = xBounds + (radius * 1.5f)
                    yPos = Math.random().toFloat() * yBounds
                    xVel = -Math.random().toFloat()
                    yVel = (-1 + Math.random() * 2).toFloat()
                }
                Sides.Bottom -> {
                    xPos = Math.random().toFloat() * xBounds
                    yPos = yBounds + (radius * 1.5f)
                    xVel = (-1 + Math.random() * 2).toFloat()
                    yVel = -Math.random().toFloat()
                }
                Sides.Left -> {
                    xPos = -radius * 1.5f
                    yPos = Math.random().toFloat() * yBounds
                    xVel = Math.random().toFloat()
                    yVel = (-1 + Math.random() * 2).toFloat()
                }
            }
        }

        fun distance(other: Particle): Float {
            return sqrt((other.yPos - yPos) * (other.yPos - yPos) + (other.xPos - xPos) * (other.xPos - xPos))
        }

        override fun toString(): String {
            return "$name Velocity: ($xVel, $yVel) Position: ($xPos, $yPos)\n"
        }

    }

}

val Float.pxToDp: Float
    get() = (this / Resources.getSystem().displayMetrics.density)
val Float.dpToPx: Float
    get() = (this * Resources.getSystem().displayMetrics.density)