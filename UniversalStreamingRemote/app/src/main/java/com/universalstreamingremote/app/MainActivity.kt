package com.universalstreamingremote.app

import android.os.Bundle
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.UUID

private val night = Color(0xFF080E1B)
private val panel = Color(0xFF121D32)
private val cobalt = Color(0xFF38D7FF)
private val soft = Color(0xFF9BABCC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = DeviceStore(this)
        setContent { UniversalApp(store) }
    }
}

@Composable
private fun UniversalApp(store: DeviceStore) {
    var devices by remember { mutableStateOf(store.load()) }
    val prefs = remember { store.preferences() }
    var primaryId by remember { mutableStateOf(prefs.getString("main", null) ?: devices.firstOrNull()?.id) }
    var secondaryId by remember { mutableStateOf(prefs.getString("second", null)) }
    var combined by remember { mutableStateOf(prefs.getBoolean("combined", false)) }
    var tvId by remember { mutableStateOf(prefs.getString("tv", null)) }
    var playerId by remember { mutableStateOf(prefs.getString("player", null)) }
    var showAdd by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Select a device or add one to begin") }
    var textEntry by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<RokuApp>>(emptyList()) }
    var appDevice by remember { mutableStateOf<String?>(null) }
    var loadingApps by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = devices.firstOrNull { it.id == primaryId }
    val secondary = devices.firstOrNull { it.id == secondaryId && it.id != primaryId }

    LaunchedEffect(primaryId, secondaryId, combined, tvId, playerId) {
        prefs.edit().putString("main",primaryId).putString("second",secondaryId)
            .putBoolean("combined",combined).putString("tv",tvId).putString("player",playerId).apply()
    }
    fun send(device: RemoteDevice?, key: String) {
        if (device == null) { status = "Choose a device first"; return }
        if (device.type == DeviceType.SAMSUNG_IR || device.type == DeviceType.LG_IR) {
            status = when {
                !Infrared.available(context) -> "This phone has no IR blaster. Use a compatible IR adapter or a Wi-Fi TV driver."
                !Infrared.supports(device.type, key) -> "${device.name}: this button has no IR code in its current profile"
                Infrared.send(context, device.type, key) -> "${device.name} · $key sent via IR (point your phone at the TV)"
                else -> "IR transmission failed"
            }
            return
        }
        if (device.type != DeviceType.ROKU) { status = "${device.type.title}: pairing driver not available in this version"; return }
        if (Build.VERSION.SDK_INT >= 26) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        scope.launch {
            if (!Roku.send(device.ip, key)) status = "Could not send $key to ${device.name}. Check Wi-Fi and Remote Control settings."
            else status = "${device.name} · $key"
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary=cobalt, surface=panel, background=night, onSurface=Color.White)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(night,Color(0xFF102143),night)))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SettingsRemote,null,tint=cobalt,modifier=Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("UNIVERSAL",fontSize=23.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp)
                        Text("STREAMING REMOTE",fontSize=11.sp,color=cobalt,letterSpacing=2.sp)
                    }
                    IconButton(onClick={showAdd=true}) { Icon(Icons.Rounded.AddCircle,"Add device",tint=cobalt) }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { DeviceSelector("MAIN REMOTE",primary,devices) { primaryId=it; if (secondaryId == it) secondaryId=null } }
                    if(combined) Box(Modifier.weight(1f)) { DeviceSelector("SECOND REMOTE",secondary,devices.filter { it.id!=primaryId }) { secondaryId=it } }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.ViewColumn,null,tint=cobalt)
                    Spacer(Modifier.width(8.dp))
                    Text("COMBINED REMOTES",modifier=Modifier.weight(1f),fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                    Switch(checked=combined,onCheckedChange={ combined=it },colors=SwitchDefaults.colors(checkedTrackColor=cobalt))
                }
                Text(status, color=soft,fontSize=11.sp,maxLines=3,modifier=Modifier.padding(vertical=6.dp))
                LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.weight(1f)) {
                    if (combined) item {
                        val tv = devices.firstOrNull { it.id == tvId } ?: primary
                        val player = devices.firstOrNull { it.id == playerId } ?: secondary
                        HybridRemote(tv, player, devices, onTvSelect={tvId=it}, onPlayerSelect={playerId=it}, onSend={d,k->send(d,k)})
                    }
                    item {
                        RemotePanel(primary,compact=combined) { send(primary,it) }
                    }
                    if(combined) item {
                        RemotePanel(secondary,compact=true) { send(secondary,it) }
                    }
                    item {
                        if(primary?.type == DeviceType.ROKU) {
                            Column(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(18.dp)).padding(14.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text("ROKU CHANNELS",color=cobalt,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                                    TextButton(onClick={ val device = primary; if(device != null) scope.launch {
                                        loadingApps=true
                                        apps=Roku.channels(device.ip)
                                        appDevice=device.id
                                        loadingApps=false
                                        status=if(apps.isEmpty()) "No channels returned by Roku" else "${apps.size} channels found"
                                    } },enabled=!loadingApps) { Text(if(loadingApps) "Loading…" else "Load") }
                                }
                                if(appDevice==primary?.id) apps.forEach { app ->
                                    TextButton(onClick={ val device=primary; if(device!=null)scope.launch {
                                        status=if(Roku.launch(device.ip,app.id)) "Opening ${app.name}" else "Could not open ${app.name}"
                                    } },modifier=Modifier.fillMaxWidth()) { Text(app.name) }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(18.dp)).padding(14.dp)) {
                            Text("ROKU TEXT ENTRY",color=cobalt,fontSize=11.sp,fontWeight=FontWeight.Bold)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                OutlinedTextField(value=textEntry,onValueChange={textEntry=it.take(200)},modifier=Modifier.weight(1f),singleLine=true,placeholder={Text("Type into a Roku text field")})
                                Spacer(Modifier.width(8.dp))
                                Button(enabled=primary?.type==DeviceType.ROKU && textEntry.isNotEmpty(),onClick={
                                    val p=primary ?: return@Button
                                    scope.launch { status=if(Roku.type(p.ip,textEntry)) "Text sent" else "Text entry failed" }
                                }) { Text("Send") }
                            }
                        }
                    }
                    item { Text("DEVICE SUPPORT: Roku local Wi-Fi and Samsung/LG infrared controls are implemented. IR requires a physical phone IR emitter and direct line-of-sight. Fire TV, onn/Google TV, and other TVs need pairing drivers. Network devices must share a local network. Roku players may not support TV volume.",color=soft,fontSize=11.sp,modifier=Modifier.padding(8.dp)) }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
        if(showAdd) {
            AddDeviceDialog(onDismiss={showAdd=false},onAdd={ d ->
                devices=devices+d; store.save(devices); if(primaryId==null) primaryId=d.id
                status="${d.name} added"; showAdd=false
            },onRemove={d ->
                devices=devices.filterNot { it.id==d.id }; store.save(devices)
                if(primaryId==d.id) primaryId=devices.firstOrNull()?.id
                if(secondaryId==d.id) secondaryId=null
                if(tvId==d.id)tvId=null
                if(playerId==d.id)playerId=null
            },devices=devices)
        }
    }
}

@Composable
private fun DeviceSelector(label:String, selected: RemoteDevice?, devices: List<RemoteDevice>, onSelect:(String)->Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.background(panel,RoundedCornerShape(16.dp)).clickable { menu=true }.padding(12.dp)) {
        Text(label,color=cobalt,fontSize=10.sp,letterSpacing=1.sp)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(selected?.name ?: "Select device",fontSize=13.sp,maxLines=1,modifier=Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore,null,tint=soft)
        }
        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
            devices.forEach { d -> DropdownMenuItem(text={Text("${d.name} · ${d.type.title}")},onClick={onSelect(d.id);menu=false}) }
        }
    }
}

@Composable
private fun RemotePanel(device:RemoteDevice?,compact:Boolean,onKey:(String)->Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp,cobalt.copy(alpha=0.22f),RoundedCornerShape(24.dp)).background(panel,RoundedCornerShape(24.dp)).padding(if(compact) 12.dp else 17.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if(device?.type == DeviceType.ROKU) Color(0xFF57E8A8) else soft,CircleShape))
            Spacer(Modifier.width(7.dp))
            Text((device?.name ?: "NO DEVICE").uppercase(),fontWeight=FontWeight.Bold,fontSize=12.sp,modifier=Modifier.weight(1f),maxLines=1)
            Text(device?.type?.title ?: "—",color=soft,fontSize=10.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Key("⏻","POWER",onKey,accent=Color(0xFFFF6D82))
            Key("⌂","HOME",onKey)
            Key("↩","BACK",onKey)
            Key("☷","OPTIONS",onKey)
        }
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center,modifier=Modifier.fillMaxWidth()) {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Key("+","VOL_UP",onKey)
                Key("−","VOL_DOWN",onKey)
                Key("×","MUTE",onKey)
            }
            Spacer(Modifier.width(if(compact) 18.dp else 32.dp))
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Key("▲","UP",onKey,large=true)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Key("◀","LEFT",onKey,large=true)
                    Key("OK","OK",onKey,large=true,accent=cobalt)
                    Key("▶","RIGHT",onKey,large=true)
                }
                Key("▼","DOWN",onKey,large=true)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            Key("⏮","REW",onKey)
            Key("⏯","PLAY",onKey,accent=cobalt)
            Key("⏭","FF",onKey)
            Key("↺","REPLAY",onKey)
        }
    }
}

@Composable
private fun Key(symbol:String,command:String,onKey:(String)->Unit,large:Boolean=false,accent:Color=Color.White) {
    val size = if(large) 62.dp else 49.dp
    Box(Modifier.size(size).background(Color(0xFF1D2D49),CircleShape).border(1.dp,accent.copy(alpha=0.22f),CircleShape).clickable { onKey(command) },contentAlignment=Alignment.Center) {
        Text(symbol,color=accent,fontSize=if(symbol=="OK") 16.sp else 24.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
    }
}

private fun isValidLocalIp(ip: String): Boolean {
    val nums = ip.split('.')
    if (nums.size != 4) return false
    val x = nums.map { it.toIntOrNull() ?: return false }
    if (x.any { it !in 0..255 }) return false
    return x[0] == 10 || (x[0] == 172 && x[1] in 16..31) || (x[0] == 192 && x[1] == 168) || (x[0] == 169 && x[1] == 254)
}

@Composable
private fun AddDeviceDialog(onDismiss:()->Unit,onAdd:(RemoteDevice)->Unit,onRemove:(RemoteDevice)->Unit,devices:List<RemoteDevice>) {
    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(DeviceType.ROKU) }
    var expanded by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest=onDismiss,title={Text("DEVICE MANAGER",fontWeight=FontWeight.Black,color=cobalt)},text={
        Column(verticalArrangement=Arrangement.spacedBy(9.dp),modifier=Modifier.verticalScroll(rememberScrollState())) {
            Text("Choose device type",color=soft,fontSize=12.sp)
            Box { OutlinedButton(onClick={expanded=true}) { Text(type.title); Icon(Icons.Rounded.ExpandMore,null) }
                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                    DeviceType.entries.forEach { t -> DropdownMenuItem(text={Text(t.title)},onClick={type=t;expanded=false}) }
                }
            }
            OutlinedTextField(name,{name=it},label={Text("Device name")},singleLine=true)
            if (type != DeviceType.SAMSUNG_IR && type != DeviceType.LG_IR) {
                OutlinedTextField(ip,{ip=it.trim()},label={Text("Local IP address")},singleLine=true,keyboardOptions=KeyboardOptions.Default)
            } else Text(if (Infrared.available(context)) "IR emitter detected. Point your phone at the TV." else "No IR emitter detected on this phone. This profile needs an IR-capable phone.", color=soft,fontSize=12.sp)
            if(type==DeviceType.ROKU) {
                OutlinedButton(onClick={scanning=true;scope.launch { scanResult=Roku.discover(context);scanning=false;error=if(scanResult.isEmpty()) "No Roku found. Enter its IP manually." else "" }},enabled=!scanning) {
                    Text(if(scanning) "Searching…" else "Find Roku on Wi-Fi")
                }
                scanResult.forEach { host -> TextButton(onClick={ip=host;if(name.isBlank())name="Roku $host"}) { Text("Use $host") } }
            } else if (type != DeviceType.SAMSUNG_IR && type != DeviceType.LG_IR) Text("This device type can be saved to your layout, but cannot send commands until its pairing driver is implemented.",color=soft,fontSize=12.sp)
            if(error.isNotBlank()) Text(error,color=Color(0xFFFFA7A7),fontSize=12.sp)
            if(devices.isNotEmpty()) {
                HorizontalDivider()
                Text("SAVED DEVICES",fontSize=11.sp,color=cobalt)
                devices.forEach { d -> Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(d.name,modifier=Modifier.weight(1f),fontSize=12.sp)
                    IconButton(onClick={onRemove(d)}) { Icon(Icons.Rounded.DeleteOutline,"Remove ${d.name}",tint=soft) }
                } }
            }
        }
    },confirmButton={ TextButton(onClick={
        val isIr = type == DeviceType.SAMSUNG_IR || type == DeviceType.LG_IR
        if(name.isBlank() || (!isIr && ip.isBlank())){error="Name and IP are required for Wi-Fi devices";return@TextButton}
        if(!isIr && !isValidLocalIp(ip)){error="Enter a valid local IPv4 address";return@TextButton}
        scope.launch {
            if(type==DeviceType.ROKU && !Roku.verify(ip)){error="Roku not reachable at $ip on port 8060";return@launch}
            onAdd(RemoteDevice(UUID.randomUUID().toString(),name.trim(),if (isIr) "" else ip,type))
        }
    }) { Text("Add device") } },dismissButton={TextButton(onClick=onDismiss){Text("Close")}})
}

@Composable
private fun HybridRemote(
    tv: RemoteDevice?, player: RemoteDevice?, devices: List<RemoteDevice>,
    onTvSelect:(String)->Unit, onPlayerSelect:(String)->Unit,
    onSend:(RemoteDevice?,String)->Unit
) {
    Column(Modifier.fillMaxWidth().border(1.dp,cobalt.copy(alpha=.45f),RoundedCornerShape(22.dp))
        .background(Color(0xFF152840),RoundedCornerShape(22.dp)).padding(12.dp)) {
        Text("FUSION CONTROL DECK", color=cobalt, fontSize=12.sp, fontWeight=FontWeight.Black, letterSpacing=2.sp)
        Text("Choose which device receives each group of commands", color=soft, fontSize=11.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { DeviceSelector("TV / SOUND",tv,devices,onTvSelect) }
            Box(Modifier.weight(1f)) { DeviceSelector("STREAMING",player,devices,onPlayerSelect) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            Key("⏻","POWER",{onSend(tv,it)},accent=Color(0xFFFF6D82))
            Key("+","VOL_UP",{onSend(tv,it)})
            Key("−","VOL_DOWN",{onSend(tv,it)})
            Key("×","MUTE",{onSend(tv,it)})
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            Key("⌂","HOME",{onSend(player,it)})
            Key("↩","BACK",{onSend(player,it)})
            Key("☷","OPTIONS",{onSend(player,it)})
            Key("⏯","PLAY",{onSend(player,it)},accent=cobalt)
        }
        Spacer(Modifier.height(9.dp))
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
            Key("▲","UP",{onSend(player,it)},large=true)
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Key("◀","LEFT",{onSend(player,it)},large=true)
                Key("OK","OK",{onSend(player,it)},large=true,accent=cobalt)
                Key("▶","RIGHT",{onSend(player,it)},large=true)
            }
            Key("▼","DOWN",{onSend(player,it)},large=true)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            Key("⏮","REW",{onSend(player,it)})
            Key("↺","REPLAY",{onSend(player,it)})
            Key("⏭","FF",{onSend(player,it)})
        }
    }
}
