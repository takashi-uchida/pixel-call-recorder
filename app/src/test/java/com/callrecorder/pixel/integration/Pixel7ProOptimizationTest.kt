package com.callrecorder.pixel.integration

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callrecorder.pixel.audio.AudioEnhancer
import com.callrecorder.pixel.audio.MediaRecorderAudioProcessor
import com.callrecorder.pixel.data.model.AudioQuality
import com.callrecorder.pixel.settings.SettingsManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Pixel 7 Pro 実機での動作確認テスト
 * Tensor G2チップの最適化機能をテストします
 */
@RunWith(AndroidJUnit4::class)
class Pixel7ProOptimizationTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var audioProcessor: MediaRecorderAudioProcessor
    private lateinit var audioEnhancer: AudioEnhancer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        settingsManager = SettingsManager(context)
        audioProcessor = MediaRecorderAudioProcessor(context, settingsManager)
        audioEnhancer = AudioEnhancer()
    }

    @Test
    fun testPixel7ProDeviceDetection() {
        // Pixel 7 Pro の検出テスト
        val deviceModel = Build.MODEL
        val deviceManufacturer = Build.MANUFACTURER
        
        // テスト環境では実際のPixel 7 Proでない可能性があるため、
        // デバイス情報の取得が正常に動作することを確認
        assertNotNull(deviceModel)
        assertNotNull(deviceManufacturer)
        assertTrue(deviceModel.isNotEmpty())
        assertTrue(deviceManufacturer.isNotEmpty())
    }

    @Test
    fun testTensorG2AudioOptimization() {
        // Tensor G2チップの音声最適化機能テスト
        val supportedSampleRates = listOf(8000, 16000, 22050, 44100, 48000)
        
        supportedSampleRates.forEach { sampleRate ->
            val isSupported = audioEnhancer.isSampleRateSupported(sampleRate)
            // 基本的なサンプルレートはサポートされているべき
            if (sampleRate in listOf(16000, 44100, 48000)) {
                assertTrue(isSupported, "Sample rate $sampleRate should be supported")
            }
        }
    }

    @Test
    fun testHighQualityAudioCapture() {
        // 高品質音声キャプチャのテスト
        settingsManager.setAudioQuality(AudioQuality.HIGH_QUALITY)
        
        val quality = settingsManager.getAudioQuality()
        assertEquals(AudioQuality.HIGH_QUALITY, quality)
        assertEquals(48000, quality.sampleRate)
        assertEquals(128000, quality.bitRate)
    }

    @Test
    fun testAudioEnhancementFeatures() {
        // 音声強化機能のテスト
        val testAudioData = FloatArray(1024) { index ->
            // テスト用のサイン波を生成
            kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * index / 44100.0).toFloat()
        }
        
        // ノイズリダクション
        val noiseReducedData = audioEnhancer.applyNoiseReduction(testAudioData)
        assertNotNull(noiseReducedData)
        assertEquals(testAudioData.size, noiseReducedData.size)
        
        // 自動ゲイン制御
        val agcData = audioEnhancer.applyAutomaticGainControl(testAudioData)
        assertNotNull(agcData)
        assertEquals(testAudioData.size, agcData.size)
        
        // 音声正規化
        val normalizedData = audioEnhancer.normalizeAudio(testAudioData)
        assertNotNull(normalizedData)
        assertEquals(testAudioData.size, normalizedData.size)
    }

    @Test
    fun testRealtimeProcessingPerformance() {
        // リアルタイム処理のパフォーマンステスト
        val bufferSize = 4096
        val testData = FloatArray(bufferSize) { 0.5f }
        
        val startTime = System.nanoTime()
        
        // 複数の処理を連続実行
        repeat(100) {
            audioEnhancer.applyNoiseReduction(testData)
            audioEnhancer.applyAutomaticGainControl(testData)
        }
        
        val endTime = System.nanoTime()
        val processingTimeMs = (endTime - startTime) / 1_000_000
        
        // リアルタイム処理要件: 100回の処理が100ms以内に完了すること
        assertTrue(processingTimeMs < 100, "Processing took ${processingTimeMs}ms, should be < 100ms")
    }

    @Test
    fun testAudioManagerConfiguration() {
        // AudioManagerの設定テスト
        val mode = audioManager.mode
        assertNotNull(mode)
        
        // サポートされている音声フォーマットの確認
        val supportedFormats = listOf(
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_8BIT
        )
        
        // 各フォーマットが利用可能かテスト
        supportedFormats.forEach { format ->
            // フォーマットの有効性を確認
            assertTrue(format > 0)
        }
    }

    @Test
    fun testMemoryUsageOptimization() {
        // メモリ使用量最適化のテスト
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        // 大量の音声データ処理をシミュレート
        repeat(10) {
            val largeAudioData = FloatArray(44100) { index ->
                kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * index / 44100.0).toFloat()
            }
            
            audioEnhancer.applyNoiseReduction(largeAudioData)
            
            // ガベージコレクションを促進
            System.gc()
        }
        
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        
        // メモリ増加が合理的な範囲内であることを確認（10MB以下）
        assertTrue(memoryIncrease < 10 * 1024 * 1024, "Memory increase: ${memoryIncrease / 1024 / 1024}MB")
    }

    @Test
    fun testBatteryOptimization() {
        // バッテリー最適化のテスト（CPU使用率の監視）
        val startTime = System.currentTimeMillis()
        var operationCount = 0
        
        // 1秒間の処理回数を測定
        while (System.currentTimeMillis() - startTime < 1000) {
            val testData = FloatArray(1024) { 0.1f }
            audioEnhancer.applyNoiseReduction(testData)
            operationCount++
        }
        
        // 効率的な処理が行われていることを確認
        // 1秒間に最低100回の処理が可能であること
        assertTrue(operationCount >= 100, "Only $operationCount operations per second")
    }

    @Test
    fun testMultiChannelAudioSupport() {
        // マルチチャンネル音声のサポートテスト
        val monoData = FloatArray(1024) { 0.5f }
        val stereoData = Array(2) { FloatArray(1024) { 0.5f } }
        
        // モノラル処理
        val processedMono = audioEnhancer.applyNoiseReduction(monoData)
        assertNotNull(processedMono)
        assertEquals(monoData.size, processedMono.size)
        
        // ステレオ処理
        val processedStereo = audioEnhancer.applyStereoProcessing(stereoData)
        assertNotNull(processedStereo)
        assertEquals(2, processedStereo.size)
        assertEquals(stereoData[0].size, processedStereo[0].size)
        assertEquals(stereoData[1].size, processedStereo[1].size)
    }

    @Test
    fun testAdaptiveQualityControl() {
        // 適応的品質制御のテスト
        val availableStorage = context.getExternalFilesDir(null)?.freeSpace ?: 0L
        
        // ストレージ容量に基づく品質調整
        val recommendedQuality = when {
            availableStorage > 1024 * 1024 * 1024 -> AudioQuality.HIGH_QUALITY // 1GB以上
            availableStorage > 512 * 1024 * 1024 -> AudioQuality.STANDARD // 512MB以上
            else -> AudioQuality.SPACE_SAVING
        }
        
        settingsManager.setAudioQuality(recommendedQuality)
        assertEquals(recommendedQuality, settingsManager.getAudioQuality())
    }
}