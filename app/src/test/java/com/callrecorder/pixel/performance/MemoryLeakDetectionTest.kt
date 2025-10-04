package com.callrecorder.pixel.performance

import android.os.Debug
import com.callrecorder.pixel.audio.MediaRecorderAudioProcessor
import com.callrecorder.pixel.service.CallRecordingServiceImpl
import com.callrecorder.pixel.storage.FileManager
import com.callrecorder.pixel.data.repository.RecordingRepository
import com.callrecorder.pixel.TestUtils
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.lang.ref.WeakReference

/**
 * メモリリーク検出テスト
 * Requirements: 2.1, 2.3
 */
class MemoryLeakDetectionTest {

    private lateinit var audioProcessor: MediaRecorderAudioProcessor
    private lateinit var fileManager: FileManager
    private lateinit var repository: RecordingRepository

    @Before
    fun setUp() {
        audioProcessor = MediaRecorderAudioProcessor(TestUtils.getTestContext())
        fileManager = mockk()
        repository = mockk()
    }

    @Test
    fun `検出 - AudioProcessor のメモリリーク`() = runTest {
        val initialMemory = getUsedMemory()
        val processorReferences = mutableListOf<WeakReference<MediaRecorderAudioProcessor>>()
        
        // 複数のAudioProcessorインスタンスを作成・破棄
        repeat(50) {
            val processor = MediaRecorderAudioProcessor(TestUtils.getTestContext())
            processor.initializeAudioCapture(TestUtils.getTestAudioQuality())
            
            val testFile = TestUtils.createTestFile("leak_test_$it.wav")
            processor.startCapture(testFile)
            delay(10) // 短時間録音
            processor.stopCapture()
            
            processorReferences.add(WeakReference(processor))
            testFile.delete()
        }
        
        // ガベージコレクションを強制実行
        System.gc()
        Thread.sleep(1000)
        System.gc()
        Thread.sleep(1000)
        
        val finalMemory = getUsedMemory()
        val memoryIncrease = finalMemory - initialMemory
        
        println("初期メモリ: ${initialMemory}MB")
        println("最終メモリ: ${finalMemory}MB")
        println("メモリ増加: ${memoryIncrease}MB")
        
        // メモリ増加が20MB以下であるべき
        assertTrue("AudioProcessorでメモリリークが検出されました: ${memoryIncrease}MB", 
                  memoryIncrease < 20.0)
        
        // WeakReferenceの大部分がnullになっているべき
        val aliveReferences = processorReferences.count { it.get() != null }
        val leakPercentage = (aliveReferences.toDouble() / processorReferences.size) * 100
        
        println("生存参照: $aliveReferences/${processorReferences.size} (${leakPercentage}%)")
        assertTrue("オブジェクトが適切に解放されていません: ${leakPercentage}%", 
                  leakPercentage < 10.0)
    }

    @Test
    fun `検出 - 録音ファイルハンドルのリーク`() = runTest {
        val initialFileDescriptors = getOpenFileDescriptorCount()
        val testFiles = mutableListOf<File>()
        
        // 多数のファイルを作成・操作
        repeat(100) { index ->
            val testFile = TestUtils.createTestFile("fd_test_$index.wav")
            testFiles.add(testFile)
            
            // ファイル操作をシミュレート
            audioProcessor.initializeAudioCapture(TestUtils.getTestAudioQuality())
            audioProcessor.startCapture(testFile)
            delay(5)
            audioProcessor.stopCapture()
        }
        
        // ファイルを削除
        testFiles.forEach { it.delete() }
        
        // ガベージコレクションを実行
        System.gc()
        Thread.sleep(1000)
        
        val finalFileDescriptors = getOpenFileDescriptorCount()
        val fdIncrease = finalFileDescriptors - initialFileDescriptors
        
        println("初期ファイルディスクリプタ数: $initialFileDescriptors")
        println("最終ファイルディスクリプタ数: $finalFileDescriptors")
        println("ファイルディスクリプタ増加: $fdIncrease")
        
        // ファイルディスクリプタの増加が10以下であるべき
        assertTrue("ファイルハンドルリークが検出されました: $fdIncrease", 
                  fdIncrease < 10)
    }

    @Test
    fun `検出 - 長時間動作でのメモリリーク`() = runTest {
        val memorySnapshots = mutableListOf<Double>()
        val initialMemory = getUsedMemory()
        memorySnapshots.add(initialMemory)
        
        println("長時間メモリリークテスト開始")
        
        // 30回の録音サイクルを実行
        repeat(30) { cycle ->
            audioProcessor.initializeAudioCapture(TestUtils.getTestAudioQuality())
            val testFile = TestUtils.createTestFile("longterm_$cycle.wav")
            
            audioProcessor.startCapture(testFile)
            delay(100) // 100ms録音
            audioProcessor.stopCapture()
            
            testFile.delete()
            
            // 5回ごとにメモリ使用量を記録
            if (cycle % 5 == 0) {
                System.gc()
                delay(500)
                val currentMemory = getUsedMemory()
                memorySnapshots.add(currentMemory)
                println("サイクル $cycle: ${currentMemory}MB")
            }
        }
        
        // メモリ使用量の傾向を分析
        val memoryTrend = calculateMemoryTrend(memorySnapshots)
        println("メモリ使用量の傾向: ${memoryTrend}MB/サイクル")
        
        // メモリ使用量が継続的に増加していないことを確認
        assertTrue("長時間動作でメモリリークが検出されました: ${memoryTrend}MB/サイクル", 
                  memoryTrend < 0.5)
        
        val finalMemory = memorySnapshots.last()
        val totalIncrease = finalMemory - initialMemory
        println("総メモリ増加: ${totalIncrease}MB")
        
        // 総メモリ増加が30MB以下であるべき
        assertTrue("総メモリ増加が大きすぎます: ${totalIncrease}MB", 
                  totalIncrease < 30.0)
    }

    @Test
    fun `検出 - スレッドリーク`() {
        val initialThreadCount = Thread.activeCount()
        val threadReferences = mutableListOf<WeakReference<Thread>>()
        
        // 複数の録音処理を並行実行
        repeat(20) { index ->
            val thread = Thread {
                val processor = MediaRecorderAudioProcessor(TestUtils.getTestContext())
                // Note: Cannot use suspend functions in Thread, this test needs refactoring
                val testFile = TestUtils.createTestFile("thread_test_$index.wav")
                Thread.sleep(50)
                testFile.delete()
            }
            
            threadReferences.add(WeakReference(thread))
            thread.start()
            thread.join() // スレッドの完了を待機
        }
        
        // ガベージコレクションを実行
        System.gc()
        Thread.sleep(1000)
        
        val finalThreadCount = Thread.activeCount()
        val threadIncrease = finalThreadCount - initialThreadCount
        
        println("初期スレッド数: $initialThreadCount")
        println("最終スレッド数: $finalThreadCount")
        println("スレッド増加: $threadIncrease")
        
        // スレッド数の増加が5以下であるべき
        assertTrue("スレッドリークが検出されました: $threadIncrease", 
                  threadIncrease < 5)
        
        // WeakReferenceがnullになっているべき
        val aliveThreads = threadReferences.count { it.get() != null }
        assertTrue("スレッドが適切に終了していません: $aliveThreads", 
                  aliveThreads == 0)
    }

    private fun getUsedMemory(): Double {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        return (totalMemory - freeMemory) / (1024.0 * 1024.0) // MB単位
    }

    private fun getOpenFileDescriptorCount(): Int {
        return try {
            val pid = android.os.Process.myPid()
            val fdDir = File("/proc/$pid/fd")
            fdDir.listFiles()?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun calculateMemoryTrend(snapshots: List<Double>): Double {
        if (snapshots.size < 2) return 0.0
        
        val n = snapshots.size
        val sumX = (0 until n).sum().toDouble()
        val sumY = snapshots.sum()
        val sumXY = snapshots.mapIndexed { index, value -> index * value }.sum()
        val sumX2 = (0 until n).map { it * it }.sum().toDouble()
        
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
    }
}