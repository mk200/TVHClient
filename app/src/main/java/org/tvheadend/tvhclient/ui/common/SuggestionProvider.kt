package org.tvheadend.tvhclient.ui.common

import android.content.SearchRecentSuggestionsProvider

class SuggestionProvider : SearchRecentSuggestionsProvider() {

    init {
        setupSuggestions(AUTHORITY, MODE)
    }

    companion object {
        const val AUTHORITY = "org.tvheadend.tvhclient.ui.common.SuggestionProvider"
        const val MODE = DATABASE_MODE_QUERIES
    }
}
