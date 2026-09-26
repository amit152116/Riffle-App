package com.amit152116.riffle.permission

import android.Manifest
import android.os.Build

object MediaPermission {
    val readAudio: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}
