package com.iliverez.spoldify.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.iliverez.spoldify.BuildConfig
import com.iliverez.spoldify.R
import com.iliverez.spoldify.SpoldifyApp

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        setupStreamingQuality()
        setupDownloadQuality()
        setupStorageLocation()
        setupMaxStorage()
        setupOfflineMode()
        setupNormalizeVolume()
        setupAbout()
    }

    private fun setupStreamingQuality() {
        val pref = findPreference<ListPreference>("streaming_quality") ?: return
        pref.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        pref.setOnPreferenceChangeListener { _, newValue ->
            SpoldifyApp.instance.preferences.streamingQuality =
                com.iliverez.spoldify.data.model.AudioQuality.entries.first { it.name == newValue }
            true
        }
    }

    private fun setupDownloadQuality() {
        val pref = findPreference<ListPreference>("download_quality") ?: return
        pref.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        pref.setOnPreferenceChangeListener { _, newValue ->
            SpoldifyApp.instance.preferences.downloadQuality =
                com.iliverez.spoldify.data.model.AudioQuality.entries.first { it.name == newValue }
            true
        }
    }

    private fun setupStorageLocation() {
        val pref = findPreference<Preference>("storage_location") ?: return
        pref.summary = SpoldifyApp.instance.preferences.storagePath
        pref.setOnPreferenceClickListener {
            Toast.makeText(requireContext(), "Storage picker not yet implemented", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun setupMaxStorage() {
        val pref = findPreference<SeekBarPreference>("max_storage_gb") ?: return
        pref.summary = "${SpoldifyApp.instance.preferences.maxStorageGb} GB"
        pref.setOnPreferenceChangeListener { _, newValue ->
            SpoldifyApp.instance.preferences.maxStorageGb = (newValue as Int)
            pref.summary = "$newValue GB"
            true
        }
    }

    private fun setupOfflineMode() {
        val pref = findPreference<SwitchPreferenceCompat>("offline_mode") ?: return
        pref.setOnPreferenceChangeListener { _, newValue ->
            SpoldifyApp.instance.preferences.offlineMode = newValue as Boolean
            true
        }
    }

    private fun setupNormalizeVolume() {
        val pref = findPreference<SwitchPreferenceCompat>("normalize_volume") ?: return
        pref.setOnPreferenceChangeListener { _, newValue ->
            SpoldifyApp.instance.preferences.normalizeVolume = newValue as Boolean
            true
        }
    }

    private fun setupAbout() {
        val count = preferenceScreen.preferenceCount
        if (count == 0) return
        val aboutCategory = preferenceScreen.getPreference(count - 1) as? PreferenceCategory ?: return
        if (aboutCategory.preferenceCount == 0) return
        aboutCategory.getPreference(0).summary = "Spoldify v${BuildConfig.VERSION_NAME}"
    }
}
