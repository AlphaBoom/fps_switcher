package com.alphaboom.fpsswitcher

class FpsService :IFpsInterface.Stub(){
    override fun setFps(fps: Int) {
        Runtime.getRuntime().exec("settings put secure miui_refresh_rate $fps")
        Runtime.getRuntime().exec("settings put secure user_refresh_rate $fps")
    }

    override fun getFps(): Int {
        val process = Runtime.getRuntime().exec("settings get secure miui_refresh_rate")
        process.inputStream.bufferedReader().use {
            return it.readText().trim().toInt()
        }
    }
}