package com.universalstreamingremote.app

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.DatagramPacket
import java.net.MulticastSocket
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

/** Roku ECP: documented on developer.roku.com. Never send commands to a public IP. */
data class RokuApp(val id: String, val name: String)

object Roku {
    private val supportedKeys = mapOf(
        "POWER" to "Power", "HOME" to "Home", "BACK" to "Back", "UP" to "Up", "DOWN" to "Down",
        "LEFT" to "Left", "RIGHT" to "Right", "OK" to "Select", "PLAY" to "Play", "REW" to "Rev",
        "FF" to "Fwd", "VOL_UP" to "VolumeUp", "VOL_DOWN" to "VolumeDown", "MUTE" to "VolumeMute",
        "OPTIONS" to "Info", "REPLAY" to "InstantReplay", "INPUT" to "InputTuner"
    )
    private fun localIpv4(host: String): Boolean {
        val bytes = try { InetAddress.getByName(host).address } catch (_: Exception) { return false }
        if (bytes.size != 4) return false
        val a = bytes[0].toInt() and 255; val b = bytes[1].toInt() and 255
        return a == 10 || a == 192 && b == 168 || a == 172 && b in 16..31 || a == 169 && b == 254
    }
    suspend fun verify(ip: String): Boolean = withContext(Dispatchers.IO) {
        if (!localIpv4(ip)) return@withContext false
        try {
            val conn = URL("http://$ip:8060/query/device-info").openConnection() as HttpURLConnection
            conn.connectTimeout = 1500; conn.readTimeout = 1500
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8).contains("<device-info") }
                .also { conn.disconnect() }
        } catch (_: Exception) { false }
    }
    suspend fun send(ip: String, key: String): Boolean = withContext(Dispatchers.IO) {
        val command = supportedKeys[key] ?: return@withContext false
        if (!localIpv4(ip)) return@withContext false
        try {
            val c = URL("http://$ip:8060/keypress/$command").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 1300; c.readTimeout = 1300; c.doOutput = true
            c.outputStream.use { }
            val ok = c.responseCode in 200..299
            c.disconnect(); ok
        } catch (_: Exception) { false }
    }
    suspend fun type(ip: String, text: String): Boolean = withContext(Dispatchers.IO) {
        if (!localIpv4(ip) || text.length > 200) return@withContext false
        for (ch in text) {
            try {
                val letter = URLEncoder.encode(ch.toString(), "UTF-8")
                val c = URL("http://$ip:8060/keypress/Lit_$letter").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 1200; c.readTimeout = 1200; c.doOutput = true
                c.outputStream.use { }
                val ok = c.responseCode in 200..299
                c.disconnect()
                if (!ok) return@withContext false
            } catch (_: Exception) { return@withContext false }
        }
        true
    }
    suspend fun channels(ip: String): List<RokuApp> = withContext(Dispatchers.IO) {
        if (!localIpv4(ip)) return@withContext emptyList()
        try {
            val c = URL("http://$ip:8060/query/apps").openConnection() as HttpURLConnection
            c.connectTimeout = 1500; c.readTimeout = 1500
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            val doc = c.inputStream.use { factory.newDocumentBuilder().parse(it) }
            val nodes = doc.getElementsByTagName("app")
            val result = (0 until nodes.length).mapNotNull { i ->
                val e = nodes.item(i) as? org.w3c.dom.Element ?: return@mapNotNull null
                val id = e.getAttribute("id")
                if (id.matches(Regex("[0-9A-Za-z._-]{1,80}"))) RokuApp(id, e.textContent ?: id) else null
            }
            c.disconnect(); result
        } catch (_: Exception) { emptyList() }
    }
    suspend fun launch(ip: String, appId: String): Boolean = withContext(Dispatchers.IO) {
        if(!localIpv4(ip) || !appId.matches(Regex("[0-9A-Za-z._-]{1,80}"))) return@withContext false
        try {
            val c=URL("http://$ip:8060/launch/$appId").openConnection() as HttpURLConnection
            c.requestMethod="POST"; c.connectTimeout=1400; c.readTimeout=1400; c.doOutput=true
            c.outputStream.use { }
            val ok=c.responseCode in 200..299
            c.disconnect(); ok
        } catch (_:Exception) { false }
    }
    suspend fun discover(context: Context): List<String> = withContext(Dispatchers.IO) {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wm.createMulticastLock("usr_ssdp").apply { setReferenceCounted(false); acquire() }
        val found = ConcurrentHashMap.newKeySet<String>()
        try {
            MulticastSocket(0).use { socket ->
                socket.soTimeout = 650
                val message = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: roku:ecp\r\n\r\n"
                val addr = InetAddress.getByName("239.255.255.250")
                repeat(2) { socket.send(DatagramPacket(message.toByteArray(),message.length,addr,1900)) }
                val until = System.currentTimeMillis() + 2800
                while (System.currentTimeMillis() < until) {
                    try {
                        val buf = ByteArray(4096)
                        val packet = DatagramPacket(buf,buf.size)
                        socket.receive(packet)
                        val body = String(packet.data, 0, packet.length)
                        if (body.contains("roku", ignoreCase = true)) {
                            packet.address.hostAddress?.let { if (localIpv4(it)) found.add(it) }
                        }
                    } catch (_: java.net.SocketTimeoutException) { }
                }
            }
        } catch (_: Exception) { } finally { if (lock.isHeld) lock.release() }
        found.filter { verify(it) }.sorted()
    }
}
