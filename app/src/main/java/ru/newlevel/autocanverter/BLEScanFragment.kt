package ru.newlevel.autocanverter

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import by.kirich1409.viewbindingdelegate.viewBinding
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.newlevel.autocanverter.databinding.FragmentBleScanBinding
import java.util.UUID

class BLEScanFragment : Fragment(R.layout.fragment_ble_scan) {

    private val bleViewModel by viewModel<BLEViewModel>()
    private val binding: FragmentBleScanBinding by viewBinding()
    private val SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val CHAR_SETTINGS_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    private val bluetoothManager by lazy {
        requireContext().getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter by lazy { bluetoothManager.adapter }
    private val scanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private var settingsCharacteristic: BluetoothGattCharacteristic? = null
    private var scanning = false

    private var gatt: BluetoothGatt? = null


    @SuppressLint("MissingPermission", "SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnScan.setOnClickListener {
            if (!bluetoothAdapter.isEnabled) {
                askEnableBluetooth()
                return@setOnClickListener
            }

            if (!hasBlePermissions()) {
                requestBlePermissions()
                return@setOnClickListener
            }
            if (!hasLocationPermission()) {
                locationPermissionLauncher.launch(ACCESS_FINE_LOCATION)
                return@setOnClickListener
            }

            if (gatt != null) {
                Log.e("AAAA", "gatt != null отключаем")
                gatt?.close()
                gatt = null
                binding.tvStatus.text = "Отключено"
                binding.btnScan.text = "Подключиться"
                binding.settingsLinear.visibility = View.INVISIBLE
            } else if (!scanning) startScan() else stopScan()
        }
        setupCheckBoxes()
        setupSeekBars()
        binding.checkboxEmulateTorqueReduce.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutEmulateTorque.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.gearChangeAlgoritm.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutBarGearChangeAlgoritm.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        bleViewModel.settings.observe(viewLifecycleOwner, Observer { settings ->
            binding.checkboxReduceTorqueOff.isChecked = settings.reduceTorqueOff
            binding.checkboxEmulateTorqueReduce.isChecked = settings.emulateTorqueReduce
            binding.seekBar12.progress = settings.emulatePercent[0]
            binding.seekBar23.progress = settings.emulatePercent[1]
            binding.seekBar34.progress = settings.emulatePercent[2]
            binding.seekBar45.progress = settings.emulatePercent[3]
            binding.seekBar56.progress = settings.emulatePercent[4]
            binding.seekBarGearChangeAlgoritm.progress = settings.shiftTorqueAdjust
            binding.GearChangeAlgoritmPercent.text = settings.shiftTorqueAdjust.toString() + "%"
            binding.checkboxClearBits.isChecked = settings.clearBits
            binding.checkboxEmulateInit.isChecked = settings.emulateInit
            binding.fixCruise.isChecked = settings.cruiseFix
            binding.checkboxEmulateRPM.isChecked = settings.emulateRPM
            binding.checkboxFixChecksum.isChecked = settings.fixChecksum
            binding.checkboxFixTorqueRequest.isChecked = settings.fixTorqueRequest
            binding.fixTorqueReductionRequest.isChecked = settings.fixTorqueReductionRequest
            binding.gearChangeAlgoritm.isChecked = settings.gearChangeAlgoritm
            binding.checkboxEmulateESP.isChecked = settings.emulateESP
            binding.fix3c9.isChecked = settings.fix3c9
            binding.fixInfoEP.isChecked = settings.fixInfoEP
            binding.emulate4speed.isChecked = settings.emulate4speed
            binding.checkboxEmulateClutch.isChecked = settings.emulateClutch
            binding.emulate4speedInfo.isChecked = settings.emulate4speedInfo
        })
        bleViewModel.debug.observe(viewLifecycleOwner, Observer { debug ->
            if(debug.frame305){
                binding.iv305.setImageResource(R.drawable.ok)
            } else {
                binding.iv305.setImageResource(R.drawable.none)
            }
            if(debug.frame30D){
                binding.iv30D.setImageResource(R.drawable.ok)
            }
            if(debug.frame40D){
                binding.iv40D.setImageResource(R.drawable.ok)
            }
            if(debug.frame3CD){
                binding.iv3CD.setImageResource(R.drawable.ok)
            } else {
                binding.iv3CD.setImageResource(R.drawable.none)
            }
            if(debug.frame44D){
                binding.iv44D.setImageResource(R.drawable.ok)
            } else {
                binding.iv44D.setImageResource(R.drawable.none)
            }
            if(debug.frame38D){
                binding.iv38D.setImageResource(R.drawable.ok)
            } else {
                binding.iv38D.setImageResource(R.drawable.none)
            }
            if(debug.frame34D){
                binding.iv34D.setImageResource(R.drawable.ok)
            } else {
                binding.iv34D.setImageResource(R.drawable.none)
            }
            if(debug.frame3AD){
                binding.iv3AD.setImageResource(R.drawable.ok)
            }

        })

    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(requireContext(), "Геолокация нужна для подключения BLE", Toast.LENGTH_SHORT).show()
            }
        }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun setupSeekBars() {
        binding.seekBar12.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sendSettingsToEsp(bleViewModel.setSeekbar(2, binding.seekBar12.progress))
            }
        })
        binding.seekBar23.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sendSettingsToEsp(bleViewModel.setSeekbar(3, binding.seekBar23.progress))
            }
        })
        binding.seekBar34.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sendSettingsToEsp(bleViewModel.setSeekbar(4, binding.seekBar34.progress))
            }
        })
        binding.seekBar45.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sendSettingsToEsp(bleViewModel.setSeekbar(5, binding.seekBar45.progress))
            }
        })
        binding.seekBar56.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sendSettingsToEsp(bleViewModel.setSeekbar(6, binding.seekBar56.progress))
            }
        })
        binding.seekBarGearChangeAlgoritm.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sendSettingsToEsp(bleViewModel.setSeekbar(7, binding.seekBarGearChangeAlgoritm.progress))
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun setupCheckBoxes() {
        binding.resetBTN.setOnClickListener {
            val defaultData = byteArrayOf(0xFC.toByte(), 0x00, 0x0A.toByte(), 0x0A.toByte(), 0x0A.toByte(), 0x0A.toByte(), 0x0A.toByte(), 0x00.toByte())
            sendSettingsToEsp(defaultData)
        }
        binding.fixInfoEP.setOnClickListener {
            sendSettingsToEsp(
                bleViewModel.setBitInSettingsOfTwo(
                    1,
                    3,
                    1,
                    4,
                    binding.fixInfoEP.isChecked,
                    if (binding.emulate4speed.isChecked) !binding.emulate4speed.isChecked else binding.emulate4speed.isChecked
                )
            )
        }
        binding.emulate4speed.setOnClickListener {
            sendSettingsToEsp(
                bleViewModel.setBitInSettingsOfTwo(
                    1,
                    4,
                    1,
                    3,
                    binding.emulate4speed.isChecked,
                    if (binding.fixInfoEP.isChecked) !binding.fixInfoEP.isChecked else binding.fixInfoEP.isChecked
                )
            )
        }
        binding.checkboxReduceTorqueOff.setOnClickListener {
            sendSettingsToEsp(
                bleViewModel.setBitInSettingsOfTwo(
                    0,
                    0,
                    0,
                    1,
                    binding.checkboxReduceTorqueOff.isChecked,
                    if (binding.checkboxEmulateTorqueReduce.isChecked) !binding.checkboxEmulateTorqueReduce.isChecked else binding.checkboxEmulateTorqueReduce.isChecked
                )
            )
        }
        binding.checkboxEmulateTorqueReduce.setOnClickListener {
            sendSettingsToEsp(
                bleViewModel.setBitInSettingsOfTwo(
                    0,
                    1,
                    0,
                    0,
                    binding.checkboxEmulateTorqueReduce.isChecked,
                    if (binding.checkboxReduceTorqueOff.isChecked) !binding.checkboxReduceTorqueOff.isChecked else binding.checkboxReduceTorqueOff.isChecked
                )
            )

        }

//        binding.seekBar12.progress = settings.emulatePercent[0] / 10
//        binding.seekBar23.progress = settings.emulatePercent[1] / 10
//        binding.seekBar34.progress = settings.emulatePercent[2] / 10
//        binding.seekBar56.progress = settings.emulatePercent[3] / 10
        binding.emulate4speedInfo.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(1, 7, binding.emulate4speedInfo.isChecked))
        }
        binding.fix3c9.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(1, 6, binding.fix3c9.isChecked))
        }
        binding.fixCruise.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(1, 5, binding.fixCruise.isChecked))
        }
        binding.checkboxClearBits.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(0, 2, binding.checkboxClearBits.isChecked))
        }
        binding.checkboxEmulateInit.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(0, 3, binding.checkboxEmulateInit.isChecked))
        }
        binding.checkboxEmulateRPM.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(0, 4, binding.checkboxEmulateRPM.isChecked))
        }
        binding.checkboxFixChecksum.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(0, 5, binding.checkboxFixChecksum.isChecked))
        }
        binding.checkboxFixTorqueRequest.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(0, 6, binding.checkboxFixTorqueRequest.isChecked))
        }
        binding.fixTorqueReductionRequest.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(0, 7, binding.fixTorqueReductionRequest.isChecked))
        }
        binding.gearChangeAlgoritm.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(1, 0, binding.gearChangeAlgoritm.isChecked))
        }
        binding.checkboxEmulateClutch.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(1, 1, binding.checkboxEmulateClutch.isChecked))
        }
        binding.checkboxEmulateESP.setOnClickListener {
            sendSettingsToEsp(bleViewModel.setBitInSettings(1, 2, binding.checkboxEmulateESP.isChecked))
        }
    }

    // --- Permissions launcher
    @SuppressLint("MissingPermission")
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            val ok = res.values.all { it }
            if (ok) {
                binding.tvStatus.text = "Разрешения получены"
                startScan()
            } else {
                binding.tvStatus.text = "Нет разрешений на BLE"
                requestBlePermissions()
            }
        }

    // --- Enable BT launcher
    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            if (bluetoothAdapter.isEnabled) {
                binding.tvStatus.text = "Bluetooth включен"
            } else {
                binding.tvStatus.text = "Bluetooth выключен"
            }
        }


    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            //Log.e("AAAAA", "onScanResult")
            val device = result.device ?: return
            //  Log.e("AAAAA", device.name)
            val name = result.scanRecord?.deviceName ?: device.name
            if (name == "AutoCANverter" && gatt == null) {
                if (ActivityCompat.checkSelfPermission(requireContext(), BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
                connect(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Toast.makeText(requireContext(), "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
            scanning = false
        }
    }

// ---------------- Permissions ----------------

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPerm(BLUETOOTH_SCAN) &&
                    hasPerm(BLUETOOTH_CONNECT)
        } else {
            // до 12 андроида нужно Location для скана
            hasPerm(ACCESS_FINE_LOCATION)
        }
    }

    private fun hasPerm(p: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), p) == PackageManager.PERMISSION_GRANTED

    private fun requestBlePermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                BLUETOOTH_SCAN,
                BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(perms)
    }

    private fun askEnableBluetooth() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBtLauncher.launch(intent)
    }

// ---------------- Scan ----------------

    @RequiresPermission(BLUETOOTH_SCAN)
    private fun startScan() {
        Log.e("AAAA", "startScan")
        if (scanning) return
        binding.tvStatus.text = "Подключение..."
        binding.btnScan.text = "Стоп"
        scanning = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()
        val filters = listOf(
            ScanFilter.Builder()
                .setDeviceName("AutoCANverter")
                .build()
        )
        scanner.startScan(filters, settings, scanCallback)

        // автостоп через 10 сек
        view?.postDelayed({
            if (scanning) stopScan()
        }, 10_000)
    }

    @RequiresPermission(BLUETOOTH_SCAN)
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        if (gatt == null) {
            binding.btnScan.text = "Подключиться"
            binding.tvStatus.text = "Скан остановлен"
        }
        scanner.stopScan(scanCallback)
    }

// ---------------- Connect ----------------

    @RequiresPermission(BLUETOOTH_CONNECT)
    private fun connect(device: BluetoothDevice) {
        if (gatt == null)
            binding.tvStatus.text = "Подключение к ${device.name}..."
        if (!hasBlePermissions()) {
            binding.tvStatus.text = "Нет разрешений для подключения"
            return
        }

        gatt?.close()
        gatt = null
        gatt = device.connectGatt(requireContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.e("AAAA", "STATE_CONNECTED")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e("AAAA", "STATE_DISCONNECTED")
                requireActivity().runOnUiThread {
                    binding.tvStatus.text = "Отключено"
                    binding.btnScan.text = "Подключиться"
                    binding.settingsLinear.visibility = View.INVISIBLE
                }
                gatt.close()
            }
        }

        @RequiresPermission(BLUETOOTH_SCAN)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            requireActivity().runOnUiThread {
                binding.loadingIcon.visibility = View.VISIBLE
                val rotate = ObjectAnimator.ofFloat(binding.loadingIcon, "rotation", 0f, 360f)
                rotate.duration = 2000
                rotate.repeatCount = ValueAnimator.INFINITE
                rotate.start()
                binding.tvStatus.text = "Загрузка..."
                binding.btnScan.text = "Отключиться"
            }
            val service = gatt.getService(SERVICE_UUID) ?: return
            val ch = service.getCharacteristic(CHAR_SETTINGS_UUID) ?: return

            // включаем notify
            if (ActivityCompat.checkSelfPermission(requireContext(), BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            gatt.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            cccd?.let {
                it.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                gatt.writeDescriptor(it)  // асинхронная запись
            }
            gatt.readCharacteristic(ch)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.e("AAAA", "onCharacteristicRead")
            val value = characteristic.value
            Log.e("AAAA", value.joinToString(" ") { String.format("%02X", it) })
            if (characteristic.uuid == CHAR_SETTINGS_UUID) {
                Log.e("AAAA", "characteristic.uuid == CHAR_SETTINGS_UUID")
                val data = characteristic.value
                settingsCharacteristic = characteristic
                if (data.size == 9) {
                    applySettingsFromEsp(data)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.e("AAAA", "onCharacteristicRead")
            val value = characteristic.value
            Log.e("AAAA", value.joinToString(" ") { String.format("%02X", it) })
            if (characteristic.uuid == CHAR_SETTINGS_UUID) {
                Log.e("AAAA", "characteristic.uuid == CHAR_SETTINGS_UUID")
                val data = characteristic.value
                settingsCharacteristic = characteristic
                if (data.size == 9) {
                    applySettingsFromEsp(data)
                }
            }
        }
    }

    fun applySettingsFromEsp(data: ByteArray) {
        Log.e("AAAA", "applySettingsFromEsp")
        if (data.size < 9) return
        Log.e("AAAA", "data.size < 8")
        bleViewModel.setSettings(data)
        requireActivity().runOnUiThread {
            binding.tvStatus.text = "Подключено"
            binding.loadingIcon.visibility = View.GONE
            binding.settingsLinear.visibility = View.VISIBLE
        }
    }

    // settings[0] bit mapping
// bit0 - forceDisableTorqueReduce
// bit1 - emulateTorqueReduce
// bit2 - clearBits0(KPB)
// bit3 - emulateInit
// bit4 - emulateRpm
// bit5 - fixChecksum
// bit6 - fixTorqueRequest
// bit7 - fixTorqueRreductionRequest
// settings[1] bit mapping
// bit0 - gearChange
// bit1 - emilatecluch
// bit2 - emilatecESP
// bit7 - RESET

    @RequiresPermission(BLUETOOTH_CONNECT)
    fun sendSettingsToEsp(bytes: ByteArray) {
        Log.e("AAAA", "sendSettingsToEsp")
        val ch = settingsCharacteristic ?: return
        ch.value = bytes
        val value = ch.value
        Log.e("AAAA", value.joinToString(" ") { String.format("%02X", it) })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt?.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            gatt?.writeCharacteristic(ch)
        }
        gatt?.readCharacteristic(ch)
    }

    @RequiresPermission(BLUETOOTH_SCAN)
    override fun onDestroyView() {
        super.onDestroyView()
        stopScan()
        if (ActivityCompat.checkSelfPermission(requireContext(), BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            gatt = null
            return
        }
        gatt?.close()
        gatt = null
    }

}
