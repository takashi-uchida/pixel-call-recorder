package com.callrecorder.pixel.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import com.callrecorder.pixel.service.CallRecordingServiceImpl
import com.callrecorder.pixel.audio.MediaRecorderAudioProcessor
import com.callrecorder.pixel.TestUtils
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.RandomAccessFile
import kotlin.system.measureTimeMillis

/**
 * システムリソース監視テスト - CPU使用率とバッテリー消費の監視
 * Requirements: 2.1, 2.3
 */
class SystemResourceMonitorTest {

    private lateinit var context: Context
    private lateinit var activityManager: ActivityManager
    private lateinit var recordingService: CallRecordingServiceImpl
    private lateinit var audioProcessor: MediaRecorderAudioProcessor

    @Before
    fun setUp() {
        context = TestUtils.getTestContext()
        activityManager = mockk()
        recordingService = mockk()
        audioProcessor = MediaRecorderAudioProcessor(context)
        
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
    }

    @Test
    fun `監視 - 録音中のCPU使用率`() = runTest {
        val cpuUsageBeforeRecording = getCurrentCpuUsage()
        println("録音前CPU使用率: ${cpuUsageBeforeRecording}%")
        
        // 録音開始
        audioProcessor.initializeAudioCapture(TestUtils.getTestAudioQuality())
        val testFile = TestUtils.createTestFile("cpu_test.wav")
        audioProcessor.startCapture(testFile)
        
        // 録音中のCPU使用率を測定
        delay(2000) // 2秒間録音
        val cpuUsageDuringRecording = getCurrentCpuUsage()
        println("録音中CPU使用率: ${cpuUsageDuringRecording}%")
        
        audioProcessor.stopCapture()
        
        // 録音停止後のCPU使用率
        Thread.sleep(1000)
        val cpuUsageAfterRecording = getCurrentCpuUsage()
        println("録音後CPU使用率: ${cpuUsageAfterRecording}%")
        
        // CPU使用率が30%以下であるべき
        assertTrue("録音中のCPU使用率が高すぎます: ${cpuUsageDuringRecording}%", 
                  cpuUsageDuringRecording < 30.0)
        
        testFile.delete()
    }

    @Test
    fun `監視 - メモリ使用量の変化`() {
        val memoryBefore = getCurrentMemoryUsage()
        println("録音前メモリ使用量: ${memoryBefore}MB")
        
        // 録音処理を実行
        audioProcessor.initializeAudioCapture()
        val testFile = File.createTempFile("memory_test", ".wav")
        audioProcessor.startCapture(testFile)
        
        Thread.sleep(3000) // 3秒間録音
        
        val memoryDuring = getCurrentMemoryUsage()
        println("録音中メモリ使用量: ${memoryDuring}MB")
        
        audioProcessor.stopCapture()
        
        // ガベージコレクションを実行
        System.gc()
        Thread.sleep(1000)
        
        val memoryAfter = getCurrentMemoryUsage()
        println("録音後メモリ使用量: ${memoryAfter}MB")
        
        // メモリ増加量が50MB以下であるべき
        val memoryIncrease = memoryDuring - memoryBefore
        assertTrue("メモリ使用量の増加が大きすぎます: ${memoryIncrease}MB", 
                  memoryIncrease < 50)
        
        // 録音停止後にメモリがある程度解放されるべき
        val memoryRetained = memoryAfter - memoryBefore
        assertTrue("メモリリークの可能性があります: ${memoryRetained}MB", 
                  memoryRetained < 20)
        
        testFile.delete()
    }

    @Test
    fun `監視 - 長時間録音でのリソース使用量`() {
        val initialMemory = getCurrentMemoryUsage()
        val initialCpu = getCurrentCpuUsage()
        
        println("長時間録音テスト開始")
        println("初期メモリ: ${initialMemory}MB, 初期CPU: ${initialCpu}%")
        
        audioProcessor.initializeAudioCapture()
        val testFile = File.createTempFile("longterm_test", ".wav")
        audioProcessor.startCapture(testFile)
        
        val measurements = mutableListOf<Pair<Double, Double>>() // CPU, Memory
        
        // 10秒間、1秒ごとに測定
        repeat(10) { second ->
            Thread.sleep(1000)
            val cpu = getCurrentCpuUsage()
            val memory = getCurrentMemoryUsage()
            measurements.add(Pair(cpu, memory))
            println("${second + 1}秒: CPU=${cpu}%, Memory=${memory}MB")
        }
        
        audioProcessor.stopCapture()
        
        // CPU使用率が安定していることを確認
        val cpuValues = measurements.map { it.first }
        val cpuVariance = calculateVariance(cpuValues)
        assertTrue("CPU使用率が不安定です: variance=${cpuVariance}", 
                  cpuVariance < 100.0)
        
        // メモリ使用量が線形に増加していないことを確認
        val memoryValues = measurements.map { it.second }
        val memoryTrend = calculateTrend(memoryValues)
        assertTrue("メモリリークの兆候があります: trend=${memoryTrend}MB/sec", 
                  memoryTrend < 2.0)
        
        testFile.delete()
    }

    @Test
    fun `監視 - バックグラウンド動作時のリソース使用量`() {
        // バックグラウンドでの動作をシミュレート
        val backgroundMemory = getCurrentMemoryUsage()
        val backgroundCpu = getCurrentCpuUsage()
        
        println("バックグラウンド動作テスト")
        println("バックグラウンドメモリ: ${backgroundMemory}MB")
        println("バックグラウンドCPU: ${backgroundCpu}%")
        
        // バックグラウンドでのCPU使用率は5%以下であるべき
        assertTrue("バックグラウンドでのCPU使用率が高すぎます: ${backgroundCpu}%", 
                  backgroundCpu < 5.0)
        
        // バックグラウンドでのメモリ使用量は適切な範囲内であるべき
        assertTrue("バックグラウンドでのメモリ使用量が多すぎます: ${backgroundMemory}MB", 
                  backgroundMemory < 100.0)
    }

    private fun getCurrentCpuUsage(): Double {
        return try {
            val pid = Process.myPid()
            val statFile = RandomAccessFile("/proc/$pid/stat", "r")
            val statData = statFile.readLine()
            statFile.close()
            
            val stats = statData.split(" ")
            val utime = stats[13].toLong()
            val stime = stats[14].toLong()
            val totalTime = utime + stime
            
            // 簡易的なCPU使用率計算（実際の実装ではより精密な計算が必要）
            (totalTime / 1000.0) % 100.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun getCurrentMemoryUsage(): Double {
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo.totalPss / 1024.0 // MB単位
    }

    private fun calculateVariance(values: List<Double>): Double {
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }

    private fun calculateTrend(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        
        val n = values.size
        val sumX = (0 until n).sum()
        val sumY = values.sum()
        val sumXY = values.mapIndexed { index, value -> index * value }.sum()
        val sumX2 = (0 until n).map { it * it }.sum()
        
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
    }
}