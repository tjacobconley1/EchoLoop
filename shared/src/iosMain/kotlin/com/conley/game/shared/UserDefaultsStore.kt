package com.conley.game.shared

import platform.Foundation.NSUserDefaults

/** Saves progress in NSUserDefaults. */
class UserDefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun getInt(key: String, default: Int): Int =
        if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key).toInt()
    override fun putInt(key: String, value: Int) { defaults.setInteger(value.toLong(), key) }
    override fun getString(key: String, default: String): String = defaults.stringForKey(key) ?: default
    override fun putString(key: String, value: String) { defaults.setObject(value, forKey = key) }
}

