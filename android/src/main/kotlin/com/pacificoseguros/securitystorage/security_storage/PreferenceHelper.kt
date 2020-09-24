package com.pacificoseguros.securitystorage.security_storage

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

object PreferenceHelper {

    val INITIALIZATIONVECTORS = "INITIALIZATIONVECTORS"

    fun defaultPreference(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun customPreference(context: Context, name: String): SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    inline fun SharedPreferences.editMe(operation: (SharedPreferences.Editor) -> Unit) {
        val editMe = edit()
        operation(editMe)
        editMe.apply()
    }


    var SharedPreferences.initializationVectors
        get() = getString(INITIALIZATIONVECTORS, "")
        set(value) {
            editMe {
                it.putString(INITIALIZATIONVECTORS, value)
            }
        }

    var SharedPreferences.clearValues
        get() = { }
        set(value) {
            editMe {
                it.clear()
            }
        }
}