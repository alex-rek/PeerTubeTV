package app.peertube.tv

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ServerDiscoveryJobService : JobService() {
    private var jobScope: CoroutineScope? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        jobScope = scope
        scope.launch {
            try {
                val prefs = getSharedPreferences("peertube", MODE_PRIVATE)
                val server = prefs.getString("server", "https://peertube.tv") ?: "https://peertube.tv"
                val repository = ServerRepository(applicationContext)
                repository.seedBundled(applicationContext)
                ServerDiscoveryManager(applicationContext, repository).discover(server)
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {
                // A transient directory or federation failure is retried at the next scheduled interval.
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobScope?.cancel()
        jobScope = null
        return true
    }
}
