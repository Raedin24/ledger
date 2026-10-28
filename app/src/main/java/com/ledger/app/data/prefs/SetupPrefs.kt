package com.ledger.app.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time setup steps the user has been through.
 *
 * Kept in SharedPreferences rather than the encrypted database, like the
 * guide's progress: it only decides what to show, holds nothing financial,
 * and must be readable before the database opens.
 */
@Singleton
class SetupPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether the user has been offered the category shortlist.
     *
     * This is a flag because the data can't tell: "every category is enabled"
     * looks the same for someone who never saw the screen and someone who kept
     * everything.
     */
    var categoriesChosen: Boolean
        get() = prefs.getBoolean(KEY_CATEGORIES_CHOSEN, false)
        set(value) { prefs.edit().putBoolean(KEY_CATEGORIES_CHOSEN, value).apply() }

    /**
     * The version of the default category list this ledger has been given (see
     * DefaultCategories.VERSION). Ledgers from before it was recorded start at 1.
     */
    var defaultCategoriesVersion: Int
        get() = prefs.getInt(KEY_DEFAULTS_VERSION, 1)
        set(value) { prefs.edit().putInt(KEY_DEFAULTS_VERSION, value).apply() }

    private companion object {
        const val PREFS = "ledger_setup"
        const val KEY_CATEGORIES_CHOSEN = "categories_chosen"
        const val KEY_DEFAULTS_VERSION = "default_categories_version"
    }
}
