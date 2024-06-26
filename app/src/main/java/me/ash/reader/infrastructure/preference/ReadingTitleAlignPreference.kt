package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.style.TextAlign
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.readingTitleAlign
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalReadingTitleAlign =
    compositionLocalOf<ReadingTitleAlignPreference> { ReadingTitleAlignPreference.default }

sealed class ReadingTitleAlignPreference(val value: Int) : Preference() {
    object Left : ReadingTitleAlignPreference(0)
    object Right : ReadingTitleAlignPreference(1)
    object Center : ReadingTitleAlignPreference(2)
    object Justify : ReadingTitleAlignPreference(3)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKey.readingTitleAlign,
                value
            )
        }
    }

    @Stable
    fun toDesc(context: Context): String =
        when (this) {
            Left -> context.getString(R.string.align_left)
            Right -> context.getString(R.string.align_right)
            Center -> context.getString(R.string.center_text)
            Justify -> context.getString(R.string.justify)
        }

    @Stable
    fun toTextAlign(): TextAlign =
        when (this) {
            Left -> TextAlign.Start
            Right -> TextAlign.End
            Center -> TextAlign.Center
            Justify -> TextAlign.Justify
        }

    companion object {

        val default = Left
        val values = listOf(Left, Right, Center, Justify)

        fun fromPreferences(preferences: Preferences): ReadingTitleAlignPreference =
            when (preferences[DataStoreKey.keys[readingTitleAlign]?.key as Preferences.Key<Int>]) {
                0 -> Left
                1 -> Right
                2 -> Center
                3 -> Justify
                else -> default
            }
    }
}
