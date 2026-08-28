package com.gyanoba.inspektor.platform

import com.gyanoba.inspektor.UnstableInspektorAPI

/**
 * Where Inspektor keeps its database and override store.
 *
 * On desktop this throws unless `setApplicationId(...)` was called first -- there is no other way
 * to know which directory belongs to the host application.
 */
@UnstableInspektorAPI
public expect fun getAppDataDir(): String

@UnstableInspektorAPI
public expect fun getAppCacheDir(): String