package org.tvheadend.tvhclient.ui.features.navigation

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.afollestad.materialdialogs.MaterialDialog
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.holder.BadgeStyle
import com.mikepenz.materialdrawer.holder.StringHolder
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IProfile
import org.tvheadend.tvhclient.R
import org.tvheadend.tvhclient.domain.entity.Connection
import org.tvheadend.tvhclient.ui.features.channels.ChannelListFragment
import org.tvheadend.tvhclient.ui.features.dvr.recordings.CompletedRecordingListFragment
import org.tvheadend.tvhclient.ui.features.dvr.recordings.FailedRecordingListFragment
import org.tvheadend.tvhclient.ui.features.dvr.recordings.RemovedRecordingListFragment
import org.tvheadend.tvhclient.ui.features.dvr.recordings.ScheduledRecordingListFragment
import org.tvheadend.tvhclient.ui.features.dvr.series_recordings.SeriesRecordingListFragment
import org.tvheadend.tvhclient.ui.features.dvr.timer_recordings.TimerRecordingListFragment
import org.tvheadend.tvhclient.ui.features.epg.ProgramGuideFragment
import org.tvheadend.tvhclient.ui.features.information.StatusFragment
import org.tvheadend.tvhclient.ui.features.information.StatusViewModel
import org.tvheadend.tvhclient.ui.features.information.WebViewFragment
import org.tvheadend.tvhclient.ui.features.unlocker.UnlockerFragment
import org.tvheadend.tvhclient.util.getThemeId
import java.util.*

class NavigationDrawer(private val activity: AppCompatActivity,
                       private val savedInstanceState: Bundle?,
                       private val toolbar: Toolbar,
                       private val navigationViewModel: NavigationViewModel,
                       statusViewModel: StatusViewModel) : AccountHeader.OnAccountHeaderListener, Drawer.OnDrawerItemClickListener {

    private lateinit var headerResult: AccountHeader
    private lateinit var result: Drawer

    init {
        createHeader()
        createMenu()

        navigationViewModel.isUnlocked.observe(activity, Observer { result.removeItem(MENU_UNLOCKER.toLong()) })
        navigationViewModel.connections.observe(activity, Observer { this.showConnectionsInDrawerHeader(it) })

        statusViewModel.channelCount.observe(activity, Observer { count -> result.updateBadge(MENU_CHANNELS.toLong(), StringHolder(count.toString())) })
        statusViewModel.seriesRecordingCount.observe(activity, Observer { count -> result.updateBadge(MENU_SERIES_RECORDINGS.toLong(), StringHolder(count.toString())) })
        statusViewModel.timerRecordingCount.observe(activity, Observer { count -> result.updateBadge(MENU_TIMER_RECORDINGS.toLong(), StringHolder(count.toString())) })
        statusViewModel.completedRecordingCount.observe(activity, Observer { count -> result.updateBadge(MENU_COMPLETED_RECORDINGS.toLong(), StringHolder(count.toString())) })
        statusViewModel.scheduledRecordingCount.observe(activity, Observer { count -> result.updateBadge(MENU_SCHEDULED_RECORDINGS.toLong(), StringHolder(count.toString())) })
        statusViewModel.failedRecordingCount.observe(activity, Observer { count -> result.updateBadge(MENU_FAILED_RECORDINGS.toLong(), StringHolder(count.toString())) })
        statusViewModel.removedRecordingCount.observe(activity, Observer { count -> result.updateBadge(MENU_REMOVED_RECORDINGS.toLong(), StringHolder(count.toString())) })
    }

    private fun createHeader() {
        headerResult = AccountHeaderBuilder()
                .withActivity(activity)
                .withCompactStyle(true)
                .withSelectionListEnabledForSingleProfile(false)
                .withProfileImagesVisible(false)
                .withHeaderBackground(if (getThemeId(activity) == R.style.CustomTheme_Light) R.drawable.header_light else R.drawable.header_dark)
                .withOnAccountHeaderListener(this)
                .withSavedInstance(savedInstanceState)
                .build()
    }

    private fun createMenu() {
        val badgeStyle = BadgeStyle()
                .withColorRes(getResourceIdFromAttr(R.attr.material_drawer_badge))

        val channelItem = PrimaryDrawerItem()
                .withIdentifier(MENU_CHANNELS.toLong()).withName(R.string.channels)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_channels))
                .withBadgeStyle(badgeStyle)
        val programGuideItem = PrimaryDrawerItem()
                .withIdentifier(MENU_PROGRAM_GUIDE.toLong()).withName(R.string.pref_program_guide)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_program_guide))
        val completedRecordingsItem = PrimaryDrawerItem()
                .withIdentifier(MENU_COMPLETED_RECORDINGS.toLong()).withName(R.string.completed_recordings)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_completed_recordings))
                .withBadgeStyle(badgeStyle)
        val scheduledRecordingsItem = PrimaryDrawerItem()
                .withIdentifier(MENU_SCHEDULED_RECORDINGS.toLong()).withName(R.string.scheduled_recordings)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_scheduled_recordings))
                .withBadgeStyle(badgeStyle)
        val seriesRecordingsItem = PrimaryDrawerItem()
                .withIdentifier(MENU_SERIES_RECORDINGS.toLong()).withName(R.string.series_recordings)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_scheduled_recordings))
                .withBadgeStyle(badgeStyle)
        val timerRecordingsItem = PrimaryDrawerItem()
                .withIdentifier(MENU_TIMER_RECORDINGS.toLong()).withName(R.string.timer_recordings)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_scheduled_recordings))
                .withBadgeStyle(badgeStyle)
        val failedRecordingsItem = PrimaryDrawerItem()
                .withIdentifier(MENU_FAILED_RECORDINGS.toLong()).withName(R.string.failed_recordings)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_failed_recordings))
                .withBadgeStyle(badgeStyle)
        val removedRecordingsItem = PrimaryDrawerItem()
                .withIdentifier(MENU_REMOVED_RECORDINGS.toLong()).withName(R.string.removed_recordings)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_removed_recordings))
                .withBadgeStyle(badgeStyle)
        val statusItem = PrimaryDrawerItem()
                .withIdentifier(MENU_STATUS.toLong()).withName(R.string.status)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_status))
        val settingsItem = PrimaryDrawerItem()
                .withIdentifier(MENU_SETTINGS.toLong()).withName(R.string.settings)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_settings))
                .withSelectable(false)
        val extrasItem = PrimaryDrawerItem()
                .withIdentifier(MENU_UNLOCKER.toLong()).withName(R.string.pref_unlocker)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_extras))
        val helpItem = PrimaryDrawerItem()
                .withIdentifier(MENU_HELP.toLong()).withName(R.string.help_and_support)
                .withIcon(getResourceIdFromAttr(R.attr.ic_menu_help))

        val drawerBuilder = DrawerBuilder()
        drawerBuilder.withActivity(activity)
                .withAccountHeader(headerResult)
                .withToolbar(toolbar)
                .withOnDrawerItemClickListener(this)
                .withSavedInstance(savedInstanceState)

        drawerBuilder.addDrawerItems(
                channelItem,
                programGuideItem,
                DividerDrawerItem(),
                completedRecordingsItem,
                scheduledRecordingsItem,
                seriesRecordingsItem,
                timerRecordingsItem,
                failedRecordingsItem,
                removedRecordingsItem,
                DividerDrawerItem(),
                extrasItem,
                settingsItem,
                helpItem,
                statusItem)

        result = drawerBuilder.build()
    }

    private fun getResourceIdFromAttr(@AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        activity.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.resourceId
    }

    private fun showConnectionsInDrawerHeader(connections: List<Connection>) {
        // Remove old profiles from the header
        val profileIdList = ArrayList<Long>()
        headerResult.profiles?.forEach {
            profileIdList.add(it.identifier)
        }
        for (id in profileIdList) {
            headerResult.removeProfileByIdentifier(id)
        }
        // Add the existing connections as new profiles
        if (connections.isNotEmpty()) {
            for ((id, name, serverUrl) in connections) {
                headerResult.addProfiles(
                        ProfileDrawerItem()
                                .withIdentifier(id.toLong())
                                .withName(name)
                                .withEmail(serverUrl))
            }
        } else {
            headerResult.addProfiles(ProfileDrawerItem().withName(R.string.no_connection_available))
        }

        headerResult.setActiveProfile(navigationViewModel.connection.id.toLong())
    }

    override fun onProfileChanged(view: View?, profile: IProfile<*>, current: Boolean): Boolean {
        result.closeDrawer()

        // Do nothing if the same profile has been selected
        if (current) {
            return true
        }

        MaterialDialog(activity).show {
            title(R.string.dialog_title_connect_to_server)
            negativeButton(R.string.cancel) {
                headerResult.setActiveProfile(navigationViewModel.connection.id.toLong())
            }
            positiveButton(R.string.dialog_button_connect) {
                headerResult.setActiveProfile(profile.identifier)
                if (navigationViewModel.setSelectedConnectionAsActive(profile.identifier.toInt())) {
                    navigationViewModel.updateConnectionAndRestartApplication(activity)
                }
            }
            cancelable(false)
            cancelOnTouchOutside(false)
        }
        return false
    }

    override fun onItemClick(view: View?, position: Int, drawerItem: IDrawerItem<*>): Boolean {
        result.closeDrawer()
        navigationViewModel.setNavigationMenuId(drawerItem.identifier.toInt())
        return true
    }

    fun saveInstanceState(outState: Bundle): Bundle {
        var out = outState
        out = result.saveInstanceState(out)
        out = headerResult.saveInstanceState(out)
        return out
    }

    fun handleMenuSelection(fragment: Fragment?) {
        when (fragment) {
            is ChannelListFragment -> result.setSelection(MENU_CHANNELS.toLong(), false)
            is ProgramGuideFragment -> result.setSelection(MENU_PROGRAM_GUIDE.toLong(), false)
            is CompletedRecordingListFragment -> result.setSelection(MENU_COMPLETED_RECORDINGS.toLong(), false)
            is ScheduledRecordingListFragment -> result.setSelection(MENU_SCHEDULED_RECORDINGS.toLong(), false)
            is SeriesRecordingListFragment -> result.setSelection(MENU_SERIES_RECORDINGS.toLong(), false)
            is TimerRecordingListFragment -> result.setSelection(MENU_TIMER_RECORDINGS.toLong(), false)
            is FailedRecordingListFragment -> result.setSelection(MENU_FAILED_RECORDINGS.toLong(), false)
            is RemovedRecordingListFragment -> result.setSelection(MENU_REMOVED_RECORDINGS.toLong(), false)
            is StatusFragment -> result.setSelection(MENU_STATUS.toLong(), false)
            is UnlockerFragment -> result.setSelection(MENU_UNLOCKER.toLong(), false)
            is WebViewFragment -> result.setSelection(MENU_HELP.toLong(), false)
        }
    }

    companion object {

        // The index for the navigation drawer menus
        const val MENU_CHANNELS = 0
        const val MENU_PROGRAM_GUIDE = 1
        const val MENU_COMPLETED_RECORDINGS = 2
        const val MENU_SCHEDULED_RECORDINGS = 3
        const val MENU_SERIES_RECORDINGS = 4
        const val MENU_TIMER_RECORDINGS = 5
        const val MENU_FAILED_RECORDINGS = 6
        const val MENU_REMOVED_RECORDINGS = 7
        const val MENU_STATUS = 8
        const val MENU_SETTINGS = 9
        const val MENU_UNLOCKER = 10
        const val MENU_HELP = 11
    }
}
