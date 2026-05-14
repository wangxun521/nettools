package com.example.nettools.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object Prefs {
    private const val NAME = "nettools_prefs"

    fun getString(ctx: Context, key: String, default: String = ""): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(key, default) ?: default

    fun putString(ctx: Context, key: String, value: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(key, value).apply()
    }

    fun getBool(ctx: Context, key: String, default: Boolean = false): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(key, default)

    fun putBool(ctx: Context, key: String, value: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(key, value).apply()
    }
}

@Composable
fun rememberPrefString(key: String, default: String): MutableState<String> {
    val ctx = LocalContext.current
    return remember(key) {
        val backing = mutableStateOf(Prefs.getString(ctx, key, default))
        object : MutableState<String> {
            override var value: String
                get() = backing.value
                set(v) {
                    if (v != backing.value) {
                        backing.value = v
                        Prefs.putString(ctx, key, v)
                    }
                }
            override fun component1() = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberPrefBool(key: String, default: Boolean): MutableState<Boolean> {
    val ctx = LocalContext.current
    return remember(key) {
        val backing = mutableStateOf(Prefs.getBool(ctx, key, default))
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = backing.value
                set(v) {
                    if (v != backing.value) {
                        backing.value = v
                        Prefs.putBool(ctx, key, v)
                    }
                }
            override fun component1() = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}
