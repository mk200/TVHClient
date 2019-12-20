package org.tvheadend.tvhclient.ui.features

import android.app.ActivityManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.SearchRecentSuggestions
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.cast.framework.*
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.service.HtspService
import org.tvheadend.tvhclient.service.SyncStateReceiver
import org.tvheadend.tvhclient.ui.base.BaseActivity
import org.tvheadend.tvhclient.ui.common.*
import org.tvheadend.tvhclient.ui.common.interfaces.SearchRequestInterface
import org.tvheadend.tvhclient.ui.features.channels.ChannelListFragment
import org.tvheadend.tvhclient.ui.features.dvr.recordings.RecordingDetailsFragment
import org.tvheadend.tvhclient.ui.features.dvr.recordings.download.DownloadPermissionGrantedInterface
import org.tvheadend.tvhclient.ui.features.dvr.series_recordings.SeriesRecordingDetailsFragment
import org.tvheadend.tvhclient.ui.features.dvr.timer_recordings.TimerRecordingDetailsFragment
import org.tvheadend.tvhclient.ui.features.epg.EpgFragment
import org.tvheadend.tvhclient.ui.features.information.PrivacyPolicyFragment
import org.tvheadend.tvhclient.ui.features.information.StatusViewModel
import org.tvheadend.tvhclient.ui.features.navigation.NavigationDrawer
import org.tvheadend.tvhclient.ui.features.navigation.NavigationDrawer.Companion.MENU_SETTINGS
import org.tvheadend.tvhclient.ui.features.navigation.NavigationViewModel
import org.tvheadend.tvhclient.ui.features.playback.external.CastSessionManagerListener
import org.tvheadend.tvhclient.ui.features.programs.ProgramDetailsFragment
import org.tvheadend.tvhclient.ui.features.programs.ProgramListFragment
import org.tvheadend.tvhclient.ui.features.settings.SettingsActivity
import org.tvheadend.tvhclient.util.extensions.*
import timber.log.Timber

class MainActivity : BaseActivity(R.layout.main_activity), SearchView.OnQueryTextListener, SearchView.OnSuggestionListener, SyncStateReceiver.Listener {

    private lateinit var navigationViewModel: NavigationViewModel
    private lateinit var statusViewModel: StatusViewModel

    private lateinit var syncProgress: ProgressBar

    private var searchMenuItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var mediaRouteMenuItem: MenuItem? = null
    private var introductoryOverlay: IntroductoryOverlay? = null
    private var castSession: CastSession? = null
    private var castContext: CastContext? = null
    private var castStateListener: CastStateListener? = null
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null

    private lateinit var navigationDrawer: NavigationDrawer
    private lateinit var syncStateReceiver: SyncStateReceiver

    private var isUnlocked: Boolean = false
    private var isDualPane: Boolean = false

    private lateinit var queryTextSubmitTask: Runnable
    private val delayedQueryTextSubmitHandler = Handler()

    private lateinit var miniController: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        navigationViewModel = ViewModelProviders.of(this).get(NavigationViewModel::class.java)
        statusViewModel = ViewModelProviders.of(this).get(StatusViewModel::class.java)

        // Reset the search in case the main activity was called for the first
        // time or when we came back from another like the search activity
        if (savedInstanceState == null) {
            baseViewModel.clearSearchQuery()
            baseViewModel.removeFragmentWhenSearchIsDone = false
        }

        syncProgress = findViewById(R.id.sync_progress)
        syncStateReceiver = SyncStateReceiver(this)
        isDualPane = findViewById<View>(R.id.details) != null

        miniController = findViewById(R.id.cast_mini_controller)
        miniController.gone()

        navigationDrawer = NavigationDrawer(this, savedInstanceState, toolbar, navigationViewModel, statusViewModel)

        supportFragmentManager.addOnBackStackChangedListener {
            navigationDrawer.handleMenuSelection(supportFragmentManager.findFragmentById(R.id.main))
        }

        castContext = this.getCastContext()
        if (castContext != null) {
            Timber.d("Casting is available")
            castSessionManagerListener = CastSessionManagerListener(this, castSession)
            castStateListener = CastStateListener { newState ->
                Timber.d("Cast state changed to $newState")
                if (newState != CastState.NO_DEVICES_AVAILABLE) {
                    showIntroductoryOverlay()
                }
            }
        } else {
            Timber.d("Casting is not available, casting will no be enabled")
        }

        // Calls the method in the fragments that will initiate the actual search
        // Disable search as you type in the epg because the results
        // will be shown in a separate fragment program list.
        queryTextSubmitTask = Runnable {
            Timber.d("Delayed search timer elapsed, starting search")
        }

        // Observe any changes in the network availability. If the app is in the background
        // and is resumed and the network is still available the lambda function is not
        // called and nothing will be done.
        baseViewModel.networkStatus.observe(this, Observer { status ->
            Timber.d("Network status changed to $status")
            connectToServer(status)
        })

        baseViewModel.connectionToServerAvailable.observe(this, Observer { isAvailable ->
            Timber.d("Connection to server availability changed to $isAvailable")
            invalidateOptionsMenu()
            if (isAvailable) {
                statusViewModel.startDiskSpaceUpdateHandler()
            } else {
                statusViewModel.stopDiskSpaceUpdateHandler()
            }
        })

        navigationViewModel.getNavigationMenuId().observe(this, Observer { event ->
            event.getContentIfNotHandled()?.let {
                Timber.d("Navigation menu id changed to $it")
                handleDrawerItemSelected(it)
            }
        })
        statusViewModel.showRunningRecordingCount.observe(this, Observer { show ->
            showOrCancelNotificationProgramIsCurrentlyBeingRecorded(this, statusViewModel.runningRecordingCount, show)
        })
        statusViewModel.showLowStorageSpace.observe(this, Observer { show ->
            showOrCancelNotificationDiskSpaceIsLow(this, statusViewModel.availableStorageSpace, show)
        })
        baseViewModel.showSnackbar.observe(this, Observer { event ->
            event.getContentIfNotHandled()?.let {
                this.showSnackbarMessage(it)
            }
        })
        baseViewModel.isUnlocked.observe(this, Observer { unlocked ->
            Timber.d("Received live data, unlocked changed to $unlocked")
            isUnlocked = unlocked
            invalidateOptionsMenu()
            miniController.visibleOrGone(isUnlocked && sharedPreferences.getBoolean("casting_minicontroller_enabled", resources.getBoolean(R.bool.pref_default_casting_minicontroller_enabled)))
        })

        Timber.d("Done initializing")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        var out = outState
        // add the values which need to be saved from the drawer and header to the bundle
        out = navigationDrawer.saveInstanceState(out)
        super.onSaveInstanceState(out)
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(syncStateReceiver, IntentFilter(SyncStateReceiver.ACTION))
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(syncStateReceiver)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        castContext?.let {
            return it.onDispatchVolumeKeyEventBeforeJellyBean(event) || super.dispatchKeyEvent(event)
        } ?: run {
            return super.dispatchKeyEvent(event)
        }
    }

    public override fun onResume() {
        super.onResume()
        castSession = this.getCastSession()
    }

    public override fun onPause() {
        castContext?.let {
            try {
                it.removeCastStateListener(castStateListener)
                it.sessionManager.removeSessionManagerListener(castSessionManagerListener, CastSession::class.java)
            } catch (e: IllegalStateException) {
                Timber.e(e, "Could not remove cast state listener or get cast session manager")
            }
        }
        delayedQueryTextSubmitHandler.removeCallbacks(queryTextSubmitTask)
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_options_menu, menu)

        mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item)
        try {
            CastButtonFactory.setUpMediaRouteButton(applicationContext, menu, R.id.media_route_menu_item)
        } catch (e: Exception) {
            Timber.e(e, "Could not setup media route button")
        }

        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.menu_search)
        searchView = searchMenuItem?.actionView as SearchView

        searchView?.let {
            it.setSearchableInfo(searchManager.getSearchableInfo(componentName))
            it.setIconifiedByDefault(true)
            it.setOnQueryTextListener(this)
            it.setOnSuggestionListener(this)

            val fragment = supportFragmentManager.findFragmentById(R.id.main)
            if (fragment is SearchRequestInterface && fragment.isVisible) {
                it.queryHint = fragment.getQueryHint()
            }
        }
        return true
    }

    private fun showIntroductoryOverlay() {
        introductoryOverlay?.remove()

        mediaRouteMenuItem?.let {
            if (it.isVisible) {
                Handler().post {
                    introductoryOverlay = IntroductoryOverlay.Builder(
                            this@MainActivity, mediaRouteMenuItem)
                            .setTitleText(getString(R.string.intro_overlay_text))
                            .setOverlayColor(R.color.primary)
                            .setSingleTime()
                            .setOnOverlayDismissedListener { introductoryOverlay = null }
                            .build()
                    introductoryOverlay?.show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && permissions.isNotEmpty()
                && permissions[0] == "android.permission.WRITE_EXTERNAL_STORAGE") {
            Timber.d("Storage permission granted")
            val fragment = supportFragmentManager.findFragmentById(if (isDualPane) R.id.details else R.id.main)
            if (fragment is DownloadPermissionGrantedInterface) {
                fragment.downloadRecording()
            }
        } else {
            Timber.d("Storage permission could not be granted")
        }
    }

    /**
     * Called when a menu item from the navigation drawer was selected. It loads
     * and shows the correct fragment or fragments depending on the selected
     * menu item.
     *
     * @param id Selected position within the menu array
     */
    private fun handleDrawerItemSelected(id: Int) {
        Timber.d("Handling new navigation menu id $id")

        if (id == MENU_SETTINGS) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        val addFragmentToBackStack = sharedPreferences.getBoolean("navigation_history_enabled",
                resources.getBoolean(R.bool.pref_default_navigation_history_enabled))

        // A new or existing main fragment shall be shown. So save the menu position so we
        // know which one was selected. Additionally remove any old details fragment in case
        // dual pane mode is active to prevent showing wrong details data.
        // Finally show the new main fragment and add it to the back stack
        // only if it is a new fragment and not an existing one.
        val fragment = navigationDrawer.getFragmentFromSelection(id)
        if (fragment != null) {
            if (isDualPane) {
                val detailsFragment = supportFragmentManager.findFragmentById(R.id.details)
                if (detailsFragment != null) {
                    supportFragmentManager.beginTransaction().remove(detailsFragment).commit()
                }
            }
            supportFragmentManager.beginTransaction().replace(R.id.main, fragment).let {
                if (addFragmentToBackStack) it.addToBackStack(null)
                it.commit()
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)

        when (navigationViewModel.currentNavigationMenuId) {
            NavigationDrawer.MENU_UNLOCKER,
            NavigationDrawer.MENU_HELP -> {
                menu.findItem(R.id.media_route_menu_item)?.isVisible = false
                menu.findItem(R.id.menu_search).isVisible = false
                menu.findItem(R.id.menu_reconnect_to_server).isVisible = false
                menu.findItem(R.id.menu_privacy_policy).isVisible = false
                menu.findItem(R.id.menu_send_wake_on_lan_packet)?.isVisible = false
            }
            NavigationDrawer.MENU_STATUS -> {
                menu.findItem(R.id.media_route_menu_item)?.isVisible = false
                menu.findItem(R.id.menu_search).isVisible = false
                menu.findItem(R.id.menu_send_wake_on_lan_packet)?.isVisible = isUnlocked && baseViewModel.connection.isWolEnabled
            }
            else -> {
                menu.findItem(R.id.media_route_menu_item)?.isVisible = isUnlocked
                menu.findItem(R.id.menu_send_wake_on_lan_packet)?.isVisible = isUnlocked && baseViewModel.connection.isWolEnabled
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_privacy_policy -> {
                Timber.d("Showing privacy policy fragment")
                val fragment: Fragment = PrivacyPolicyFragment()
                supportFragmentManager.beginTransaction()
                        .replace(R.id.main, fragment)
                        .addToBackStack(null)
                        .commit()
                true
            }
            R.id.menu_reconnect_to_server -> showConfirmationToReconnectToServer(this, baseViewModel)
            R.id.menu_send_wake_on_lan_packet -> {
                WakeOnLanTask(this, baseViewModel.connection).execute()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        Timber.d("Search string $query was entered")
        delayedQueryTextSubmitHandler.removeCallbacks(queryTextSubmitTask)
        searchMenuItem?.collapseActionView()

        // Save the entered query so it will be shown later in the suggestion drpo down list
        val suggestions = SearchRecentSuggestions(this, SuggestionProvider.AUTHORITY, SuggestionProvider.MODE)
        suggestions.saveRecentQuery(query, null)

        // In case the channels or epg is currently visible, show the program list fragment.
        // It observes the search query and will perform the search and show the results.
        val fragment = supportFragmentManager.findFragmentById(R.id.main)
        if (fragment is ChannelListFragment || fragment is EpgFragment) {
            Timber.d("Adding program list fragment where the search will be done")
            val newFragment: Fragment = ProgramListFragment.newInstance()
            supportFragmentManager.beginTransaction().replace(R.id.main, newFragment).let {
                it.addToBackStack(null)
                it.commit()
            }
            baseViewModel.removeFragmentWhenSearchIsDone = true
        }

        Timber.d("Submitting search query to the view model")
        baseViewModel.startSearchQuery(query)
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        Timber.d("Search query changed to $newText")
        delayedQueryTextSubmitHandler.removeCallbacks(queryTextSubmitTask)

        val fragment = supportFragmentManager.findFragmentById(R.id.main)
        if (fragment !is ChannelListFragment && fragment !is EpgFragment) {
            if (newText.length >= 3) {
                Timber.d("Search query is ${newText.length} characters long, starting timer to start searching")
                delayedQueryTextSubmitHandler.postDelayed(queryTextSubmitTask, 2000)
            }
        }
        return true
    }

    override fun onSuggestionSelect(position: Int): Boolean {
        return false
    }

    override fun onSuggestionClick(position: Int): Boolean {
        delayedQueryTextSubmitHandler.removeCallbacks(queryTextSubmitTask)
        searchMenuItem?.collapseActionView()

        val cursor = searchView?.suggestionsAdapter?.getItem(position) as Cursor?
        cursor?.let {
            val suggestion = cursor.getString(cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1))
            searchView?.setQuery(suggestion, true)
        }
        return true
    }

    override fun onSyncStateChanged(state: SyncStateReceiver.State, message: String, details: String) {
        when (state) {
            SyncStateReceiver.State.CLOSED, SyncStateReceiver.State.FAILED -> {
                Timber.d("Connection failed or closed")
                sendSnackbarMessage(message)
                Timber.d("Setting connection to server not available")
                appRepository.setConnectionToServerAvailable(false)
            }
            SyncStateReceiver.State.CONNECTING -> {
                Timber.d("Connecting")
                sendSnackbarMessage(message)
            }
            SyncStateReceiver.State.CONNECTED -> {
                Timber.d("Connected")
                sendSnackbarMessage(message)
                appRepository.setConnectionToServerAvailable(true)
            }
            SyncStateReceiver.State.SYNC_STARTED -> {
                Timber.d("Sync started, showing progress bar")
                syncProgress.visible()
                sendSnackbarMessage(message)
            }
            SyncStateReceiver.State.SYNC_IN_PROGRESS -> {
                Timber.d("Sync in progress, updating progress bar")
                syncProgress.visible()
            }
            SyncStateReceiver.State.SYNC_DONE -> {
                Timber.d("Sync done, hiding progress bar")
                syncProgress.gone()
                sendSnackbarMessage(message)
            }
        }
    }

    private fun connectToServer(status: NetworkStatus) {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcessInfo = activityManager.runningAppProcesses?.get(0)
        val intent = Intent(this, HtspService::class.java)

        if (runningAppProcessInfo != null
                && runningAppProcessInfo.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {

            when (status) {
                NetworkStatus.NETWORK_IS_UP -> {
                    Timber.d("Connecting to server because network is up again")
                    intent.action = "connect"
                    startService(intent)
                }
                NetworkStatus.NETWORK_IS_STILL_UP -> {
                    Timber.d("Reconnecting to server because network is still up")
                    intent.action = "reconnect"
                    startService(intent)
                }
                NetworkStatus.NETWORK_IS_DOWN -> {
                    Timber.d("Disconnecting from server because network is down")
                    stopService(intent)
                    Timber.d("Setting connection to server not available")
                    appRepository.setConnectionToServerAvailable(false)
                }
                else -> {
                    Timber.d("Network status is $status, doing nothing")
                }
            }
        }
    }

    override fun onBackPressed() {
        val navigationHistoryEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("navigation_history_enabled", resources.getBoolean(R.bool.pref_default_navigation_history_enabled))
        if (!navigationHistoryEnabled) {
            // The following fragments can be called from the channel list fragment.
            // So do not finish the activity in case any of these fragments are visible
            // but pop the back stack so that the channel list is shown again.
            when (supportFragmentManager.findFragmentById(R.id.main)) {
                is ProgramListFragment -> clearSearchResultsOrPopBackStack()
                is ProgramDetailsFragment -> clearSearchResultsOrPopBackStack()
                is RecordingDetailsFragment -> clearSearchResultsOrPopBackStack()
                is SeriesRecordingDetailsFragment -> clearSearchResultsOrPopBackStack()
                is TimerRecordingDetailsFragment -> clearSearchResultsOrPopBackStack()
                else -> finish()
            }
        } else {
            // Only finish the activity when the last fragment is visible
            if (supportFragmentManager.backStackEntryCount <= 1) {
                finish()
            } else {
                clearSearchResultsOrPopBackStack()
            }
        }
    }

    /**
     * Pops the back stack to go back to the previous fragment or
     * in case a search was active, clears the search results.
     * After that a new back press can finish the activity.
     */
    private fun clearSearchResultsOrPopBackStack() {
        if (baseViewModel.isSearchActive) {
            Timber.d("Clearing search result")
            baseViewModel.clearSearchQuery()

            if (baseViewModel.removeFragmentWhenSearchIsDone) {
                Timber.d("Removing current fragment because flag was set")
                baseViewModel.removeFragmentWhenSearchIsDone = false
                super.onBackPressed()
                navigationViewModel.setSelectedMenuItemId(navigationDrawer.getSelectedMenu())
            }
        } else {
            Timber.d("Removing current fragment")
            super.onBackPressed()
            navigationViewModel.setSelectedMenuItemId(navigationDrawer.getSelectedMenu())
        }
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (Build.VERSION.SDK_INT in 21..25) {
            return
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }
}
