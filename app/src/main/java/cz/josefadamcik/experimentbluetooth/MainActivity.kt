package cz.josefadamcik.experimentbluetooth

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.view.View
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import cz.josefadamcik.experimentbluetooth.ActivityRequest.REQUEST_ENABLE_BT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Binder
import android.util.Log
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import cz.josefadamcik.experimentbluetooth.ActivityRequest.REQUEST_PERMISSION
import java.util.*

/*
 * 00:21:13:00:C1:54
 */

class MainActivity : AppCompatActivity(), BlDevicesAdapter.Listener {
    companion object {
        const val TAG = "MainActivity"

    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var snackbar: Snackbar? = null
    private lateinit var toolbar: Toolbar
    private lateinit var list: RecyclerView
    private lateinit var buttonScan: Button
    private lateinit var progressScan: ProgressBar
    private lateinit var buttonStopScan: Button
    private lateinit var containerScanInProgress: ViewGroup


    private var adapter = BlDevicesAdapter(emptyList<BluetoothDevice>(), this)
    private var discoveredDevices : MutableList<BluetoothDevice> = ArrayList()
    private var state: State = State.Init
    sealed class State {
        object Init : State()
        object WaitingForPermission : State()
        object DisplayingBoundDevices : State()
        object Scanning : State()
        object Stopped : State()
        data class Binding(val device: BluetoothDevice) : State()
        data class ServiceDiscovery(val device: BluetoothDevice) : State()

    }


    private val discoveryBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d("BT", String.format("broadcast received %s", action))
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    onNewDeviceDiscovered(device)
                }
                BluetoothDevice.ACTION_CLASS_CHANGED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    onDeviceUpdated(device)
                }
                BluetoothDevice.ACTION_UUID -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    onServiceDiscoveryFinished(device);
                }
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.my_toolbar)
        list = findViewById(R.id.list)
        buttonScan  = findViewById(R.id.button_scan)
        buttonStopScan = findViewById(R.id.button_stop_scan)
        containerScanInProgress = findViewById(R.id.container_scan_inprogress)
        progressScan = findViewById(R.id.progress_scan)

        buttonScan.setOnClickListener { bluetoothAdapter?.let{startDiscovery(it)} }
        buttonStopScan.setOnClickListener { stopDiscovery() }

        setSupportActionBar(toolbar);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        adapter = BlDevicesAdapter(discoveredDevices, this)
        list.adapter = adapter
    }



    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND)
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        intentFilter.addAction(BluetoothDevice.ACTION_CLASS_CHANGED)
        intentFilter.addAction(BluetoothDevice.ACTION_NAME_CHANGED)
        intentFilter.addAction(BluetoothDevice.ACTION_UUID)

        registerReceiver(discoveryBroadcastReceiver, intentFilter)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermission()
        } else {
            initBluetooth(bluetoothAdapter)
        }
    }

    private fun requestPermission() {
        state = State.WaitingForPermission
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_PERMISSION)
    }

    override fun onBackPressed() {
        if (state == State.Scanning) {
            stopDiscovery()
        } else {
            super.onBackPressed()
        }
    }

    override fun onStop() {
        unregisterReceiver(discoveryBroadcastReceiver)
        super.onStop()
        if (state == State.Scanning) {
            stopDiscovery()
        }
    }



    override fun onItemSelected(position: Int, bluetoothDevice: BluetoothDevice) {
        Log.d(TAG, "Selected $position, ${bluetoothDevice.address}")

         when (bluetoothDevice.bondState) {
            BluetoothDevice.BOND_NONE -> bindDevice(bluetoothDevice)
            BluetoothDevice.BOND_BONDING -> Snackbar.make(toolbar, R.string.err_device_already_bonding, Snackbar.LENGTH_LONG).show()
            BluetoothDevice.BOND_BONDED -> connectDevice(bluetoothDevice)
            else -> Log.e(TAG, "Unsupported state ${bluetoothDevice.bondState}")
        }
    }

    private fun bindDevice(bluetoothDevice: BluetoothDevice) {
        Log.d(TAG, "bind to ${bluetoothDevice.address}")
        if (state == State.Scanning) {
            stopDiscovery()
        }

        if (bluetoothDevice.createBond()) {
            state = State.Binding(bluetoothDevice)
        } else {
            Snackbar.make(toolbar, R.string.err_unable_to_start_device_binding, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun connectDevice(bluetoothDevice: BluetoothDevice) {
        Log.d(TAG, "connect to ${bluetoothDevice.address}")
        if (state == State.Scanning) {
            stopDiscovery()
        }

        //request
        if (bluetoothDevice.uuids == null) {
            startServiceDiscoveryForDevice(bluetoothDevice)
        } else {
            findUuid(bluetoothDevice)
        }
    }



    private fun startServiceDiscoveryForDevice(bluetoothDevice: BluetoothDevice) {
        if (bluetoothDevice.fetchUuidsWithSdp()) {
            state = State.ServiceDiscovery(bluetoothDevice)
        } else {
            Snackbar.make(toolbar, R.string.err_unable_to_start_service_descovery, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun initBluetooth(bluetoothAdapter: BluetoothAdapter?) {
        snackbar?.dismiss()
        if (bluetoothAdapter == null) {
            snackbar = Snackbar.make(toolbar, R.string.err_bluetooth_not_supported, Snackbar.LENGTH_INDEFINITE)
            snackbar?.show()
        } else if (!bluetoothAdapter.isEnabled) {
            snackbar = Snackbar.make(toolbar, R.string.err_bluetooth_not_nabled, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.action_enable, View.OnClickListener { startActivityForBluetoothEnable()})
            snackbar?.show()
            //note:
            /*
            Optionally, your application can also listen for the ACTION_STATE_CHANGED broadcast intent, which the system broadcasts whenever the Bluetooth state changes. This broadcast contains the extra fields EXTRA_STATE and EXTRA_PREVIOUS_STATE, containing the new and old Bluetooth states, respectively. Possible values for these extra fields are STATE_TURNING_ON, STATE_ON, STATE_TURNING_OFF, and STATE_OFF. Listening for this broadcast can be useful if your app needs to detect runtime changes made to the Bluetooth state.
             */
        } else {
            buttonScan.isEnabled = true
            displayBondedDevices(bluetoothAdapter)
        }
    }



    private fun startDiscovery(bluetoothAdapter: BluetoothAdapter) {
        buttonScan.isVisible = false
        containerScanInProgress.isVisible = true
        state = State.Scanning

        if (!bluetoothAdapter.startDiscovery()) {
            Snackbar.make(toolbar, R.string.err_bluetooth_unable_to_start_discovery, Snackbar.LENGTH_LONG).show()
        }
    }



    private fun stopDiscovery() {
        state = State.Stopped
        containerScanInProgress.isVisible = false
        buttonScan.isVisible = true
        bluetoothAdapter?.cancelDiscovery()
    }


    private fun onNewDeviceDiscovered(device: BluetoothDevice) {
        discoveredDevices.add(device)
        adapter.notifyItemInserted(discoveredDevices.size - 1)
    }


    private fun onDeviceUpdated(device: BluetoothDevice) {
        for (i in 0 until discoveredDevices.size) {
            val oldDevice = discoveredDevices[i]
            if (oldDevice.address == device.address) {
                discoveredDevices[i] = device
                adapter.notifyItemChanged(i)
            }
        }

        state.let { state ->
            if (device.bondState == BluetoothDevice.BOND_BONDED && state is State.Binding && state.device.address == device.address) {
                //The device we were waiting for is bound,yay!
                onDeviceBound(device)
            }
        }
    }

    private fun onDeviceBound(device: BluetoothDevice) {
        Log.d(TAG, "Device bound ${device.address}")
        state = State.Stopped
    }

    private fun onServiceDiscoveryFinished(device: BluetoothDevice) {
        Log.d(TAG, "Service discovery for ${device.address} finished")
        onDeviceUpdated(device)
        state =  State.Stopped
        findUuid(device)
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
            Log.i(TAG, "uuid ${uuid.uuid}")
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



    private fun displayBondedDevices(bluetoothAdapter: BluetoothAdapter) {
        state = State.DisplayingBoundDevices
        if (bluetoothAdapter.bondedDevices.isNotEmpty()) {
            discoveredDevices.clear()
            discoveredDevices.addAll(bluetoothAdapter.bondedDevices)
            adapter.notifyDataSetChanged()
        }
    }


    private fun startActivityForBluetoothEnable() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            REQUEST_ENABLE_BT -> onRequestEnableResult(resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PERMISSION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    state = State.Init
                    initBluetooth(bluetoothAdapter)
                } else {
                    snackbar = Snackbar.make(toolbar, R.string.err_permission_needed, Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.action_grant_permission, View.OnClickListener {
                                snackbar?.dismiss()
                                requestPermission()
                            })
                }
                return
            }
            else -> { }
        }
    }

    private fun onRequestEnableResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            initBluetooth(bluetoothAdapter)
        }
    }
}


class BlDevicesAdapter(
        val items: List<BluetoothDevice>,
        val listener: BlDevicesAdapter.Listener

): RecyclerView.Adapter<BlDevicesAdapter.ViewHolder>() {
    interface Listener {
        fun onItemSelected(position: Int, bluetoothDevice: BluetoothDevice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.device_list_item, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = items[position]
        holder.text.text = "${device.name} (${device.address})"
        val bondState = when (device.bondState) {
            BluetoothDevice.BOND_NONE -> "none"
            BluetoothDevice.BOND_BONDING -> "bonding"
            BluetoothDevice.BOND_BONDED -> "bonded"
            else -> "n/a"
        }

        val deviceType = when(device.type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classic"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "dual"
            BluetoothDevice.DEVICE_TYPE_LE -> "LE"
            BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "unknown"
            else -> "n/a"
        }

        val deviceMajorClass =  when(device.bluetoothClass.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "audio/video"
            BluetoothClass.Device.Major.COMPUTER -> "computer"
            BluetoothClass.Device.Major.HEALTH -> "health"
            BluetoothClass.Device.Major.IMAGING -> "imaging"
            BluetoothClass.Device.Major.MISC -> "misc"
            BluetoothClass.Device.Major.NETWORKING -> "networking"
            BluetoothClass.Device.Major.PERIPHERAL -> "peripheral"
            BluetoothClass.Device.Major.PHONE -> "phone"
            BluetoothClass.Device.Major.TOY -> "toy"
            BluetoothClass.Device.Major.UNCATEGORIZED -> "uncategorized"
            BluetoothClass.Device.Major.WEARABLE -> "wearable"
            else -> "n/a"
        }

        holder.subtext.text = "type: $deviceType class: $deviceMajorClass state: $bondState"
    }

    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        var text: TextView = view.findViewById(R.id.text)
        var subtext: TextView = view.findViewById(R.id.subtext)
        init {
            view.setOnClickListener { listener.onItemSelected( adapterPosition , items[adapterPosition])}
        }
    }
}