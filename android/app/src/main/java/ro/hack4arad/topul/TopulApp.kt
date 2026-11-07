package ro.hack4arad.topul

import android.app.Application
import ro.hack4arad.topul.data.CrashReporter
import ro.hack4arad.topul.ui.theme.ThemeState
import ro.hack4arad.topul.ui.theme.LangState
import ro.hack4arad.topul.data.Network
import ro.hack4arad.topul.data.PlacePhotos

class TopulApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeState.init(this)
        LangState.init(this)
        Network.init(this)
        CrashReporter.install(this)
        PlacePhotos.init(this)
    }
}
