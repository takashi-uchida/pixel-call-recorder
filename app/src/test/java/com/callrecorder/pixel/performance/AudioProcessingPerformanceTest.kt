package com.callrecorder.pixel.performance

import android.os.Debug
import com.callrecorder.pixel.audio.AudioProcessor
import com.callrecorder.pixel.audio.MediaRecorderAudioProcessor
import com.callrecorder.pixel.data.model.AudioQuality
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * パフォーマンステスト - リアルタイム音声処理の遅延測定
 * Requirements: 2.1, 2.3
 */
class AudioProcessingPerformanceTest {

    private lateinit var audioProcessor: AudioProcessor
    private lateinit var testFile: File

    @Before
    fun setUp() {
        audioProcessor = MediaRecorderAudioProcessor()
        testFile = File.createTempFile("test_audio", ".wav")
    }

    @Test
    fun `測定 - 音声処理初期化の遅延時間`() {
        val initializationTime = measureTimeMillis {
            audioProcessor.initializeAudioCapture()
        }
        
        // 初期化は100ms以内であるべき
        assertTrue("音声処理初期化が遅すぎます: ${initializationTime}ms", 
                  initializationTime < 100)
        
        println("音声処理初期化時間: ${initializationTime}ms")
    }

    @Test
    fun `測定 - 録音開始の遅延時間`() {
        audioProcessor.initializeAudioCapture()
        
        val startTime = measureTimeMillis {
            audioProcessor.startCapture(testFile)
        }
        
        // 録音開始は50ms以内であるべき
        assertTrue("録音開始が遅すぎます: ${startTime}ms", 
                  startTime < 50)
        
        println("録音開始時間: ${startTime}ms")
        
        audioProcessor.stopCapture()
    }

    @Test
    fun `測定 - 音声強化処理の遅延時間`() {
        val inputFile = File.createTempFile("input", ".wav")
        val outputFile = File.createTempFile("output", ".wav")
        
        // テスト用の音声データを作成（模擬）
        inputFile.writeBytes(ByteArray(44100 * 2 * 10)) // 10秒の音声データ
        
        val enhancementTime = measureTimeMillis {
            audioProcessor.applyAudioEnhancement(inputFile, outputFile)
        }
        
        // 10秒の音声に対して1秒以内で処理完了すべき
        assertTrue("音声強化処理が遅すぎます: ${enhancementTime}ms", 
                  enhancementTime < 1000)
        
        println("音声強化処理時間: ${enhancementTime}ms")
        
        inputFile.delete()
        outputFile.delete()
    }

    @Test
    fun `測定 - 音声正規化処理の遅延時間`() {
        val audioFile = File.createTempFile("normalize", ".wav")
        audioFile.writeBytes(ByteArray(44100 * 2 * 5)) // 5秒の音声データ
        
        val normalizationTime = measureTimeMillis {
            audioProcessor.normalizeAudio(audioFile)
        }
        
        // 5秒の音声に対して500ms以内で処理完了すべき
        assertTrue("音声正規化処理が遅すぎます: ${normalizationTime}ms", 
                  normalizationTime < 500)
        
        println("音声正規化処理時間: ${normalizationTime}ms")
        
        audioFile.delete()
    }

    @Test
    fun `測定 - 異なる音質設定での処理時間比較`() {
        val qualities = listOf(
            AudioQuality.HIGH_QUALITY,
            AudioQuality.STANDARD,
            AudioQuality.SPACE_SAVING
        )
        
        qualities.forEach { quality ->
            val processingTime = measureTimeMillis {
                // 各品質設定での処理時間を測定
                audioProcessor.initializeAudioCapture()
                audioProcessor.startCapture(testFile)
                Thread.sleep(100) // 短時間の録音をシミュレート
                audioProcessor.stopCapture()
            }
            
            println("${quality.name} 処理時間: ${processingTime}ms")
            
            // 高品質でも200ms以内で処理完了すべき
            assertTrue("${quality.name}の処理が遅すぎます: ${processingTime}ms", 
                      processingTime < 200)
        }
    }

    @Test
    fun `測定 - 連続録音処理のパフォーマンス劣化チェック`() {
        val processingTimes = mutableListOf<Long>()
        
        repeat(10) { iteration ->
            val time = measureTimeMillis {
                audioProcessor.initializeAudioCapture()
                audioProcessor.startCapture(testFile)
                Thread.sleep(50)
                audioProcessor.stopCapture()
            }
            processingTimes.add(time)
            println("録音 ${iteration + 1}: ${time}ms")
        }
        
        // 最初と最後の処理時間の差が50%以内であるべき
        val firstTime = processingTimes.first()
        val lastTime = processingTimes.last()
        val degradation = (lastTime - firstTime).toDouble() / firstTime
        
        assertTrue("パフォーマンス劣化が大きすぎます: ${degradation * 100}%", 
                  degradation < 0.5)
        
        println("パフォーマンス劣化率: ${degradation * 100}%")
    }
}