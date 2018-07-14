package cz.josefadamcik.experimentbluetooth.ui.console

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.snackbar.Snackbar
import cz.josefadamcik.experimentbluetooth.Bluetooth
import cz.josefadamcik.experimentbluetooth.R
import cz.josefadamcik.experimentbluetooth.bluetooth.BluetoothSPPService
import cz.josefadamcik.experimentbluetooth.ui.pair.ChooseDeviceActivity
import kotlinx.android.synthetic.main.activity_serial_console.*


class SerialConsoleActivity : AppCompatActivity() {
    companion object {
        const val TAG = "ChooseDeviceActivity"
        private const val PAR_DEVICE = "device"

        fun start(context: Context, device: BluetoothDevice) {
            val intent = Intent(context, SerialConsoleActivity::class.java)
            intent.putExtra(PAR_DEVICE, device)
            context.startActivity(intent)
        }
    }

    private lateinit var device: BluetoothDevice
    private lateinit var adapter: ConsoleAdapter
    private val items: MutableList<String> = mutableListOf()
    private val bluetoothBroadcastReceiver =  BluetoothBroadcastReceiver()

    private inner class BluetoothBroadcastReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val broadcast = BluetoothSPPService.obtainBroadcastMessage(intent)
            when(broadcast) {
                is BluetoothSPPService.BluetoothBroadcast.Invalid -> Log.e(TAG, "Invalid broadcast")
                is BluetoothSPPService.BluetoothBroadcast.LineReceived -> onNewLine(broadcast.line)

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_serial_console)
        device = intent.extras[PAR_DEVICE] as BluetoothDevice

        toolbar.title = device.name
        adapter = ConsoleAdapter(items)
        list.adapter =  adapter
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(bluetoothBroadcastReceiver, BluetoothSPPService.createBroadcastIntentFilter(this))
        findUuid(bluetoothDevice = device)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(bluetoothBroadcastReceiver)
    }

    private fun onNewLine(line: String) {
        items.add(line)
        adapter.notifyItemInserted(items.lastIndex)
    }

    private fun findUuid(bluetoothDevice: BluetoothDevice) {
        if (bluetoothDevice.uuids == null || bluetoothDevice.uuids.isEmpty()) {
            Snackbar.make(toolbar, R.string.msg_no_uuids_found, Snackbar.LENGTH_LONG).show()
            return
        }
        /*
        This should be UUID for SPP (serial port profile): 00001101-0000-1000-8000-00805f9b34fb, it's mentioned repeatedly on SO ect

        HC-06 reports also following ie service discovery.
        00000000-0000-1000-8000-00805f9b34fb

        According to the https://www.bluetooth.com/specifications/assigned-numbers/service-discovery,
        this is a base UUID.

        1101 is also mentioned above, in the table "Table 2: Service Class Profile Identifiers":
        "SerialPort	0x1101	Serial Port Profile (SPP)  NOTE: The example SDP record in SPP v1.0 does not include a BluetoothProfileDescriptorList attribute, but some implementations may also use this UUID for the Profile Identifier.	Service Class/ Profile"

        Formula for computation of an 128-bit UUID from a 16/32bit UUID is (according to the core bluetooth spec):
           128_bit_value = 16_bit_value * 2^96 + Bluetooth_Base_UUID
           and that is correct that:
           0x1101 * 2^96 + Bluetooth_Base_UUI = 00001101-0000-1000-8000-00805f9b34fb
         */
        var found = false
        for (uuid in bluetoothDevice.uuids) {
            Log.i(ChooseDeviceActivity.TAG, "uuid ${uuid.uuid}")
            if (uuid.uuid == Bluetooth.serialPortProfileUUID) {
                found = true
            }
        }
        if (!found) {
            Snackbar.make(toolbar, R.string.err_no_spp_on_device, Snackbar.LENGTH_LONG).show()
        } else {
            startService(BluetoothSPPService.createConnectIntent(this, bluetoothDevice))
        }
    }

}
