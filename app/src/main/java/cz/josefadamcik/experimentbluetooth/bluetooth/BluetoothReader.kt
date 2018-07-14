package cz.josefadamcik.experimentbluetooth.bluetooth

import android.util.Log
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.consumes
import okio.Okio
import java.io.InputStream


/**
 * Uses kotlin coroutines to read from serial on background.
 */
class BluetoothReader (
        private val bluetoothInputStream : InputStream,
        private val onReceived: (line: String) -> Unit
) {
    private lateinit var mainThreadJob: Job
    private var started = false
    companion object {
        private const val TAG = "BluetoothReader"
    }

    fun start() {
        Log.d(TAG, "Starting bluetooth reader")
        started = true
        mainThreadJob = launch (UI) {
            val channel = Channel<String>()
            val backgroundJob = launch { readBluetoothSerial(this, channel) }
            while (isActive) {
                while (!channel.isEmpty) {
                    Log.d(TAG, "Nonempty channel")
                    val line = channel.receive()
                    Log.d(TAG, "Received $line")
                    onReceived(line)
                }
                Log.d(TAG, "Empty channel, wait")
                delay(500) //wait for more
            }
            Log.d(TAG, "we are no longer active, let's finish")
            backgroundJob.cancelAndJoin()
        }
    }


    fun destroy() {
        if (started) {
            Log.d(TAG, "Destroying bluetooth reader")
            launch (UI) {
                val result = mainThreadJob.cancelAndJoin()
                Log.d(TAG, "Cancel $mainThreadJob $result")
            }
        }
    }

    private suspend fun readBluetoothSerial(scope: CoroutineScope, channel: Channel<String>) {
        Okio.buffer(Okio.source(bluetoothInputStream)).use { source ->
            while(scope.isActive) {
                source.readUtf8Line()?.let {
                    Log.d(TAG, "Serial: $it")
                    channel.send(it)
                }
            }
            channel.close()
        }
    }

}

