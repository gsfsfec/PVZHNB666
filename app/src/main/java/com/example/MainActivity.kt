package com.example

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.BootScreen
import com.example.ui.QuantumTerminalScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.QuantumViewModel

class MainActivity : ComponentActivity() {
  private var mediaPlayer: MediaPlayer? = null
  private var isPrepared = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Auto play music on startup
    playBackgroundMusic()

    setContent {
      MyApplicationTheme {
        val viewModel: QuantumViewModel = viewModel()
        val bootCompleted by viewModel.bootCompleted.collectAsState()

        LaunchedEffect(Unit) {
          viewModel.autoInitSystemAndLoadCredentials()
        }

        Box(modifier = Modifier.fillMaxSize()) {
          Image(
            painter = painterResource(id = R.drawable.img_global_bg_1780052602908),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
          )

          if (!bootCompleted) {
            BootScreen(onBootFinished = { viewModel.setBootCompleted() })
          } else {
            QuantumTerminalScreen(viewModel = viewModel)
          }
        }
      }
    }
  }

  private fun playBackgroundMusic() {
    try {
      isPrepared = false
      mediaPlayer?.release()
      mediaPlayer = null
      mediaPlayer = MediaPlayer().apply {
        // Direct RAW url is more robust and standard for MediaPlayer than github.com html/redirect references
        setDataSource("https://raw.githubusercontent.com/gsfsfec/uyykbhhxf/main/%E5%BF%98%E8%AE%B0.mp3")
        setAudioAttributes(
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        )
        isLooping = true
        setOnPreparedListener { 
          isPrepared = true
          try {
            it.start() 
          } catch (e: Exception) {
            e.printStackTrace()
          }
        }
        setOnErrorListener { mp, what, extra ->
          android.util.Log.e("QuantumMusic", "MediaPlayer Error: what=$what, extra=$extra")
          isPrepared = false
          // Return true to indicate we handled the error and don't trigger default error handler
          true
        }
        prepareAsync()
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  override fun onResume() {
    super.onResume()
    try {
      val mp = mediaPlayer
      if (mp == null) {
        playBackgroundMusic()
      } else {
        if (isPrepared && !mp.isPlaying) {
          mp.start()
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      playBackgroundMusic() // fallback retry
    }
  }

  override fun onPause() {
    super.onPause()
    try {
      if (mediaPlayer?.isPlaying == true) {
        mediaPlayer?.pause()
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    try {
      mediaPlayer?.stop()
      mediaPlayer?.release()
      mediaPlayer = null
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}

