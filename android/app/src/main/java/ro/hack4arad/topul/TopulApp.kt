package ro.hack4arad.topul

import android.app.Application
import ro.hack4arad.topul.data.Network

class TopulApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Network.init(this)
    }
}
