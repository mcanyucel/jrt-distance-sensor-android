package com.bridgewiz.jrtdistancemeasure

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.bridgewiz.jrtdistancemeasure.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var mainViewModel: MainViewModel
    private lateinit var jrtManager: JRTManager

    private val usbRequestCode = 100
    private val actionUsbPermission = "com.bridgewiz.jrtdistancemeasure.USB_PERMISSION"

    private var receiver: BroadcastReceiver? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // view model
        mainViewModel =
            ViewModelProvider(this@MainActivity).get(MainViewModel::class.java)
        // data binding
        binding = DataBindingUtil.setContentView(this@MainActivity, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.mainViewModel = mainViewModel
        // JRT manager
        val usbManager: UsbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        jrtManager = JRTManager(usbManager)

        // Bind raw data observer
        jrtManager.response.observe(this, {
            mainViewModel.setRawData(it)
        })
        // bind result string observer
        jrtManager.resultString.observe(this, statusResultObserver)
        // bind distance observer
        jrtManager.distance.observe(this, distanceObserver)
        // bind UI interaction observers
        mainViewModel.shouldConnect.observe(this, {
            if (it)
                connectToUSB()
        })
        mainViewModel.shouldDisconnect.observe(this, {
            if (it) {
                when (jrtManager.disconnect()) {
                    ConnectionResult.Disconnected -> mainViewModel.onDisconnected()
                    else -> {
                    }
                }
            }
        })
        mainViewModel.shouldGetStatus.observe(this, { should ->
            if (should) {
                jrtManager.readStatus()
            }
        })
        mainViewModel.shouldSingleShot.observe(this, { should ->
            if (should) {
                jrtManager.singleMeasureAuto()
            }
        })
        /*
        * When shouldToggle is:
        *    null: skip
        *    true: open the laser
        *    false: close the laser
        */
        mainViewModel.shouldToggleContinuous.observe(this, { should ->
            should?.let {
                if (it)
                    jrtManager.startContinuousMeasure()
                else
                    jrtManager.stopContinuousMeasure()
                 mainViewModel.onContinuousToggled()
            }
        })

        /*
         * When shouldToggle is:
         *    null: skip
         *    true: open the laser
         *    false: close the laser
         */
        mainViewModel.shouldToggleLaser.observe(this, {
            it?.let { shouldToggle ->
                if (shouldToggle)
                    jrtManager.openLaser()
                else
                    jrtManager.closeLaser()
                mainViewModel.onLaserToggled()
            }
        })
    }

    /**
     * Observable responsible from marshalling status string from JRTManager to view model
     */
    private val statusResultObserver: (String) -> Unit = {
        mainViewModel.setStatusStringAsync(it)
        mainViewModel.onGotStatus()
    }

    /**
     * Observalbe responsible from marshalling distance values from JRTManager to view model
     */
    private val distanceObserver: (Long) -> Unit = {
        mainViewModel.setDistance(it, jrtManager.signalQuality)
        mainViewModel.onSingleShotCompleted()

    }

    /**
     * Initialize a connection to USB over JRTManager. If permissions are required, call
     * permission function
     */
    private fun connectToUSB() {
        CoroutineScope(Dispatchers.IO).launch {
            when (jrtManager.connect()) {
                ConnectionResult.NoDeviceFound -> {
                    Log.i(tag, "no device")
                    mainViewModel.onConnectedAsync(0)
                }
                ConnectionResult.Connected -> {
                    Log.i(tag, "connected")
                    mainViewModel.onConnectedAsync(1)
                }
                ConnectionResult.DeviceRequiresPermission -> {
                    requestUsbPermission(
                        jrtManager.usbManager,
                        jrtManager.device
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * Ask the user for USB permission using Broadcasts and PendingIntent.
     * Note that USB permission implementation is different than standard permission requests.
     * If the user gives permission, call JRTManager.connect again.
     */
    private fun requestUsbPermission(
        usbManager: UsbManager,
        device: UsbDevice
    ) {
        val pi: PendingIntent =
            PendingIntent.getBroadcast(this, usbRequestCode, Intent(actionUsbPermission), 0)
        val intentFilter = IntentFilter(actionUsbPermission)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.action?.let { intentAction ->
                    if (intentAction == actionUsbPermission) {
                        val usbDevice: UsbDevice? =
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        usbDevice?.let {
                            if (intent.getBooleanExtra(
                                    UsbManager.EXTRA_PERMISSION_GRANTED,
                                    false
                                )
                            ) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    when (jrtManager.connect()) {
                                        ConnectionResult.NoDeviceFound -> mainViewModel.onConnectedAsync(
                                            0
                                        )
                                        ConnectionResult.Connected -> mainViewModel.onConnectedAsync(
                                            1
                                        )
                                        ConnectionResult.ConnectionError -> mainViewModel.onConnectedAsync(
                                            2
                                        )
                                        else -> {
                                        }
                                    }
                                }
                            }

                        }
                    }

                }
            }
        }
        registerReceiver(receiver, intentFilter)
        usbManager.requestPermission(device, pi)
    }
}