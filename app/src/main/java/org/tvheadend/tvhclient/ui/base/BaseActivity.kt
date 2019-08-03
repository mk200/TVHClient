package org.tvheadend.tvhclient.ui.base

import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.tvheadend.tvhclient.MainApplication
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.data.repository.AppRepository
import org.tvheadend.tvhclient.ui.common.NetworkStatusReceiver
import org.tvheadend.tvhclient.ui.common.SnackbarMessageReceiver
import org.tvheadend.tvhclient.ui.common.callbacks.ToolbarInterface
import org.tvheadend.tvhclient.ui.common.onAttach
import org.tvheadend.tvhclient.util.getThemeId
import javax.inject.Inject

open class BaseActivity(private val layoutId: Int = R.layout.misc_content_activity) : AppCompatActivity(), ToolbarInterface {

    @Inject
    lateinit var appContext: Context
    @Inject
    lateinit var appRepository: AppRepository
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    protected lateinit var baseViewModel: BaseViewModel
    private lateinit var snackbarMessageReceiver: SnackbarMessageReceiver
    private lateinit var networkStatusReceiver: NetworkStatusReceiver
    protected lateinit var toolbar: Toolbar

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getThemeId(this))
        super.onCreate(savedInstanceState)
        setContentView(layoutId)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        MainApplication.component.inject(this)

        baseViewModel = ViewModelProviders.of(this).get(BaseViewModel::class.java)
        snackbarMessageReceiver = SnackbarMessageReceiver(appRepository)
        networkStatusReceiver = NetworkStatusReceiver(appRepository)
    }

    public override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(snackbarMessageReceiver, IntentFilter(SnackbarMessageReceiver.SNACKBAR_ACTION))
        registerReceiver(networkStatusReceiver, IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"))
    }

    public override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(snackbarMessageReceiver)
        unregisterReceiver(networkStatusReceiver)
    }

    override fun attachBaseContext(context: Context) {
        super.attachBaseContext(onAttach(context))
    }

    override fun setTitle(title: String) {
        supportActionBar?.title = title
    }

    override fun setSubtitle(subtitle: String) {
        supportActionBar?.subtitle = subtitle
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
