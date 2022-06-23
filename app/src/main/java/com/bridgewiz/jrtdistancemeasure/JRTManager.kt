package com.bridgewiz.jrtdistancemeasure

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.*

class JRTManager(private val _usbManager: UsbManager) : SerialInputOutputManager.Listener {

    private lateinit var currentPort: UsbSerialPort
    private lateinit var usbIoManager: SerialInputOutputManager
    private lateinit var currentDevice: UsbDevice
    private lateinit var currentDriver: UsbSerialDriver
    val device: UsbDevice
        get() = currentDevice

    private val lastResponse = MutableLiveData("")
    val response: LiveData<String>
        get() = lastResponse

    private val _resultString = MutableLiveData("")
    val resultString: LiveData<String>
        get() = _resultString

    private val lastDistance = MutableLiveData(0L)
    val distance: LiveData<Long>
        get() = lastDistance

    private var lastSignalQuality = 0L
    val signalQuality: Long
        get() = lastSignalQuality

    val usbManager: UsbManager
        get() = _usbManager


    /**
     * Probes all available USB devices and tries to connect to the first one. If this function
     * returns ConnectionStatus.DeviceRequiresPermission, the Activity should ask for user
     * permission and call this method again.
     * This function is async because it needs to wait for 1 seconds before initializing
     * communications, inside the openPort method
     * @return Connection status
     */
    suspend fun connect(): ConnectionResult {

        val result: ConnectionResult

        // on every connect call, the usb ports are probed to detect changes
        val availableDrivers: List<UsbSerialDriver> =
            UsbSerialProber.getDefaultProber().findAllDrivers(_usbManager)

        result = if (availableDrivers.isEmpty())
            ConnectionResult.NoDeviceFound
        else {
            currentDriver = availableDrivers[0]
            currentDevice = currentDriver.device
            if (_usbManager.hasPermission(currentDevice)) {
                openPort()
            } else {
                ConnectionResult.DeviceRequiresPermission
            }

        }

        return result
    }

    /**
     * Disconnects from the USB device and closes the port
     * @return Connection result
     */
    fun disconnect(): ConnectionResult {
        currentPort.let {
            if (it.isOpen) {
                usbIoManager.stop()
                it.close()
            }
        }
        return ConnectionResult.Disconnected
    }


    /**
     * Powers up the laser
     */
    fun openLaser() {
        currentPort.write(laserOnHexString.decodeHex(), 100)
    }

    /**
     * Powers down the laser
     */
    fun closeLaser() {
        currentPort.write(laserOffHexString.decodeHex(), 100)
    }

    /**
     * Reads the latest status of the model
     */
    fun readStatus() {
        currentPort.write(readStatusHexString.decodeHex(), 100)
    }

    /**
     * Performs a single measurement with AUTO setting.
     * Refer to M8xx-JRT user manual Section 6.5 for measure modes.
     */
    fun singleMeasureAuto() {
        currentPort.write(oneShotAutoHexString.decodeHex(), 100)
    }

    /**
     * Starts continuous measuring sequence with AUTO setting
     * Refer to M8xx-JRT user manual Section 6.5 for measure modes.
     */
    fun startContinuousMeasure() {
        currentPort.write(continuousAutoHexString.decodeHex(), 100)
    }

    /**
     * Stops continuous measure sequence
     */
    fun stopContinuousMeasure() {
        currentPort.write(stopContinuousHexString.decodeHex(), 100)
    }


    /**
     * Tries to open the current port. This function is async because it needs to wait
     * for 1 seconds after opening the port, before sending the initialization command
     */
    private suspend fun openPort(): ConnectionResult {
        var result = ConnectionResult.ConnectionError
        try {
            val connection: UsbDeviceConnection = _usbManager.openDevice(currentDriver.device)
            currentPort = currentDriver.ports[0]
            currentPort.open(connection)
            currentPort.setParameters(baudRate, dataBits, stopBits, parity)
            usbIoManager = SerialInputOutputManager(currentPort, this)
            usbIoManager.start()

            withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
                // send the "set baud rate to auto" command to initialize communications
                // wait for 1 sec before sending the command so that the port is ready
                delay(1000)
                startComm()
                result = ConnectionResult.Connected
            }

        } catch (e: Exception) {
            _resultString.value = e.message
        }
        return result
    }

    /**
     * Starts the communications between the sensor and device by sending 'set baud rate' command
     */
    private fun startComm() {
        currentPort.write(autoBaudRateHexString.decodeHex(), 100)
    }


    /**
     * Callback called every time new data arrives
     */
    override fun onNewData(data: ByteArray?) {
        data?.let {
            val dataHexStringArray = it.toHexStringArray()
            lastResponse.postValue(it.toHexString())
            if (dataHexStringArray[0] == headSuccess) {
                when (dataHexStringArray[3]) {
                    statusResponseRegister -> {
                        val statusCode = dataHexStringArray.subList(6, 8).joinToString("")
                        _resultString.postValue(statusCodes[statusCode])
                    }
                    distanceResponseRegister -> {
                        val distanceHex = dataHexStringArray.subList(6, 10).joinToString("")
                        val distanceInt = distanceHex.toLong(radix = 16)
                        val signalQualityHex = dataHexStringArray.subList(10, 12).joinToString("")
                        val signalQualityInt = signalQualityHex.toLong(radix = 16)
                        _resultString.postValue(statusCodes["0000"])
                        lastDistance.postValue(distanceInt)
                        lastSignalQuality = signalQualityInt
                    }
                }
            } else if (dataHexStringArray[0] == headError) {
                val statusCode = dataHexStringArray.subList(6, 8).joinToString("")
                _resultString.postValue(statusCodes[statusCode])
            }
        }
    }

    /**
     * Callback called when an error happens on Listener
     */
    override fun onRunError(e: Exception?) {
        Log.e(tag, "Error", e)
    }


    companion object {
        private const val baudRate = 115200
        private const val dataBits = 8
        private const val stopBits = UsbSerialPort.STOPBITS_1
        private const val parity = UsbSerialPort.PARITY_NONE

        private const val headError = "EE"
        private const val headSuccess = "AA"

        private const val statusResponseRegister = "00"
        private const val distanceResponseRegister = "22"

        private const val tag = "JRTManager"
        private const val laserOnHexString = "AA0001BE00010001C1"
        private const val laserOffHexString = "AA0001BE00010000C0"
        private const val readStatusHexString = "AA80000080"
        private const val oneShotAutoHexString = "AA0000200001000021"
        private const val continuousAutoHexString = "AA0000200001000425"
        private const val stopContinuousHexString = "58"
        private const val autoBaudRateHexString = "55"

        private val statusCodes = hashMapOf(
            "0000" to "Hata Yok",
            "0001" to "Voltaj Düşük, 2.2V'tan yüksek olmalı",
            "0002" to "Önemsiz iç hata",
            "0003" to "Cihaz çok soğuk -20C altında",
            "0004" to "Cihaz çok sıcak 40C üstünde",
            "0005" to "Hedef menzil dışında",
            "0006" to "Hatalı ölçüm sonucu",
            "0007" to "Arka plan çok parlak",
            "0008" to "Lazer çok zayıf",
            "0009" to "Lazer çok kuvvetli",
            "000A" to "Lazermetre Donanım hatası 1",
            "000B" to "Lazermetre Donanım hatası 2",
            "000C" to "Lazermetre Donanım hatası 3",
            "000D" to "Lazermetre Donanım hatası 4",
            "000E" to "Lazermetre Donanım hatası 5",
            "000F" to "Lazer stabil değil",
            "0010" to "Lazermetre Donanım hatası 6",
            "0011" to "Lazermetre Donanım hatası 7",
            "0081" to "Hatalı işlem komutu"
        )

        private fun ByteArray.toHexStringArray(): List<String> {
            return this.map {
                String.format("%02x", it).toUpperCase(Locale.ROOT)
            }
        }

        private fun String.decodeHex(): ByteArray = chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        private fun ByteArray.toHexString(): String {
            return this.joinToString(" ") {
                String.format("%02x", it)
            }.toUpperCase(Locale.ROOT)
        }
    }
}

enum class ConnectionResult {
    NoDeviceFound,
    DeviceRequiresPermission,
    Connected,
    ConnectionError,
    Disconnected
}

