package com.example.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/**
 * Utility for composing and launching formatted WhatsApp messages via Intent.ACTION_VIEW
 * targeting the 'com.whatsapp' package.
 */
object WhatsAppMessagingUtil {

    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    /**
     * Resolves whether standard WhatsApp ('com.whatsapp') or WhatsApp Business is installed.
     */
    fun findWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        return listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE).firstOrNull { pkg ->
            pm.getLaunchIntentForPackage(pkg) != null
        }
    }

    /**
     * Builds an Intent.ACTION_VIEW targeting [packageName] (defaults to 'com.whatsapp')
     * with a formatted UTF-8 message and optional phone number.
     */
    fun buildActionViewIntent(
        phoneNumber: String,
        formattedMessage: String,
        packageName: String = WHATSAPP_PACKAGE
    ): Intent {
        val normalizedPhone = ContactLookupHelper.normalizeForWhatsApp(phoneNumber)
        val encodedText = Uri.encode(formattedMessage.trim())
        val uriString = if (normalizedPhone.isNotBlank()) {
            "https://api.whatsapp.com/send?phone=$normalizedPhone&text=$encodedText"
        } else {
            "https://api.whatsapp.com/send?text=$encodedText"
        }

        return Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Reads display name and phone number from a URI returned by Android's contact picker.
     */
    fun resolveSelectedContactFromUri(context: Context, contactPhoneUri: Uri): ResolvedContact? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return try {
            context.contentResolver.query(contactPhoneUri, projection, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        )
                        val numIdx = cursor.getColumnIndex(
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        )
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else "Contact"
                        val phone = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                        if (phone.isNotBlank()) ResolvedContact(name, phone) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Launches WhatsApp directly via Intent.ACTION_VIEW with package 'com.whatsapp'
     * to send [formattedMessage] to [phoneNumber].
     */
    fun sendFormattedMessage(
        context: Context,
        phoneNumber: String,
        formattedMessage: String,
        displayName: String? = null
    ): ActionExecutionResult {
        val targetPackage = findWhatsAppPackage(context) ?: WHATSAPP_PACKAGE
        val viewIntent = buildActionViewIntent(
            phoneNumber = phoneNumber,
            formattedMessage = formattedMessage,
            packageName = targetPackage
        )

        return try {
            if (viewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(viewIntent)
                val label = displayName?.takeIf { it.isNotBlank() } ?: phoneNumber.ifBlank { "selected contact" }
                ActionExecutionResult(
                    success = true,
                    message = "Opened WhatsApp ($targetPackage) for $label with formatted message ready."
                )
            } else {
                // Fallback to browser/system handler if com.whatsapp is not installed
                val webFallback = Intent(Intent.ACTION_VIEW, viewIntent.data).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (webFallback.resolveActivity(context.packageManager) != null) {
                    context.startActivity(webFallback)
                    ActionExecutionResult(
                        success = true,
                        message = "WhatsApp app not found; opened WhatsApp web link."
                    )
                } else {
                    ActionExecutionResult(
                        success = false,
                        message = "WhatsApp (com.whatsapp) is not installed on this device."
                    )
                }
            }
        } catch (e: Exception) {
            ActionExecutionResult(
                success = false,
                message = "Failed to open WhatsApp: ${e.localizedMessage ?: "App unavailable"}"
            )
        }
    }

    /**
     * Resolves [contactQuery] (phone number or contact name) and sends [formattedMessage]
     * via Intent.ACTION_VIEW targeting 'com.whatsapp'.
     */
    fun sendFormattedMessageToContact(
        context: Context,
        contactQuery: String,
        formattedMessage: String
    ): ActionExecutionResult {
        val explicitDigits = contactQuery.filter { it.isDigit() }
        val resolved = when {
            explicitDigits.length >= 6 -> ResolvedContact(contactQuery, explicitDigits)
            contactQuery.isNotBlank() -> ContactLookupHelper.findPhoneByName(context, contactQuery)
            else -> null
        }

        if (resolved != null) {
            return sendFormattedMessage(
                context = context,
                phoneNumber = resolved.phoneNumber,
                formattedMessage = formattedMessage,
                displayName = resolved.displayName
            )
        }

        val needsPermission = contactQuery.isNotBlank() &&
            explicitDigits.length < 6 &&
            !ContactLookupHelper.hasContactsPermission(context)

        // Launch Intent.ACTION_VIEW with com.whatsapp and pre-filled formatted text so user can select contact
        val targetPackage = findWhatsAppPackage(context) ?: WHATSAPP_PACKAGE
        val pickerViewIntent = buildActionViewIntent(
            phoneNumber = "",
            formattedMessage = formattedMessage,
            packageName = targetPackage
        )
        return try {
            if (pickerViewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(pickerViewIntent)
                ActionExecutionResult(
                    success = true,
                    message = if (needsPermission) {
                        "Opened WhatsApp with message. Allow Contacts or pick a contact to jump directly to \"$contactQuery\"."
                    } else {
                        "Opened WhatsApp with formatted message. Pick a contact to send."
                    },
                    needsContactsPermission = needsPermission
                )
            } else {
                ActionExecutionResult(
                    success = false,
                    message = if (needsPermission) {
                        "Grant Contacts permission or pick a contact to send via WhatsApp."
                    } else {
                        "WhatsApp (com.whatsapp) is not installed on this device."
                    },
                    needsContactsPermission = needsPermission
                )
            }
        } catch (e: Exception) {
            ActionExecutionResult(
                success = false,
                message = "Could not launch WhatsApp: ${e.localizedMessage ?: "Unavailable"}",
                needsContactsPermission = needsPermission
            )
        }
    }
}
