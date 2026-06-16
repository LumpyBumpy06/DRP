package com.drp33.quietsignal.data.remote.models

/** A list of storage object names (newest first) — used for the recent snaps /
 *  voice clips the viewer can page back through. */
data class ObjectsResponse(
    val objects: List<String> = emptyList(),
)
