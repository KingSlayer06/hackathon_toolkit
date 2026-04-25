package com.kingslayer06.vox.platform

import android.content.Context

/** Tiny global ContentProvider-free way to hand the Android Context to common code. */
object AppContext {
    private var ref: Context? = null
    fun init(ctx: Context) { ref = ctx.applicationContext }
    fun get(): Context = ref ?: error("AppContext not initialised — call AppContext.init(this) in MainActivity.onCreate")
}
