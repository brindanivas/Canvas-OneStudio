package com.hbcugo.tv.integration

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.aellayer.TimestampProvider
import com.example.aellayer.VideoInterruptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Simple integration helper for adding video interruption functionality
 * to any existing ExoPlayer implementation
 */
class VideoInterruptionIntegration(
    private val context: Context,
    private val player: ExoPlayer,
    private val parentContainer: FrameLayout
) {
    
    private var videoInterruptionManager: VideoInterruptionManager? = null
    private val positionCheckHandler = Handler(Looper.getMainLooper())
    private var positionCheckRunnable: Runnable? = null
    private var isSeekRestricted = false
    private var restrictedPosition = 0L
    
    interface InterruptionListener {
        fun onFormSubmitted()
        fun onFormSkipped(newPosition: Long)
        fun onTimestampReached(timestamp: String)
    }
    
    private var listener: InterruptionListener? = null
    
    fun setListener(listener: InterruptionListener) {
        this.listener = listener
    }
    
    /**
     * Initialize the video interruption system with video ID
     */
    fun initialize(videoId: String) {
        
        // Initialize the interruption manager
        videoInterruptionManager = VideoInterruptionManager(
            context = context,
            player = player,
            parentView = parentContainer
        )
        
        // Set up callback for form interactions
        videoInterruptionManager?.setCallback(object : VideoInterruptionManager.InterruptionCallback {
            override fun onFormSubmitted() {
                listener?.onFormSubmitted()
            }
            
            override fun onFormSkipped(newPosition: Long) {
                restrictedPosition = newPosition
                isSeekRestricted = true
                listener?.onFormSkipped(newPosition)
            }
        })
        
        // Initialize with video ID and fetch lock data
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                videoInterruptionManager?.initializeWithVideoId(videoId)
            } catch (e: Exception) {
                // Silent error handling - fallback will be used
            }
        }
        
        // Add player listener for seek restriction and state management
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                
                // Check if user is seeking to a locked position
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    // Check for seek interruption (form should appear at locked segments)
                    videoInterruptionManager?.checkForSeekInterruption(newPosition.positionMs)
                    
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                
                when (playbackState) {
                    Player.STATE_READY -> {
                        startPositionChecking()
                    }
                    Player.STATE_ENDED -> {
                        stopPositionChecking()
                        reset()
                    }
                }
            }
        })
    }
    
    /**
     * Start checking video position for timestamp interruptions
     */
    private fun startPositionChecking() {
        stopPositionChecking() // Ensure no duplicate runnables
        
        positionCheckRunnable = object : Runnable {
            override fun run() {
                // Only check if player is playing AND no form is currently showing
                if (player.isPlaying && videoInterruptionManager?.getFormShowingStatus() != true) {
                    val currentPosition = player.currentPosition
                    
                    // Check for timestamp interruptions
                    videoInterruptionManager?.checkForInterruption(currentPosition)
                     videoInterruptionManager?.checkForSeekInterruption(currentPosition)
                }
                
                // Schedule next check in 1 second
                positionCheckHandler.postDelayed(this, 1000)
            }
        }
        
        positionCheckRunnable?.let { runnable ->
            positionCheckHandler.post(runnable)
        }
    }
    
    /**
     * Stop checking video position
     */
    private fun stopPositionChecking() {
        positionCheckRunnable?.let { runnable ->
            positionCheckHandler.removeCallbacks(runnable)
        }
        positionCheckRunnable = null
    }
    
    /**
     * Reset interruption state for new video
     */
    fun reset() {
        isSeekRestricted = false
        restrictedPosition = 0L
        videoInterruptionManager?.reset()
    }
    
    /**
     * Check if seeking to a specific position is allowed
     */
    fun isSeekAllowed(seekPosition: Long): Boolean {
        return videoInterruptionManager?.isSeekAllowed(seekPosition) ?: true
    }
    
    /**
     * Clean up resources
     */
    fun release() {
        stopPositionChecking()
        videoInterruptionManager?.release()
        listener = null
    }
    
    /**
     * Pause position checking (call in onPause)
     */
    fun pause() {
        stopPositionChecking()
    }
    
    /**
     * Resume position checking (call in onResume)
     */
    fun resume() {
        if (player.playbackState == Player.STATE_READY) {
            startPositionChecking()
        }
    }
}