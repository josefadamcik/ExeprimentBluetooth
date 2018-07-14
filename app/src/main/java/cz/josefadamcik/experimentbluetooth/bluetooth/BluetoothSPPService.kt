package cz.josefadamcik.experimentbluetooth.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import cz.josefadamcik.experimentbluetooth.Bluetooth
import cz.josefadamcik.experimentbluetooth.PersistentIntentService
import kotlinx.android.parcel.Parcelize
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


/**
 * Service runs in the background ant activities can send request via intents and listen to events via
 * broadcast listeners.
 *
 */
class BluetoothSPPService : PersistentIntentService("BluetoothSPP") {

    private enum class Actions {
        Connect,
        Send,
        Disconnect
    }

    companion object {
        const val TAG = "BluetoothSPPService"
        private const val PAR_DEVICE = "device"
        private const val PAR_CONTENT = "content"
        private const val BROADCAST_STATE_CHANGE = "broadcast_state_change"
        private const val BROADCAST_LINE_RECEIVED = "broadcast_line_received"

        fun createConnectIntent(context: Context, device: BluetoothDevice): Intent {
            val intent = createIntent(context)
            intent.action = Actions.Connect.toString()
            intent.putExtra(PAR_DEVICE, device)
            return intent
        }

        fun createSendLineIntent(context: Context, device: BluetoothDevice, content: String): Intent {
            val intent = createIntent(context)
            intent.action = Actions.Send.toString()
            intent.putExtra(PAR_DEVICE, device)
            intent.putExtra(PAR_CONTENT, content)
            return intent
        }

        fun createDisconnectIntent(context: Context): Intent {
            val intent = createIntent(context)
            intent.action = Actions.Disconnect.toString()
            return intent
        }

        fun createBroadcastIntentFilter(context: Context): IntentFilter {
            val iFilter = IntentFilter()
            iFilter.addAction(BROADCAST_LINE_RECEIVED)
            iFilter.addAction(BROADCAST_STATE_CHANGE)
            return iFilter
        }

        fun obtainBroadcastMessage(intent: Intent?) : BluetoothBroadcast {
            return intent?.action?.let { action: String ->
                return if (action == BROADCAST_LINE_RECEIVED && intent.hasExtra(PAR_CONTENT)) {
                    BluetoothBroadcast.LineReceived(intent.getStringExtra(PAR_CONTENT))
                } else {
                    BluetoothBroadcast.Invalid
                }
            } ?: BluetoothBroadcast.Invalid
        }

        private fun createIntent(context: Context) = Intent(context, BluetoothSPPService::class.java)
        private fun buildReceivedLineBroadcast(line: String): Intent {
            val intent = Intent(BROADCAST_LINE_RECEIVED)
            intent.putExtra(PAR_CONTENT, line)
            return intent
        }

        private fun buildStateChangedBroadcast(state: BluetoothConnectionState): Intent {
            val intent = Intent(BROADCAST_STATE_CHANGE)
            TODO("add state info and device info")
            return intent
        }

    }

    public enum class PublicState {
        NotConnected,
        Connecting,
        Connected,
        Error
    }


    public sealed class BluetoothBroadcast {
        object Invalid: BluetoothBroadcast() //unable to process data, invalid state
        data class LineReceived(val line:String): BluetoothBroadcast()
        data class StateChanged(val state: PublicState): BluetoothBroadcast()
    }

    private sealed class BluetoothConnectionState {
        object NotConnected : BluetoothConnectionState()
        data class Connecting(val device: BluetoothDevice) : BluetoothConnectionState()
        data class Connected(val device: BluetoothDevice,
                             val socket: BluetoothSocket,
                             val inputStream: InputStream,
                             val outputStream: OutputStream

        ) : BluetoothConnectionState() {
            fun close() {
                inputStream.close()
                outputStream.close()
                socket.close()
            }
        }
    }

    private var bluetoothConnectionState : BluetoothConnectionState = BluetoothConnectionState.NotConnected
    private var bluetoothReader: BluetoothReader? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
        if (bluetoothConnectionState is BluetoothConnectionState.Connected) {
            onHandleDisconnect()
        }
    }

    //runs on bg thread
    override fun onHandleIntent(intent: Intent) {
        when(Actions.valueOf(intent.action)) {
            Actions.Connect -> onHandleConnect(intent.extras.getParcelable(PAR_DEVICE))
            Actions.Disconnect -> onHandleDisconnect()
            Actions.Send -> TODO()
        }

    }

    private fun onHandleDisconnect() {
        bluetoothConnectionState.let {
            if (it is BluetoothConnectionState.Connected) {
                Log.i(TAG, "Disconnecting")
                bluetoothReader?.destroy()
                try {
                    (bluetoothConnectionState as BluetoothConnectionState.Connected).close()
                } catch (ex: IOException) {
                    Log.e(TAG, "unable to close socket", ex)
                }
                bluetoothConnectionState = BluetoothConnectionState.NotConnected
            }
        }
    }



    private fun onHandleConnect(maybeDevice: BluetoothDevice?) {
        maybeDevice?.let { device ->
            if (bluetoothConnectionState is BluetoothConnectionState.Connected) {
                onHandleDisconnect()
            }
            Log.i(TAG, "Connecting to ${device.address}")
            bluetoothConnectionState = BluetoothConnectionState.Connecting(device)
            try {
                val serialSocket = device.createRfcommSocketToServiceRecord(Bluetooth.serialPortProfileUUID)
                serialSocket.connect()
                bluetoothConnectionState = BluetoothConnectionState.Connected(device, serialSocket, serialSocket.inputStream, serialSocket.outputStream)
                startSocketReader(serialSocket)
            } catch (ex: Exception) {

                Log.e(TAG, "Unable to connect", ex)
                bluetoothConnectionState = BluetoothConnectionState.NotConnected
            }
        }

    }

    private fun onLineReceived(line: String) {
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(buildReceivedLineBroadcast(line))
    }



    private fun startSocketReader(serialSocket: BluetoothSocket) {
        bluetoothReader?.destroy();
        bluetoothReader = BluetoothReader(serialSocket.inputStream, ::onLineReceived)
        bluetoothReader?.start()
    }

}

