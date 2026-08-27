package com.gyanoba.inspektor.data

import com.gyanoba.inspektor.UnstableInspektorAPI

/**
 * Sets the application id used to resolve the database location. No-op — there is no database.
 */
@UnstableInspektorAPI
public expect fun setApplicationId(applicationId: String)
