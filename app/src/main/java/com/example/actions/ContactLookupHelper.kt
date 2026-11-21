package com.example.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ResolvedContact(
    val displayName: String,
    val phoneNumber: String
)

object ContactLookupHelper {

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun findPhoneByName(context: Context, queryName: String): ResolvedContact? {
        if (!hasContactsPermission(context) || queryName.isBlank()) return null

        val trimmed = queryName.trim()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$trimmed%")

        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else trimmed
                    val rawNum = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                    if (rawNum.isNotBlank()) {
                        ResolvedContact(name, rawNum)
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Normalizes a phone string into digits suitable for wa.me links.
     * Preserves leading country code or converts local 01XXXXXXXXX BD numbers to 8801XXXXXXXXX.
     */
    fun normalizeForWhatsApp(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        return when {
            digits.startsWith("01") && digits.length == 11 -> "88$digits"
            else -> digits
        }
    }
}
