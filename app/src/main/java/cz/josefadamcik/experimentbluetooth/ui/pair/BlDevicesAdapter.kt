package cz.josefadamcik.experimentbluetooth.ui.pair

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cz.josefadamcik.experimentbluetooth.R

class BlDevicesAdapter(
        val items: List<BluetoothDevice>,
        val listener: Listener

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