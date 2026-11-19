package com.universalstreamingremote.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class DeviceType(val title: String) {
    ROKU("Roku"), FIRE("Fire TV / Fire Stick"), GOOGLE("Google TV / onn"), TV("Other Smart TV"), SAMSUNG_IR("Samsung TV · IR"), LG_IR("LG TV · IR")
}
data class RemoteDevice(val id: String, val name: String, val ip: String, val type: DeviceType)

class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)
    fun preferences() = prefs
    fun load(): List<RemoteDevice> = try {
        val arr = JSONArray(prefs.getString("saved", "[]"))
        (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i)
            RemoteDevice(item.getString("id"), item.getString("name"), item.getString("ip"), DeviceType.valueOf(item.getString("type")))
        }
    } catch (_: Exception) { emptyList() }
    fun save(devices: List<RemoteDevice>) {
        val arr = JSONArray()
        devices.forEach { d -> arr.put(JSONObject().put("id",d.id).put("name",d.name).put("ip",d.ip).put("type",d.type.name)) }
        prefs.edit().putString("saved", arr.toString()).apply()
    }
}
