package com.dragolabs.able.feature.tools.downdetector

import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
data class DowndetectorUiState(
    val site: DowndetectorSite = DowndetectorSites.DEFAULT,
    /** True when the user picked the country; false when it came from SIM/network/locale. */
    val countryChosen: Boolean = false,
    val query: String = "",
    val favourites: List<String> = emptyList(),
) {
    val suggestions: List<SuggestedService>
        get() = DowndetectorSites.suggestions(site.countryIso).filterNot { s ->
            favourites.any { DowndetectorSites.slugify(it) == s.slug }
        }
}

/**
 * Local preferences for the Downdetector launcher (NET-002): chosen country and favourite
 * service names. Stored in app-private SharedPreferences; nothing leaves the device.
 */
class DowndetectorPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("downdetector", Context.MODE_PRIVATE)

    var country: String?
        get() = prefs.getString(KEY_COUNTRY, null)
        set(value) = prefs.edit { putString(KEY_COUNTRY, value) }

    var favourites: List<String>
        get() = prefs.getString(KEY_FAVOURITES, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        set(value) = prefs.edit { putString(KEY_FAVOURITES, value.joinToString("\n")) }

    private companion object {
        const val KEY_COUNTRY = "country"
        const val KEY_FAVOURITES = "favourites"
    }
}

class DowndetectorViewModel(appContext: Context) : ViewModel() {

    private val prefs = DowndetectorPrefs(appContext)

    private val _uiState = MutableStateFlow(initialState(appContext))
    val uiState: StateFlow<DowndetectorUiState> = _uiState.asStateFlow()

    private fun initialState(context: Context): DowndetectorUiState {
        val saved = prefs.country
        val site = if (saved != null && DowndetectorSites.isSupported(saved)) {
            DowndetectorSites.forCountry(saved)
        } else {
            // networkCountryIso / simCountryIso need no permission.
            val tm = context.getSystemService(TelephonyManager::class.java)
            DowndetectorSites.resolveDefault(
                networkIso = runCatching { tm?.networkCountryIso }.getOrNull(),
                simIso = runCatching { tm?.simCountryIso }.getOrNull(),
                localeIso = context.resources.configuration.locales[0]?.country ?: Locale.getDefault().country,
            )
        }
        return DowndetectorUiState(site = site, countryChosen = saved != null, favourites = prefs.favourites)
    }

    fun selectCountry(iso: String) {
        val site = DowndetectorSites.forCountry(iso)
        prefs.country = site.countryIso
        _uiState.update { it.copy(site = site, countryChosen = true) }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query.take(DowndetectorSites.MAX_NAME_LENGTH)) }

    fun addFavourite(name: String) {
        val updated = DowndetectorSites.addFavourite(_uiState.value.favourites, name)
        prefs.favourites = updated
        _uiState.update { it.copy(favourites = updated) }
    }

    fun removeFavourite(name: String) {
        val updated = DowndetectorSites.removeFavourite(_uiState.value.favourites, name)
        prefs.favourites = updated
        _uiState.update { it.copy(favourites = updated) }
    }
}
