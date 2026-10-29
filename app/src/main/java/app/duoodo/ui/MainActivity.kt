package app.duoodo.ui

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import app.duoodo.sync.CheckWorker

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Doodles are private: keep them out of screenshots and the recent-apps thumbnail.
        // Debug builds skip this so the app can be screenshotted during development.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        CheckWorker.schedule(applicationContext)
        setContent {
            MaterialTheme { DuoodoScreen(viewModel) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkNow()
    }
}
