package org.tvheadend.tvhclient.ui.features.settings

import android.os.Bundle
import androidx.lifecycle.Observer
import org.tvheadend.tvhclient.R
import timber.log.Timber

class SettingsAddConnectionFragment : SettingsConnectionBaseFragment() {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        toolbarInterface.setTitle(getString(R.string.add_connection))
        toolbarInterface.setSubtitle("")

        if (savedInstanceState == null) {
            settingsViewModel.createNewConnection()
        }

        settingsViewModel.connectionCount.observe(viewLifecycleOwner, Observer { count ->
            Timber.d("Received live data, connection count is $count")
            activeEnabledPreference.isChecked = (count == 0)
        })
    }

    override fun save() {
        if (isConnectionInputValid(settingsViewModel.connection)) {
            settingsViewModel.addConnection()
            activity.let {
                if (it is RemoveFragmentFromBackstackInterface) {
                    it.removeFragmentFromBackstack()
                }
            }
        }
    }
}
