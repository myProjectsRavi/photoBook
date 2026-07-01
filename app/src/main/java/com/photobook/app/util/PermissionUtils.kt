package com.photobook.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {

    enum class PhotoAccessMode {
        None,
        Limited,
        Full,
    }

    fun requiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.READ_MEDIA_IMAGES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                permissions += Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            }
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions += Manifest.permission.ACCESS_MEDIA_LOCATION
        }
        return permissions
    }

    fun hasPhotoPermissions(context: Context): Boolean {
        return photoAccessMode(context) != PhotoAccessMode.None
    }

    fun photoAccessMode(context: Context): PhotoAccessMode {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES) -> PhotoAccessMode.Full

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccessMode.Limited

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES) -> PhotoAccessMode.Full

            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) -> PhotoAccessMode.Full

            else -> PhotoAccessMode.None
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
