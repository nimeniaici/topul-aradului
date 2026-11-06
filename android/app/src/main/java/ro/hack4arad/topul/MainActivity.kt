package ro.hack4arad.topul

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ro.hack4arad.topul.ui.AppRoot
import ro.hack4arad.topul.ui.theme.TopulTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // aplicația e mereu întunecată -> iconițe albe în status bar
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.parseColor("#0A0C1C")),
        )
        setContent {
            TopulTheme { AppRoot() }
        }
    }
}
