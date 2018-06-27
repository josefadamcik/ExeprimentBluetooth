package cz.josefadamcik.experimentbluetooth

import java.util.*

object ActivityRequest {
    const val REQUEST_ENABLE_BT = 1
    const val REQUEST_PERMISSION = 2

}

object Bluetooth {
    val serialPortProfileUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")!!
}


