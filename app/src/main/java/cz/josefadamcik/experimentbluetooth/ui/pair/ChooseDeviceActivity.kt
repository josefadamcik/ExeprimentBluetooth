package cz.josefadamcik.experimentbluetooth.ui.pair

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.view.View
import android.content.Intent
import android.view.ViewGroup
import android.widget.Button
import cz.josefadamcik.experimentbluetooth.ActivityRequest.REQUEST_ENABLE_BT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import cz.josefadamcik.experimentbluetooth.Bluetooth
import cz.josefadamcik.experimentbluetooth.bluetooth.BluetoothSPPService
import cz.josefadamcik.experimentbluetooth.R
import cz.josefadamcik.experimentbluetooth.ui.console.SerialConsoleActivity
import java.util.*

/*
 * 00:21:13:00:C1:54
 */

class ChooseDeviceActivity : AppCompatActivity(), BlDevicesAdapter.Listener {
    companion object {
        const val TAG = "ChooseDeviceActivity"
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
            SerialConsoleActivity.start(this, bluetoothDevice)
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
        state = State.Stopped
        SerialConsoleActivity.start(this, device)
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


