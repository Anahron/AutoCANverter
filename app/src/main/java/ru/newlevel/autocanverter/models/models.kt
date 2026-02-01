package ru.newlevel.autocanverter.models

import android.bluetooth.BluetoothDevice

data class BleDeviceItem(
    val device: BluetoothDevice,
    var name: String?,
    var rssi: Int
)