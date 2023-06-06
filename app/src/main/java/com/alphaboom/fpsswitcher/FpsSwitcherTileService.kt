package com.alphaboom.fpsswitcher

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.lang.NullPointerException


private const val FPS60 = 60
private const val FPS120 = 120
private const val APPLICATION_ID = "com.alphaboom.fpsswitcher"
private const val VERSION = 1
private const val INIT_REQUEST = 1001
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

class FpsSwitcherTileService : TileService() {
    private var currentFps = -1
        set(value) {
            field = value
            updateTile()
        }
    private val queue = arrayListOf<(IFpsInterface) -> Unit>()
    private var _interface: IFpsInterface? = null
    private var listening = false
    private val userServiceArgs by lazy {
        UserServiceArgs(ComponentName(APPLICATION_ID, FpsService::class.java.name))
            .daemon(false)
            .version(VERSION)
            .processNameSuffix("service")
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!listening) {
                return
            }
            service?.takeIf { it.pingBinder() }?.let {
                _interface = IFpsInterface.Stub.asInterface(it)
                queue.forEach { action ->
                    action(_interface!!)
                }
            }
            queue.clear()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _interface = null
            queue.clear()
        }

    }

    override fun onStartListening() {
        listening = true
        Shizuku.addRequestPermissionResultListener(this::onRequestPermission)
        try {
            initState()
        } catch (e: Exception) {
            //ignore
        }
    }

    override fun onStopListening() {
        listening = false
        Shizuku.removeRequestPermissionResultListener(this::onRequestPermission)
        if (_interface != null) {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        }
    }

    override fun onClick() {
        try {
            toggleState()
        } catch (e: Exception) {
            showDialog(AlertDialog.Builder(this)
                .setMessage(R.string.alert_message)
                .setPositiveButton(R.string.alert_positive) { _, _ ->
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$SHIZUKU_PACKAGE")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (e: ActivityNotFoundException) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                }
                .setNegativeButton(R.string.alert_negative) { dialog, _ ->
                    dialog.dismiss()
                }
                .create())
        }
    }

    private fun onRequestPermission(requestCode: Int, grantCode: Int) {
        if (requestCode == INIT_REQUEST && grantCode == PackageManager.PERMISSION_GRANTED) {
            initState()
        }
    }

    private fun initState(request: Boolean = false) {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            withInterface {
                currentFps = it.fps
            }
        } else if (request) {
            Shizuku.requestPermission(INIT_REQUEST)
        }

    }

    private fun toggleState() {
        if (currentFps < 0) {
            initState(true)
        } else {
            withInterface {
                val targetFps = if (currentFps == FPS120) {
                    FPS60
                } else {
                    FPS120
                }
                currentFps = targetFps
                it.fps = targetFps
            }
        }
    }

    private fun withInterface(block: (IFpsInterface) -> Unit) {
        if (_interface != null) {
            block(_interface!!)
        } else {
            queue.add(block)
            Shizuku.bindUserService(userServiceArgs, connection)
        }
    }

    private fun updateTile() {
        val state = if (currentFps == FPS120) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        qsTile.state = state
        qsTile.updateTile()
    }
}