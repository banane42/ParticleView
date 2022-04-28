package com.cphandheld.particleexperiment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.coroutines.CoroutineContext

// Work in progress Class. Intended to replace the old class once complete
class ParticleManagerV2(
    val particleCount: Int = 10,
    val maxStates: Int = 20,
    private val width: Float,
    private val height: Float,
    private val lineDist: Float,
    private val particleRadius: Float,
    private val radiusVariance: Float,
    private val alphaMin: Float?,
    private val maxSpeed: Float,
    private val minSpeed: Float
) {
    private val stateQueue: Queue<StateV2> = LinkedList()

    private val rows = if (lineDist > 0) (height / lineDist).toInt() else 1
    private val cols = if (lineDist > 0) (width / lineDist).toInt() else 1
    private var isCalculatingStates = false

    fun startStateCalculation() {
        if (!isCalculatingStates) {
            isCalculatingStates = true
            calculateState()
        }
    }

    fun stopStateCalculation() {
        isCalculatingStates = false
    }

    fun getState(): StateV2? {
        val state = stateQueue.poll()
        if (stateQueue.size < maxStates) {
            calculateState()
        }
        return state
    }

    private fun calculateState() {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.async {
            if (stateQueue.size < maxStates) {
                stateQueue.lastOrNull()?.let {
                    val newState = createState(it)
                    stateQueue.add(newState)
                } ?: run {
                    stateQueue.add(createState())
                }
                if (stateQueue.size < maxStates) {
                    calculateState()
                }
            }
        }
    }

    // Create a state from scratch
    private fun createState(): StateV2 {
        val initParticles = Array(rows) { Array(cols) { ArrayList<Particle>() } }

        // Add particles to the state
        for (i in 0 until particleCount) {
            val variance = particleRadius * radiusVariance
            val radiusMin = particleRadius - variance
            val radiusMax = particleRadius + variance
            val calcRadius = radiusMin + Math.random().toFloat() * (radiusMax - radiusMin)

            val alpha: Int? = ((alphaMin?.plus(Math.random().toFloat() * 1f))?.times(255))?.toInt()

            val particle = Particle(Util.names.random(), calcRadius, alpha, maxSpeed, minSpeed)
            particle.xPos = Math.random().toFloat() * width
            particle.yPos = Math.random().toFloat() * height
            val col = ((particle.xPos / width) * cols).toInt()
            val row = ((particle.yPos / height) * rows).toInt()

            initParticles[row][col].add(particle)
        }
        //Create Lines
        val connections = HashMap<Particle, Particle>()
        val lines = ArrayList<Line>()
        initParticles.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, particles ->
                particles.forEach { particle ->
                    for (i in -1..1) {
                        for (j in -1..1) {
                            initParticles.getOrNull(row + i)?.getOrNull(col + j)?.let {
                                it.forEach { other ->
                                    val dist = particle.distance(other)
                                    if (dist > 0.0f && dist < lineDist) {
                                        connections[particle] = other
                                        val line = Line(particle.xPos, particle.yPos, other.xPos, other.yPos)
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
        return StateV2(initParticles, lines)
    }

    // Create a state as a mutation of another state
    private fun createState(parentState: StateV2): StateV2 {
        // Move and organize Particles
        val nextParticles = Array(parentState.particles.size) { Array(parentState.particles[0].size) { ArrayList<Particle>() } }
        parentState.particles.forEach { cols ->
            cols.forEach { particles ->
                particles.forEach { particle ->
                    particle.move()
                    if (!particle.validate(width, height)) {
                        particle.reset(width, height)
                    }
                    val newCol = Integer.min(
                        ((particle.xPos / (width + (3 * particle.radius))) * parentState.particles[0].size).toInt(),
                        parentState.particles[0].size - 1
                    )
                    val newRow = Integer.min(
                        ((particle.yPos / (height + (3 * particle.radius))) * parentState.particles.size).toInt(),
                        parentState.particles.size - 1
                    )
                    nextParticles[newRow][newCol].add(particle.clone())
                }
            }
        }
        //Create Lines
        val connections = HashMap<Particle, Particle>()
        val lines = ArrayList<Line>()
        nextParticles.forEachIndexed { row, cols ->  
            cols.forEachIndexed { col, particles ->  
                particles.forEach { particle ->  
                    for (i in -1..1) {
                        for (j in -1..1) {
                            nextParticles.getOrNull(row + i)?.getOrNull(col + j)?.let {
                                it.forEach { other ->
                                    val dist = particle.distance(other)
                                    if (dist > 0.0f && dist < lineDist) {
                                        connections[particle] = other
                                        val line = Line(particle.xPos, particle.yPos, other.xPos, other.yPos)
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
        return StateV2(nextParticles, lines)
    }

}