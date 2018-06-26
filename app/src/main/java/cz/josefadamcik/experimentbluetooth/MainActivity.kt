package cz.josefadamcik.experimentbluetooth

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.bluetooth.BluetoothAdapter
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

/*
 * 00:21:13:00:C1:54
 */

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var snackbar: Snackbar? = null
    private lateinit var toolbar: Toolbar
    private lateinit var list: RecyclerView
    private lateinit var buttonScan: Button
    private lateinit var progressScan: ProgressBar


    private var adapter = BlDevicesAdapter(emptyList<BluetoothDevice>())
    private var discoveredDevices : MutableList<BluetoothDevice> = ArrayList()
    private var state: State = State.Init

    sealed class State {
        object Init : State()
        object WaitingForPermission : State()
        object DisplayingBoundDevices : State()
        object Scanning : State()
        object Stopped : State()
    }


    private val discoveryBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d("BT", String.format("broadcast received %s", action))
            if (BluetoothDevice.ACTION_FOUND == action) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                discoveredDevices.add(device)
                adapter.notifyItemInserted(discoveredDevices.size - 1)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.my_toolbar)
        list = findViewById(R.id.list)
        buttonScan  = findViewById(R.id.button_scan)
        buttonScan.setOnClickListener(View.OnClickListener { bluetoothAdapter?.let{startScanForDevices(it)} })
        progressScan = findViewById(R.id.progress_scan)
        setSupportActionBar(toolbar);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        list.adapter = adapter
    }



    override fun onStart() {
        super.onStart()
        registerReceiver(discoveryBroadcastReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
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
            bluetoothAdapter?.let { displayBondedDevices(it) }
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

    private fun stopDiscovery() {
        state = State.Stopped
        progressScan.isVisible = false
        buttonScan.isVisible = true
        bluetoothAdapter?.cancelDiscovery()
    }

    private fun startScanForDevices(bluetoothAdapter: BluetoothAdapter) {
        list.adapter = null
        adapter = BlDevicesAdapter(discoveredDevices)
        list.adapter = adapter
        discoveredDevices.clear()
        buttonScan.isVisible = false
        progressScan.isVisible = true
        state = State.Scanning

        if (!bluetoothAdapter.startDiscovery()) {
            Snackbar.make(toolbar, R.string.err_bluetooth_unable_to_start_discovery, Snackbar.LENGTH_LONG).show()
        }
    }


    private fun MainActivity.initBluetooth(bluetoothAdapter: BluetoothAdapter?) {
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

    private fun displayBondedDevices(bluetoothAdapter: BluetoothAdapter) {
        state = State.DisplayingBoundDevices
        if (bluetoothAdapter.bondedDevices.isNotEmpty()) {
            adapter = BlDevicesAdapter(ArrayList(bluetoothAdapter.bondedDevices))
            list.adapter = adapter
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
        val items: List<BluetoothDevice>

): RecyclerView.Adapter<BlDevicesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.device_list_item, parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = items[position]
        holder.text.text = "${device.name} (${device.address})"
        holder.subtext.text = "type: ${device.type} state ${device.bondState}"
    }

    class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        var text: TextView = view.findViewById(R.id.text)
        var subtext: TextView = view.findViewById(R.id.subtext)
    }
}