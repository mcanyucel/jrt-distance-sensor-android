package com.bridgewiz.jrtdistancemeasure

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    private val tag = "MainViewModel"


    private val _distance = MutableLiveData(0L)
    val distance: LiveData<Long>
        get() = _distance

    private val _signalQuality = MutableLiveData(0L)
    val signalQuality: LiveData<Long>
        get() = _signalQuality

    private val _rawOutput = MutableLiveData("")
    val rawOutput: LiveData<String>
        get() = _rawOutput

    private val _statusString = MutableLiveData("Başlıyor")
    val statusString: LiveData<String>
        get() = _statusString

    private val _isConnected = MutableLiveData(false)
    val isConnected: LiveData<Boolean>
        get() = _isConnected

    private val _isContinuousOn = MutableLiveData(false)

    private val _isLaserOn = MutableLiveData(false)

    private val _shouldToggleLaser: MutableLiveData<Boolean?> = MutableLiveData(null)
    val shouldToggleLaser: LiveData<Boolean?>
        get() = _shouldToggleLaser

    private val _shouldToggleContinuous: MutableLiveData<Boolean?> = MutableLiveData(null)
    val shouldToggleContinuous: LiveData<Boolean?>
        get() = _shouldToggleContinuous

    private val _laserButtonTextId = MutableLiveData(R.string.open_laser)
    val laserButtonTextId: LiveData<Int>
        get() = _laserButtonTextId

    private val _contMeasureButtonTextId = MutableLiveData(R.string.start_continuous)
    val contMeasureButtonTextId: LiveData<Int>
        get() = _contMeasureButtonTextId

    private val _shouldConnect = MutableLiveData(false)
    val shouldConnect: LiveData<Boolean>
        get() = _shouldConnect

    private val _shouldDisconnect = MutableLiveData(false)
    val shouldDisconnect: LiveData<Boolean>
        get() = _shouldDisconnect

    private val _shouldGetStatus = MutableLiveData(false)
    val shouldGetStatus: LiveData<Boolean>
        get() = _shouldGetStatus

    private val _shouldSingleShot = MutableLiveData(false)
    val shouldSingleShot: LiveData<Boolean>
        get() = _shouldSingleShot


    /**
     * Sets the status string
     */
    fun setStatusStringAsync(s: String) {
        _statusString.postValue(s)
    }

    /**
     * Sets the raw data
     */
    fun setRawData(dataString: String) {
        _rawOutput.value = dataString
    }


    /**
     * status:
     * 0: no USB device found
     * 1: port opened, setCurrentUsbPort called
     * 2: error
     */
    fun onConnectedAsync(status: Int) {
        when (status) {
            0 -> _statusString.postValue("USB cihaz bulunamadı")
            1 -> {
                _isConnected.postValue(true)
                _statusString.postValue("Bağlandı")
            }
            2 -> Log.e(tag, "Error connecting")
            else -> {
            }
        }
        _shouldConnect.postValue(false)
    }

    /**
     * Should be called when disconnected
     */
    fun onDisconnected() {
        _shouldDisconnect.value = false
        _isConnected.value = false
        _isLaserOn.value = false
        _isContinuousOn.value = false
        _statusString.value = "Bağlantı kesildi"
        _laserButtonTextId.value = R.string.open_laser
    }

    /**
     * Signals the activity to initialize a connection to the distance sensor
     * It is delegated to Activity due to OS interaction
     */
    fun connect() {
        _shouldConnect.value = true

    }

    /**
     * Signals the activity to disconnect from the distance sensor
     * It is delegated to Activity due to OS interaction
     */
    fun disconnect() {
        _shouldDisconnect.value = true
    }

    /**
     * Signals the activity to query status of the distance sensor
     */
    fun getStatus() {
        _shouldGetStatus.value = true
    }

    /**
     * Should be called after status query is completed
     */
    fun onGotStatus() {
        _shouldGetStatus.value = false
    }

    /**
     * Signals the activity to toggle the laser
     */
    fun toggleLaser() {
        _shouldToggleLaser.value = !_isLaserOn.value!!
    }

    /**
     * Should be called after the laser is toggled
     */
    fun onLaserToggled() {
        _shouldToggleLaser.value = null
        _isLaserOn.value = !_isLaserOn.value!!
        _isLaserOn.value?.let {
            if (it) {
                _laserButtonTextId.value = R.string.close_laser
                _statusString.value = "Lazer Açıldı"
            } else {
                _laserButtonTextId.value = R.string.open_laser
                _statusString.value = "Lazer Kapandı"
            }

        }

    }

    /**
     * Signals the activity to start a continuous measurement session with AUTO settings
     */
    fun toggleContinuousAuto() {
        _shouldToggleContinuous.value = !_isContinuousOn.value!!
    }

    fun onContinuousToggled() {
        _shouldToggleContinuous.value = null
        _isContinuousOn.value = !_isContinuousOn.value!!
        _isContinuousOn.value?.let {
            _contMeasureButtonTextId.value =
                if (it)
                    R.string.stop_continuous
                else
                    R.string.start_continuous
        }
    }

    /**
     * Signals the activity to make a single measurement with AUTO settings
     */
    fun singleMeasureAuto() {
        _shouldSingleShot.value = true
    }

    /**
     * Updates the distance parameter
     */
    fun setDistance(dist: Long, sq: Long) {
        _distance.value = dist
        _signalQuality.value = sq
    }

    /**
     * Should be called after a single measurement request is completed
     */
    fun onSingleShotCompleted() {
        _shouldSingleShot.value = false
    }

}