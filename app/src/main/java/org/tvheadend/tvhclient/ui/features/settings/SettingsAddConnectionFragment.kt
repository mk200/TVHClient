package org.tvheadend.tvhclient.ui.features.settings

import android.os.Bundle
import org.tvheadend.data.entity.Connection
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.util.extensions.sendSnackbarMessage
import timber.log.Timber

class SettingsAddConnectionFragment : SettingsConnectionBaseFragment() {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        toolbarInterface.setTitle(getString(R.string.add_connection))
        toolbarInterface.setSubtitle("")

        // When the fragment is created for the first time, create a new empty
        // connection which can be populated with the new information.
        if (savedInstanceState == null) {
            settingsViewModel.connectionToEdit = Connection()
        }

        settingsViewModel.connectionCountLiveData.observe(viewLifecycleOwner,  { count ->
            Timber.d("Received live data, connection count is $count, setting this one as the active")
            activeEnabledPreference.isChecked = (count == 0)
            settingsViewModel.connectionToEdit.isActive = (count == 0)
        })
    }

    override fun save() {
        when(val result = connectionValidator.isConnectionInputValid(settingsViewModel.connectionToEdit)) {
            is ValidationResult.Success -> {
                settingsViewModel.addConnection()
                activity.let {
                    if (it is RemoveFragmentFromBackstackInterface) {
                        it.removeFragmentFromBackstack()
                    }
                }
            }
            is ValidationResult.Failed -> {
                context?.sendSnackbarMessage(getErrorDescription(result.reason))
            }
        }
    }
}
