package io.github.rthdfd.dsh

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class CommandResult(
    val requestId: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val errorCode: Int,
    val errorMessage: String,
) {
    val succeeded: Boolean
        get() = errorCode == -1 && exitCode == 0

    val displayText: String
        get() = listOf(stdout.trim(), stderr.trim(), errorMessage.trim())
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}

internal object CommandCallbacks {
    private val callbacks = ConcurrentHashMap<String, (CommandResult) -> Unit>()

    fun register(requestId: String, callback: (CommandResult) -> Unit) {
        callbacks[requestId] = callback
    }

    fun complete(result: CommandResult) {
        callbacks.remove(result.requestId)?.invoke(result)
    }
}

internal object TermuxBridge {
    const val PACKAGE_NAME = "com.termux"
    const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val TERMUX_RELEASES_URL = "https://github.com/termux/termux-app/releases/latest"

    private const val SERVICE_NAME = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val EXTRA_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    private const val EXTRA_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    fun isInstalled(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun hasRunCommandPermission(context: Context): Boolean =
        context.checkSelfPermission(RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun openTermux(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMUX_RELEASES_URL)))
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun sendScript(
        context: Context,
        script: String,
        label: String,
        description: String,
        background: Boolean,
        sessionAction: String = "0",
        onResult: (CommandResult) -> Unit,
    ): String {
        check(isInstalled(context)) { "Termux is not installed" }

        val requestId = UUID.randomUUID().toString()
        val callbackIntent = Intent(context, CommandResultReceiver::class.java).apply {
            putExtra(CommandResultReceiver.EXTRA_REQUEST_ID, requestId)
        }
        val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            callbackIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
        )

        val commandIntent = Intent().apply {
            setClassName(PACKAGE_NAME, SERVICE_NAME)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(EXTRA_ARGUMENTS, arrayOf("--noprofile", "--norc", "-s"))
            putExtra(EXTRA_STDIN, script)
            putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home")
            putExtra(EXTRA_BACKGROUND, background)
            putExtra(EXTRA_SESSION_ACTION, sessionAction)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_DESCRIPTION, description)
            putExtra(EXTRA_PENDING_INTENT, pendingIntent)
        }

        CommandCallbacks.register(requestId, onResult)
        try {
            context.startService(commandIntent)
        } catch (error: SecurityException) {
            CommandCallbacks.complete(
                CommandResult(requestId, -1, "", "", 1, error.message ?: "Permission denied"),
            )
        } catch (error: ActivityNotFoundException) {
            CommandCallbacks.complete(
                CommandResult(requestId, -1, "", "", 1, error.message ?: "Termux service unavailable"),
            )
        }
        return requestId
    }
}
