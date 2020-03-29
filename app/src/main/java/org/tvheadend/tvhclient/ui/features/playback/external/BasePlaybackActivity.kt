package org.tvheadend.tvhclient.ui.features.playback.external

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.android.synthetic.main.play_activity.*
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.ui.common.onAttach
import org.tvheadend.tvhclient.util.extensions.gone
import org.tvheadend.tvhclient.util.getThemeId
import timber.log.Timber

abstract class BasePlaybackActivity : AppCompatActivity() {

    lateinit var viewModel: ExternalPlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getThemeId(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.play_activity)

        status.setText(R.string.connecting_to_server)

        viewModel = ViewModelProviders.of(this).get(ExternalPlayerViewModel::class.java)

        viewModel.isConnected.observe(this, Observer { isConnected ->
            if (isConnected) {
                Timber.d("Received live data, connected to server, requesting ticket")
                status.setText(R.string.requesting_playback_information)
                viewModel.requestTicketFromServer(intent.extras)
            } else {
                Timber.d("Received live data, not connected to server")
                progress_bar.gone()
                status.setText(R.string.connection_failed)
            }
        })

        viewModel.isTicketReceived.observe(this, Observer { isTicketReceived ->
            Timber.d("Received ticket $isTicketReceived")
            if (isTicketReceived) {
                progress_bar.gone()
                status.text = getString(R.string.starting_playback)
                onTicketReceived()
            }
        })
    }

    override fun attachBaseContext(context: Context) {
        super.attachBaseContext(onAttach(context))
    }

    protected abstract fun onTicketReceived()

    internal fun startExternalPlayer(intent: Intent) {
        Timber.d("Starting external player for given intent")
        // Start playing the video in the UI thread
        this.runOnUiThread {
            Timber.d("Getting list of activities that can play the intent")
            val activities: List<ResolveInfo> = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (activities.isNotEmpty()) {
                Timber.d("Found activities, starting external player")
                startActivity(intent)
                finish()
            } else {
                Timber.d("List of available activities is empty, can't start external media player")
                status.setText(R.string.no_media_player)

                // Show a confirmation dialog before deleting the recording
                MaterialDialog(this@BasePlaybackActivity).show {
                    title(R.string.no_media_player)
                    message(R.string.show_play_store)
                    positiveButton(android.R.string.yes) {
                        try {
                            Timber.d("Opening play store to download external players")
                            val installIntent = Intent(Intent.ACTION_VIEW)
                            installIntent.data = Uri.parse("market://search?q=free%20video%20player&c=apps")
                            startActivity(installIntent)
                        } catch (t2: Throwable) {
                            Timber.d("Could not startPlayback google play store")
                        } finally {
                            finish()
                        }
                    }
                    negativeButton(android.R.string.no) { finish() }
                }
            }
        }
    }
}
