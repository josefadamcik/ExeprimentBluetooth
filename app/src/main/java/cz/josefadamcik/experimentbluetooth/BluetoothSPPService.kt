package cz.josefadamcik.experimentbluetooth

import android.app.IntentService
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class BluetoothSPPService : IntentService("BluetoothSPP") {
    private enum class Actions {
        ACTION_CONNECT,
        ACTION_DISCONNECT
    }
    companion object {
        const val TAG = "BluetoothSPPService"

        private const val PAR_DEVICE = "device"

        fun createConnectIntent(context: Context, device: BluetoothDevice): Intent {
            val intent = Intent(context, BluetoothSPPService::class.java)
            intent.action = Actions.ACTION_CONNECT.toString()
            intent.putExtra(PAR_DEVICE, device)
            return intent
        }

        fun createDisconnectIntent(context: Context) = {
            val intent = Intent(context, BluetoothSPPService::class.java)
            intent.action = Actions.ACTION_DISCONNECT.toString()
        }
    }
    sealed class State {
        object NotConnected : State()
        data class Connecting(val device: BluetoothDevice) : BluetoothSPPService.State()
        data class Connected(val device: BluetoothDevice,
                             val socket: BluetoothSocket,
                             val inputStream: InputStream,
                             val outputStream: OutputStream

        ) : State() {
            fun close() {
                inputStream.close()
                outputStream.close()
                socket.close()
            }
        }
    }


    private var state : State = State.NotConnected



    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
        if (state is State.Connected) {
            onHandleDisconnect()
        }
    }

    //runs on bg thread
    override fun onHandleIntent(intent: Intent) {
        when(Actions.valueOf(intent.action)) {
            Actions.ACTION_CONNECT -> onHandleConnect(intent.extras.getParcelable(PAR_DEVICE))
            Actions.ACTION_DISCONNECT -> onHandleDisconnect()
        }

    }

    private fun onHandleDisconnect() {
        state.takeIf { it is State.Connected }?.let {
            Log.i(TAG, "Disconnecting")
            try {
                (state as State.Connected).close()
            } catch (ex: IOException) {
                Log.e(TAG, "unable to close socket", ex)
            }
            state = State.NotConnected
        }
    }



    private fun onHandleConnect(maybeDevice: BluetoothDevice?) {
        maybeDevice?.let { device ->
            if (state is State.Connected) {
                onHandleDisconnect()
            }
            Log.i(TAG, "Connecting to ${device.address}")
            state = State.Connecting(device)
            try {
                val serialSocket = device.createRfcommSocketToServiceRecord(Bluetooth.serialPortProfileUUID)
                serialSocket.connect()
                state = State.Connected(device, serialSocket, serialSocket.inputStream, serialSocket.outputStream)
            } catch (ex: Exception) {
                Log.e(TAG, "Unable to connect", ex)
                state = State.NotConnected
            }
        }

    }

}

