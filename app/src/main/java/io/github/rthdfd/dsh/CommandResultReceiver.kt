package io.github.rthdfd.dsh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class CommandResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val bundle = intent.getBundleExtra(RESULT_BUNDLE) ?: Bundle.EMPTY
        val result = CommandResult(
            requestId = requestId,
            exitCode = bundle.getInt(EXIT_CODE, -1),
            stdout = bundle.getString(STDOUT, ""),
            stderr = bundle.getString(STDERR, ""),
            errorCode = bundle.getInt(ERROR_CODE, -1),
            errorMessage = bundle.getString(ERROR_MESSAGE, ""),
        )
        Handler(Looper.getMainLooper()).post { CommandCallbacks.complete(result) }
    }

    companion object {
        const val EXTRA_REQUEST_ID = "io.github.rthdfd.dsh.REQUEST_ID"
        private const val RESULT_BUNDLE = "result"
        private const val STDOUT = "stdout"
        private const val STDERR = "stderr"
        private const val EXIT_CODE = "exitCode"
        private const val ERROR_CODE = "err"
        private const val ERROR_MESSAGE = "errmsg"
    }
}
