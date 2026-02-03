package ru.newlevel.autocanverter

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel


class BLEViewModel : ViewModel() {
    private val _settings = MutableLiveData(ESP32Settings())
    val settings: LiveData<ESP32Settings> = _settings

    fun setBitInSettings(byteIndex: Int, bitIndex: Int, enabled: Boolean): ByteArray {
        val currentBytes = settings.value.byteSettings
        val newBytes = currentBytes.copyOf()
        newBytes[byteIndex] = if (enabled) {
            (newBytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
        } else {
            (newBytes[byteIndex].toInt() and (1 shl bitIndex).inv()).toByte()
        }
        setSettings(newBytes)
        return newBytes
    }
    fun setBitInSettingsOfTwo(byteIndex: Int, bitIndex: Int, byteIndex2: Int, bitIndex2: Int, enabled: Boolean, enabled2: Boolean): ByteArray {
        val currentBytes = settings.value.byteSettings
        val newBytes = currentBytes.copyOf()
        newBytes[byteIndex] = if (enabled) {
            (newBytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
        } else {
            (newBytes[byteIndex].toInt() and (1 shl bitIndex).inv()).toByte()
        }
        newBytes[byteIndex2] = if (enabled2) {
            (newBytes[byteIndex2].toInt() or (1 shl bitIndex2)).toByte()
        } else {
            (newBytes[byteIndex2].toInt() and (1 shl bitIndex2).inv()).toByte()
        }
        setSettings(newBytes)
        return newBytes
    }
    fun setSeekbar(byte: Int, progress: Int): ByteArray{
        val currentBytes = settings.value.byteSettings
        val newBytes = currentBytes.copyOf()
        newBytes[byte] = progress.toByte()
        setSettings(newBytes)
        return newBytes
    }

    fun setSettings(data: ByteArray) {
        val flags = data[0].toInt() and 0xFF
        val b0 = flags and (1 shl 0) != 0
        val b1 = flags and (1 shl 1) != 0
        val b2 = flags and (1 shl 2) != 0
        val b3 = flags and (1 shl 3) != 0
        val b4 = flags and (1 shl 4) != 0
        val b5 = flags and (1 shl 5) != 0
        val b6 = flags and (1 shl 6) != 0
        val b7 = flags and (1 shl 7) != 0
        val flags1 = data[1].toInt() and 0xFF
        val b10 = flags1 and (1 shl 0) != 0
        val b11 = flags1 and (1 shl 1) != 0
        val b12 = flags1 and (1 shl 2) != 0
        val b13 = flags1 and (1 shl 3) != 0
        val b14 = flags1 and (1 shl 4) != 0
        val b15 = flags1 and (1 shl 5) != 0
        val b16 = flags1 and (1 shl 6) != 0
        val b17 = flags1 and (1 shl 7) != 0
        val newSettings = settings.value?.copy(
            reduceTorqueOff = b0,
            emulateTorqueReduce = b1,
            clearBits = b2,
            emulateInit = b3,
            emulateRPM = b4,
            fixChecksum = b5,
            fixTorqueRequest = b6,
            fixTorqueReductionRequest = b7,
            gearChangeAlgoritm = b10,
            emulateClutch = b11,
            emulateESP = b12,
            fixInfoEP = b13,
            fixInfoEW = b14,
            cruiseFix = b15,
            fix3c9 = b16,
            byteSettings =  data.copyOf(),
            emulatePercent = listOf(data[2].toInt(), data[3].toInt(), data[4].toInt(),data[5].toInt(), data[6].toInt()),
            shiftTorqueAdjust = data[7].toInt()
        )
        _settings.postValue(newSettings)
    }
}

data class ESP32Settings(
    var reduceTorqueOff: Boolean = false,
    var emulateTorqueReduce: Boolean = false,
    var emulatePercent: List<Int> = listOf(10, 10, 10, 10, 10), // 1-2,2-3,3-4, 4-5, 5-6
    var shiftTorqueAdjust: Int = 0,      // -30..30%
    var gearChangeAlgoritm: Boolean = false,
    var clearBits: Boolean = false,
    var emulateInit: Boolean = false,
    var emulateRPM: Boolean = false,
    var fixChecksum: Boolean = false,
    var fixTorqueRequest: Boolean = false,
    var fixTorqueReductionRequest: Boolean = false,
    var emulateClutch: Boolean = false,
    var emulateESP: Boolean = false,
    var fixInfoEP: Boolean = false,
    var fixInfoEW: Boolean = false,
    var cruiseFix: Boolean = false,
    var fix3c9: Boolean = false,
    var byteSettings: ByteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ESP32Settings

        if (reduceTorqueOff != other.reduceTorqueOff) return false
        if (emulateTorqueReduce != other.emulateTorqueReduce) return false
        if (gearChangeAlgoritm != other.gearChangeAlgoritm) return false
        if (clearBits != other.clearBits) return false
        if (emulateInit != other.emulateInit) return false
        if (emulateRPM != other.emulateRPM) return false
        if (fixChecksum != other.fixChecksum) return false
        if (fixTorqueRequest != other.fixTorqueRequest) return false
        if (fixTorqueReductionRequest != other.fixTorqueReductionRequest) return false
        if (emulateClutch != other.emulateClutch) return false
        if (emulateESP != other.emulateESP) return false
        if (emulatePercent != other.emulatePercent) return false
        if (shiftTorqueAdjust != other.shiftTorqueAdjust) return false
        if (!byteSettings.contentEquals(other.byteSettings)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = reduceTorqueOff.hashCode()
        result = 31 * result + emulateTorqueReduce.hashCode()
        result = 31 * result + gearChangeAlgoritm.hashCode()
        result = 31 * result + clearBits.hashCode()
        result = 31 * result + emulateInit.hashCode()
        result = 31 * result + emulateRPM.hashCode()
        result = 31 * result + fixChecksum.hashCode()
        result = 31 * result + fixTorqueRequest.hashCode()
        result = 31 * result + fixTorqueReductionRequest.hashCode()
        result = 31 * result + emulateClutch.hashCode()
        result = 31 * result + emulateESP.hashCode()
        result = 31 * result + emulatePercent.hashCode()
        result = 31 * result + shiftTorqueAdjust.hashCode()
        result = 31 * result + byteSettings.contentHashCode()
        return result
    }
}