package com.callrecorder.pixel.receiver

import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.callrecorder.pixel.service.CallRecordingServiceImpl
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Integration tests for PhoneStateReceiver.
 * Tests call detection and automatic recording trigger functionality.
 */
@ExperimentalCoroutinesApi
class PhoneStateReceiverTest {

    @MockK
    private lateinit var mockContext: Context

    private lateinit var receiver: PhoneStateReceiver
    private val capturedIntents = mutableListOf<Intent>()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        
        receiver = PhoneStateReceiver()
        
        // Mock context.startForegroundService to capture intents
        every { mockContext.startForegroundService(capture(capturedIntents)) } returns null
        every { mockContext.startService(capture(capturedIntents)) } returns null
        
        capturedIntents.clear()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should detect incoming call and start recording on answer`() = runTest {
        // Arrange
        val phoneNumber = "+1234567890"
        
        // Simulate incoming call ringing
        val ringingIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, phoneNumber)
        }
        
        // Simulate call answered (off hook)
        val offHookIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK)
        }

        // Act
        receiver.onReceive(mockContext, ringingIntent)
        receiver.onReceive(mockContext, offHookIntent)

        // Assert
        assertEquals("Should have started recording service", 1, capturedIntents.size)
        
        val serviceIntent = capturedIntents[0]
        assertEquals("Should target CallRecordingServiceImpl", 
            CallRecordingServiceImpl::class.java.name, 
            serviceIntent.component?.className)
        assertEquals("Should have start recording action", 
            CallRecordingServiceImpl.ACTION_START_RECORDING, 
            serviceIntent.action)
        
        val callInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            serviceIntent.getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO, 
                com.callrecorder.pixel.data.model.CallInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            serviceIntent.getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO)
        }
        
        assertNotNull("Should include call info", callInfo)
        assertEquals("Should have correct phone number", phoneNumber, callInfo?.phoneNumber)
        assertTrue("Should be marked as incoming", callInfo?.isIncoming == true)
    }

    @Test
    fun `should detect outgoing call and start recording when connected`() = runTest {
        // Arrange
        val phoneNumber = "+0987654321"
        
        // Simulate outgoing call
        val outgoingIntent = Intent(Intent.ACTION_NEW_OUTGOING_CALL).apply {
            putExtra(Intent.EXTRA_PHONE_NUMBER, phoneNumber)
        }
        
        // Simulate call connected (off hook from idle)
        val offHookIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK)
        }

        // Act
        receiver.onReceive(mockContext, outgoingIntent)
        receiver.onReceive(mockContext, offHookIntent)

        // Assert
        assertEquals("Should have started recording service", 1, capturedIntents.size)
        
        val serviceIntent = capturedIntents[0]
        assertEquals("Should have start recording action", 
            CallRecordingServiceImpl.ACTION_START_RECORDING, 
            serviceIntent.action)
        
        val callInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            serviceIntent.getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO, 
                com.callrecorder.pixel.data.model.CallInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            serviceIntent.getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO)
        }
        
        assertNotNull("Should include call info", callInfo)
        assertEquals("Should have correct phone number", phoneNumber, callInfo?.phoneNumber)
        assertFalse("Should be marked as outgoing", callInfo?.isIncoming == true)
    }

    @Test
    fun `should stop recording when call ends`() = runTest {
        // Arrange - simulate active call first
        val phoneNumber = "+1234567890"
        
        val ringingIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, phoneNumber)
        }
        
        val offHookIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK)
        }
        
        val idleIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE)
        }

        // Act
        receiver.onReceive(mockContext, ringingIntent)
        receiver.onReceive(mockContext, offHookIntent)
        capturedIntents.clear() // Clear start recording intent
        receiver.onReceive(mockContext, idleIntent)

        // Assert
        assertEquals("Should have sent stop recording command", 1, capturedIntents.size)
        
        val serviceIntent = capturedIntents[0]
        assertEquals("Should have stop recording action", 
            CallRecordingServiceImpl.ACTION_STOP_RECORDING, 
            serviceIntent.action)
    }

    @Test
    fun `should not start recording for missed calls`() = runTest {
        // Arrange
        val phoneNumber = "+1234567890"
        
        // Simulate incoming call that goes to idle without being answered
        val ringingIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, phoneNumber)
        }
        
        val idleIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE)
        }

        // Act
        receiver.onReceive(mockContext, ringingIntent)
        receiver.onReceive(mockContext, idleIntent)

        // Assert
        assertEquals("Should not have started recording for missed call", 0, capturedIntents.size)
    }

    @Test
    fun `should handle multiple consecutive calls correctly`() = runTest {
        // Arrange
        val firstNumber = "+1111111111"
        val secondNumber = "+2222222222"

        // First call - complete cycle
        val firstRinging = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, firstNumber)
        }
        
        val firstOffHook = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK)
        }
        
        val firstIdle = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE)
        }

        // Second call - complete cycle
        val secondRinging = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, secondNumber)
        }
        
        val secondOffHook = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK)
        }
        
        val secondIdle = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_IDLE)
        }

        // Act
        receiver.onReceive(mockContext, firstRinging)
        receiver.onReceive(mockContext, firstOffHook)
        receiver.onReceive(mockContext, firstIdle)
        
        receiver.onReceive(mockContext, secondRinging)
        receiver.onReceive(mockContext, secondOffHook)
        receiver.onReceive(mockContext, secondIdle)

        // Assert
        assertEquals("Should have 4 service calls (2 start, 2 stop)", 4, capturedIntents.size)
        
        // Verify first call
        assertEquals("First intent should start recording", 
            CallRecordingServiceImpl.ACTION_START_RECORDING, 
            capturedIntents[0].action)
        assertEquals("Second intent should stop recording", 
            CallRecordingServiceImpl.ACTION_STOP_RECORDING, 
            capturedIntents[1].action)
        
        // Verify second call
        assertEquals("Third intent should start recording", 
            CallRecordingServiceImpl.ACTION_START_RECORDING, 
            capturedIntents[2].action)
        assertEquals("Fourth intent should stop recording", 
            CallRecordingServiceImpl.ACTION_STOP_RECORDING, 
            capturedIntents[3].action)
        
        // Verify phone numbers
        val firstCallInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            capturedIntents[0].getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO, 
                com.callrecorder.pixel.data.model.CallInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            capturedIntents[0].getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO)
        }
        
        val secondCallInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            capturedIntents[2].getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO, 
                com.callrecorder.pixel.data.model.CallInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            capturedIntents[2].getParcelableExtra(CallRecordingServiceImpl.EXTRA_CALL_INFO)
        }
        
        assertEquals("First call should have correct number", firstNumber, firstCallInfo?.phoneNumber)
        assertEquals("Second call should have correct number", secondNumber, secondCallInfo?.phoneNumber)
    }

    @Test
    fun `should handle null context gracefully`() {
        // Arrange
        val intent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, "+1234567890")
        }

        // Act & Assert - should not throw exception
        receiver.onReceive(null, intent)
        
        // No service calls should be made
        assertEquals("Should not make any service calls with null context", 0, capturedIntents.size)
    }

    @Test
    fun `should handle null intent gracefully`() {
        // Act & Assert - should not throw exception
        receiver.onReceive(mockContext, null)
        
        // No service calls should be made
        assertEquals("Should not make any service calls with null intent", 0, capturedIntents.size)
    }

    @Test
    fun `should handle unknown phone state gracefully`() = runTest {
        // Arrange
        val unknownStateIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, "UNKNOWN_STATE")
            putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, "+1234567890")
        }

        // Act
        receiver.onReceive(mockContext, unknownStateIntent)

        // Assert - should not crash and not start recording
        assertEquals("Should not start recording for unknown state", 0, capturedIntents.size)
    }

    @Test
    fun `should handle outgoing call without phone number`() = runTest {
        // Arrange
        val outgoingIntent = Intent(Intent.ACTION_NEW_OUTGOING_CALL)
        // No phone number extra
        
        val offHookIntent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED).apply {
            putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_OFFHOOK)
        }

        // Act
        receiver.onReceive(mockContext, outgoingIntent)
        receiver.onReceive(mockContext, offHookIntent)

        // Assert - should not start recording without phone number
        assertEquals("Should not start recording without phone number", 0, capturedIntents.size)
    }
}