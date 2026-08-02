package com.ledger.app.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time setup steps the user has been through.
 *
 * SharedPreferences rather than the encrypted database, for the same reason the
 * tutorial's bookkeeping is: this decides what to *show*, it holds nothing
 * financial, and it has to be answerable before SQLCipher has finished opening.
 */
@Singleton
class SetupPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether the user has been offered the category shortlist.
     *
     * A flag rather than a look at the data, because "every category is enabled"
     * is ambiguous — it is equally the state of someone who never saw the screen
     * and of someone who saw it and kept the lot. Guessing wrong means showing
     * the same setup step again to a user who has already answered it.
     */
    var categoriesChosen: Boolean
        get() = prefs.getBoolean(KEY_CATEGORIES_CHOSEN, false)
        set(value) { prefs.edit().putBoolean(KEY_CATEGORIES_CHOSEN, value).apply() }

    private companion object {
        const val PREFS = "ledger_setup"
        const val KEY_CATEGORIES_CHOSEN = "categories_chosen"
    }
}
