package com.cphandheld.particleexperiment

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.sqrt

class ParticleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null): View(context, attrs) {

    private val circlePaint: Paint = Paint()
    private val linePaint: Paint = Paint()
    private val particleManager: ParticleManager = ParticleManager()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRunning = false

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
                linePaint.strokeWidth = getDimension(R.styleable.ParticleView_lineThickness, 10f)
            } finally {
                recycle()
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        particleManager.setBounds(width.toFloat(), height.toFloat())
        particleManager.initialize()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        particleManager.particles.forEach { particle ->
            particleManager.particles.forEach { otherParticle ->
                val dist = sqrt((otherParticle.yPos - particle.yPos) * (otherParticle.yPos - particle.yPos) + (otherParticle.xPos - particle.xPos) * (otherParticle.xPos - particle.xPos))
                if (dist < 100f.px) {
                    canvas.drawLine(particle.xPos, particle.yPos, otherParticle.xPos, otherParticle.yPos, linePaint)
                }
            }
            canvas.drawCircle(particle.xPos, particle.yPos, particle.radius, circlePaint)
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

    private fun tick() {
        mainHandler.postDelayed({
            particleManager.step()
            invalidate()
            if (isRunning) {
                tick()
            }
        }, 10)
    }

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
    }

    fun initialize(radiusVariance: Float? = null) {

        radiusVariance?.let {
            //TODO Variation in particle size
        }

        for (i in 0 until particleCount) {
            val particle = Particle(radius)
            particle.xPos = Math.random().toFloat() * width
            particle.yPos = Math.random().toFloat() * height
            particles.add(particle)
        }
    }

    class Particle(val radius: Float) {

        enum class Sides {
            Top, Right, Bottom, Left
        }

        var xPos: Float = 0f
        var yPos: Float = 0f
        var xVel: Float = (-1 + Math.random() * 2).toFloat()
        var yVel: Float = (-1 + Math.random() * 2).toFloat()
        private val speed = 10f

        fun move() {
            xPos += xVel * speed
            yPos += yVel * speed
        }

        fun validate(xBounds: Float, yBounds: Float): Boolean {
            return !((xPos < -radius * 3) || (xPos > xBounds + (radius * 3)) || (yPos < -radius * 3) || (xPos > yBounds + (radius * 3)))
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

        override fun toString(): String {
            return "Velocity: ($xVel, $yVel)\nPosition: ($xPos, $yPos)"
        }

    }

}

val Float.dp: Float
    get() = (this / Resources.getSystem().displayMetrics.density)
val Float.px: Float
    get() = (this * Resources.getSystem().displayMetrics.density)