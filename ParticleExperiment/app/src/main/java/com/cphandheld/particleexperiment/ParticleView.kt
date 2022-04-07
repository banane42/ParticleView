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
                lineDistance = getDimension(R.styleable.ParticleView_lineDistance, 100f).dpToPx
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
        particleManager.setBounds(width.toFloat(), height.toFloat())
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

        val elapsed = measureTimeMillis {
            particleManager.particles.forEachIndexed { index, particle ->
                var i = index + 1
                while (i < particleManager.particles.size) {
                    val other = particleManager.particles[i]
                    val dist = particle.distance(other)
                    if (dist > lineDistance) {
                        break
                    }
                    if (!strongLineConnection) {
                        val strength = ((1f - dist / lineDistance) * 255).toInt()
                        linePaint.alpha = strength
                    }
                    canvas.drawLine(particle.xPos, particle.yPos, other.xPos, other.yPos, linePaint)
                    i += 1
                }
                Log.d("ParticleView", "${particle.name} Neighbors checked: ${i - index}")
                circlePaint.alpha = particle.alpha ?: 255
                canvas.drawCircle(particle.xPos, particle.yPos, particle.radius.dpToPx, circlePaint)
            }
        }
//        Log.d("ParticleView", "------------------TICK----------------------")
//        val elapsed = measureTimeMillis {
//
//            var currentParticle = particleManager.particles.first()
//
//            particleManager.particles.forEachIndexed { index, particle ->
//                var i = index - 1
//                var otherParticle = particleManager.particles.getOrElse(i) { particleManager.particles.first() }
//                while (i >= 0) {
//                    val dist = particle.distance(otherParticle)
//                    if (dist > lineDistance) {
//                        break
//                    }
//                    if (!strongLineConnection) {
//                        val strength = ((1f - dist / lineDistance.dpToPx) * 255).toInt()
//                        linePaint.alpha = strength
//                    }
//                    canvas.drawLine(particle.xPos, particle.yPos, otherParticle.xPos, otherParticle.yPos, linePaint)
//                    i -= 1
//                }
//                circlePaint.alpha = particle.alpha ?: 255
//                canvas.drawCircle(particle.xPos, particle.yPos, particle.radius.dpToPx, circlePaint)
//            }
//        }

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

//    private fun tick() {
//        particleManager.step()
//        invalidate()
//    }

//    private fun tick() {
//        mainHandler.postDelayed({
//            particleManager.step()
//            invalidate()
//            if (isRunning) {
//                tick()
//            }
//        }, 10)
//    }


}

class ParticleManager {

    private var width = 0f
    private var height = 0f
    var radius = 10f
    private var particleCount = 0

    val particles = ArrayList<Particle>()

    fun setParticleCount(count: Int) {
        particleCount = count
    }

    fun setBounds(width: Float, height: Float) {
        this.width = width
        this.height = height
    }

    fun setParticleRadius(radius: Float) {
        this.radius = radius
    }

    fun step() {
        particles.forEach { particle ->
            particle.move()
            if (!particle.validate(width, height)) {
                particle.reset(width, height)
            }
        }
        particles.sort()
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
            particles.add(particle)
        }
        particles.sort()
    }

    class Particle(
        val name: String,
        val radius: Float,
        val alpha: Int? = null,
        minSpeed: Float = 1f,
        maxSpeed: Float = 10f
    ): Comparable<Particle> {

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

        fun distToOrigin(): Float {
//            val originX = -radius * 3
//            val originY = -radius * 3
            val originX = 0
            val originY = 0
            return sqrt((yPos - originY) * (yPos - originY) + (xPos - originX) * (xPos - originX))
        }

        override fun toString(): String {
            return "$name Velocity: ($xVel, $yVel) Position: ($xPos, $yPos)\n"
        }

        override fun compareTo(other: Particle): Int {
            val originDiff = ((distToOrigin() - other.distToOrigin()) * 1000f).toInt()
            val xDiff = (xPos - other.xPos) * 10f
            val yDiff = yPos - other.yPos
            return ((xDiff + yDiff + originDiff) * 1000f).toInt()
        }

    }

}

val Float.pxToDp: Float
    get() = (this / Resources.getSystem().displayMetrics.density)
val Float.dpToPx: Float
    get() = (this * Resources.getSystem().displayMetrics.density)