package org.tvheadend.tvhclient.ui.features.navigation

import android.app.Application
import androidx.lifecycle.LiveData
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.ui.base.BaseViewModel
import org.tvheadend.tvhclient.ui.common.SingleLiveEvent
import org.tvheadend.tvhclient.ui.features.navigation.NavigationDrawer.Companion.MENU_SETTINGS
import timber.log.Timber

class NavigationViewModel(application: Application) : BaseViewModel(application) {

    val connections = appRepository.connectionData.getLiveDataItems()
    private val navigationMenuId = SingleLiveEvent<Int>()
    var previousNavigationMenuId: Int = -1

    init {
        Timber.d("Initializing")
        navigationMenuId.value = Integer.parseInt(sharedPreferences.getString("start_screen", appContext.resources.getString(R.string.pref_default_start_screen))!!)
    }

    fun getNavigationMenuId(): LiveData<Int> = navigationMenuId

    fun setNavigationMenuId(id: Int) {
        Timber.d("Received new navigation id $id")
        if (previousNavigationMenuId != id || id == MENU_SETTINGS) {
            Timber.d("Setting navigation id to $id")
            navigationMenuId.value = id
        }
    }

    fun setSelectedConnectionAsActive(id: Int): Boolean {
        val connection = appRepository.connectionData.getItemById(id)
        return if (connection != null) {
            connection.isActive = true
            connection.isSyncRequired = true
            connection.lastUpdate = 0
            appRepository.connectionData.updateItem(connection)
            true
        } else {
            false
        }
    }

    fun onBackPressed() {
        Timber.d("Back button was pressed, setting current navigation id ${navigationMenuId.value} to the previous id $previousNavigationMenuId")
        navigationMenuId.value = previousNavigationMenuId
    }
}