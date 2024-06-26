package com.pitchdetector.processors

import android.util.Log
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import com.facebook.react.bridge.ReadableMap

import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import com.pitchdetector.utils.Algorithms

import kotlin.math.log2
import kotlin.math.round

open class BaseProcessor() {
  private val tones = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

  private var dispatcher: AudioDispatcher? = null
  private var processor: AudioProcessor? = null

  private var runner: Thread? = null
  private var emitter: RCTDeviceEventEmitter? = null

  private val handler = PitchDetectionHandler { res, e ->
    val pitchInHz = res.pitch
    process(pitchInHz)
  }

  private fun process(pitchInHz: Float) {
      if (pitchInHz > 0) {  // Check if pitchInHz is greater than zero
        val midiNote = round(12 * log2(pitchInHz / 440) + 69).toInt()
        val index = midiNote % 12
        val octave = (midiNote / 12) - 1  // MIDI note 12 is C1, MIDI note 0 is C-1

        val tone = tones[index]
        val noteWithOctave = "$tone$octave"  // Combines note and octave

        val data: WritableMap = WritableNativeMap()
        data.putDouble("frequency", pitchInHz.toDouble())
        data.putString("tone", noteWithOctave)

        emitter?.emit("data", data)
    }
}

  private fun prepare(config: ReadableMap) {
    val sampleRate = config.getDouble("sampleRate").toFloat()
    val bufferSize = config.getInt("bufferSize")
    val bufferOverLap = config.getInt("bufferOverLap")
    val algorithm = Algorithms.parse(config.getString("algorithm"))

    Log.i("PITCH DETECTOR", "STARTED WITH: algorithm: $algorithm, sampleRate: $sampleRate, bufferSize: $bufferSize, bufferOverLap: $bufferOverLap")

    processor = PitchProcessor(algorithm, sampleRate, bufferSize, handler)
    dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate.toInt(), bufferSize, bufferOverLap);
    dispatcher?.addAudioProcessor(processor)
  }

  fun start(config: ReadableMap, emitter: RCTDeviceEventEmitter) {
    this.emitter = emitter

    prepare(config)

    runner = Thread(dispatcher)
    runner?.start()
  }

  fun stop() {
    dispatcher?.stop()
    runner?.interrupt()
  }
}
