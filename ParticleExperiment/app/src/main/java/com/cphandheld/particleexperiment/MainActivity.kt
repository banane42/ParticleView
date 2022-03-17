package com.cphandheld.particleexperiment

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val particleView: ParticleView = findViewById(R.id.particleView)

        findViewById<Button>(R.id.buttonStartStop).setOnClickListener {
            if (particleView.isRunning()) {
                particleView.stop()
            } else {
                particleView.start()
            }
        }

    }
}