package app.peertube.tv

import android.app.Application
import android.util.Log
import com.google.mlkit.common.sdkinternal.MlKitContext

class PeerTubeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize ML Kit explicitly as a fallback for TV firmware that does not
        // reliably run its manifest initializer before the first translation request.
        runCatching { MlKitContext.initializeIfNeeded(this) }
            .onFailure { Log.e("PeerTubeTV", "Could not initialize ML Kit", it) }
    }
}
