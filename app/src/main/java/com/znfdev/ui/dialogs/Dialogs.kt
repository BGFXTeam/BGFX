package com.znfdev.ui.dialogs

/** The dialogs BGFX shows at start-up. */
object Dialogs {

    fun update(onUpdate: () -> Unit) = DialogSpec(
        title = "Update now!",
        message = "A newer version of BGFX is available. Update to the latest version to keep going.",
        positive = "Update",
        onPositive = onUpdate,
        dismissOnPositive = false,   // stays until the app is updated
        cancelable = false,
        icon = "update",
        video = "znfdev.mp4"
    )

    fun notice(message: String, markdown: Boolean) = DialogSpec(
        title = "Notice",
        message = message,
        markdown = markdown,
        positive = "OK",
        icon = "info",
        video = "edit2.mp4"
    )

    fun networkError(onRetry: () -> Unit, onExit: () -> Unit) = DialogSpec(
        title = "Connection error",
        message = "We couldn't fetch data from the server.",
        positive = "Retry",
        onPositive = onRetry,
        negative = "Exit",
        onNegative = onExit,
        cancelable = false,
        icon = "cloud_off",
        video = "edit7.mp4"
    )
    
    fun getKey(onClick: () -> Unit) = DialogSpec(
        title = "Get Key 😒",
        message = "Get the fucking key",
        positive = "Get Key",
        onPositive = onClick,
        video = "edit13.mp4",
        icon = "key"
    )

    fun credits() = DialogSpec(
        title = "ZNFDev",
        message = "Founder \u2022 Native, UX/UI & API development",
        positive = "Close",
        video = "znfdev.mp4",
        icon = "groups"
    )
}
