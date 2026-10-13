package dev.anilbeesetti.nextplayer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.common.Logger
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.crash.CrashActivity
import dev.anilbeesetti.nextplayer.crash.GlobalExceptionHandler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class NextPlayerApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var networkConnectionRepository: NetworkConnectionRepository

    @Inject
    lateinit var sshKeyStore: SshKeyStore

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))
        registerActivityLifecycleCallbacks(SecureWindowCallbacks)
        applicationScope.launch {
            runCatching {
                initializeSshKeyStore(networkConnectionRepository, sshKeyStore)
            }.onFailure { error ->
                Logger.logError(TAG, "Couldn't reconcile SSH keys: ${error.message}")
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    private companion object {
        const val TAG = "NextPlayerApplication"
    }
}

/**
 * Marks every activity window as secure so the app can't be screenshotted or screen recorded
 * and its content is hidden in the recent apps screen.
 */
private object SecureWindowCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
