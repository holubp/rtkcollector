package org.rtkcollector.app.ui.dashboard

import android.content.Context

internal interface DashboardUiPreferenceStore {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

private class AndroidDashboardUiPreferenceStore(context: Context) : DashboardUiPreferenceStore {
    private val preferences = context.getSharedPreferences("dashboard-ui", Context.MODE_PRIVATE)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
}

internal class DashboardUiPreferences(
    private val store: DashboardUiPreferenceStore,
) {
    constructor(context: Context) : this(AndroidDashboardUiPreferenceStore(context))

    fun setupExpanded(): Boolean =
        store.getBoolean(KEY_SETUP_EXPANDED, DefaultDashboardSetupExpanded)

    fun saveSetupExpanded(expanded: Boolean) {
        store.putBoolean(KEY_SETUP_EXPANDED, expanded)
    }

    private companion object {
        const val KEY_SETUP_EXPANDED = "setupExpanded"
    }
}
