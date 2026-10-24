package com.monoapps.monolights

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monoapps.monolights.api.HueApi
import com.monoapps.monolights.api.HueDiscovery
import com.monoapps.monolights.api.HueGroup
import com.monoapps.monolights.api.HueLight
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

data class Bridge(val name: String, val ip: String)

class LightsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("monolights", Context.MODE_PRIVATE)
    private val discovery = HueDiscovery(app)

    var bridgeIp by mutableStateOf(prefs.getString("bridge_ip", null)); private set
    var username by mutableStateOf(prefs.getString("username", null)); private set
    val paired: Boolean get() = bridgeIp != null && username != null

    // Setup
    val foundBridges = mutableStateListOf<Bridge>()
    var pairing by mutableStateOf(false); private set
    var setupError by mutableStateOf<String?>(null); private set
    private var pairJob: Job? = null

    // Bridge data
    var lights by mutableStateOf<Map<String, HueLight>>(emptyMap()); private set
    var rooms by mutableStateOf<List<Pair<String, HueGroup>>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var loadError by mutableStateOf<String?>(null); private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val commandJobs = mutableMapOf<String, Job>()

    private fun api(): HueApi? {
        val ip = bridgeIp ?: return null
        val user = username ?: return null
        return HueApi(ip, user)
    }

    // --- Setup ---

    fun startDiscovery() {
        foundBridges.clear()
        discovery.start { name, ip ->
            if (foundBridges.none { it.ip == ip }) foundBridges.add(Bridge(name, ip))
        }
    }

    /** Polls the bridge until the user presses the link button (or ~30s pass). */
    fun pair(ip: String) {
        pairJob?.cancel()
        setupError = null
        pairing = true
        pairJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = HueApi(ip)
                repeat(30) {
                    api.pair()?.let { user ->
                        prefs.edit().putString("bridge_ip", ip).putString("username", user).apply()
                        bridgeIp = ip
                        username = user
                        pairing = false
                        discovery.stop()
                        refresh()
                        return@launch
                    }
                    delay(1000)
                }
                setupError = "The link button was not pressed in time. Try again."
            } catch (e: IOException) {
                setupError = "Could not reach a Hue Bridge at $ip."
            } finally {
                pairing = false
            }
        }
    }

    fun cancelPairing() {
        pairJob?.cancel()
        pairing = false
    }

    fun forgetBridge() {
        prefs.edit().clear().apply()
        bridgeIp = null
        username = null
        lights = emptyMap()
        rooms = emptyList()
        loadError = null
        setupError = null
    }

    // --- Bridge data ---

    fun refresh() {
        val api = api() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (lights.isEmpty()) loading = true
            try {
                lights = api.lights()
                rooms = api.groups()
                    .filter { it.value.type == "Room" || it.value.type == "Zone" }
                    .toList()
                    .sortedBy { it.second.name }
                loadError = null
            } catch (e: IOException) {
                if (lights.isEmpty()) loadError = "Could not reach the bridge at $bridgeIp."
                else _messages.tryEmit("Bridge unreachable")
            } finally {
                loading = false
            }
        }
    }

    fun toggleLight(id: String) {
        val light = lights[id] ?: return
        val on = !light.state.on
        lights = lights + (id to light.copy(state = light.state.copy(on = on)))
        runCommand { it.setLight(id, on = on) }
    }

    fun toggleRoom(id: String) {
        val room = rooms.toMap()[id] ?: return
        val on = !room.state.anyOn
        lights = lights.mapValues { (lightId, light) ->
            if (lightId in room.lights) light.copy(state = light.state.copy(on = on)) else light
        }
        rooms = rooms.map { (roomId, group) ->
            if (roomId == id) roomId to group.copy(state = group.state.copy(anyOn = on, allOn = on))
            else roomId to group
        }
        runCommand { it.setGroup(id, on = on) }
    }

    /**
     * Sends a command after a short delay, replacing any pending command with the
     * same key. The bridge silently drops commands that arrive in quick succession
     * (groups especially), so rapid taps must collapse into one request. No refresh
     * afterwards: the optimistic state is already correct and the bridge needs a
     * moment before reads reflect the change.
     */
    private fun sendDebounced(key: String, command: (HueApi) -> Unit) {
        commandJobs[key]?.cancel()
        commandJobs[key] = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            try {
                api()?.let(command)
            } catch (e: IOException) {
                _messages.tryEmit(e.message ?: "Bridge unreachable")
            }
        }
    }

    /** Debounced so repeated taps don't flood the bridge. Turns the light on if it was off. */
    fun setBrightness(id: String, bri: Int) {
        val light = lights[id] ?: return
        val turnOn = !light.state.on
        lights = lights + (id to light.copy(state = light.state.copy(on = true, bri = bri)))
        sendDebounced("bri-light-$id") {
            it.setLight(id, on = if (turnOn) true else null, bri = bri.coerceIn(1, 254))
        }
    }

    /** Sets a color light to a hue/sat value. Turns the light on if it was off. */
    fun setColor(id: String, hue: Int, sat: Int) {
        val light = lights[id] ?: return
        val turnOn = !light.state.on
        lights = lights + (id to light.copy(
            state = light.state.copy(on = true, hue = hue, sat = sat, colormode = "hs")
        ))
        // Color and white share a key per target: they overwrite each other anyway.
        sendDebounced("color-light-$id") {
            it.setLight(id, on = if (turnOn) true else null, hue = hue, sat = sat)
        }
    }

    /** Sets a light to a white color temperature (mireds). Turns it on if it was off. */
    fun setWhite(id: String, ct: Int) {
        val light = lights[id] ?: return
        val turnOn = !light.state.on
        lights = lights + (id to light.copy(
            state = light.state.copy(on = true, ct = ct, colormode = "ct")
        ))
        sendDebounced("color-light-$id") {
            it.setLight(id, on = if (turnOn) true else null, ct = ct)
        }
    }

    /** Debounced like setBrightness. Turns the whole room on if it was off. */
    fun setRoomBrightness(id: String, bri: Int) {
        val room = rooms.toMap()[id] ?: return
        val turnOn = !room.state.anyOn
        lights = lights.mapValues { (lightId, light) ->
            if (lightId in room.lights && (turnOn || light.state.on))
                light.copy(state = light.state.copy(on = true, bri = bri))
            else light
        }
        rooms = rooms.map { (roomId, group) ->
            if (roomId == id) roomId to group.copy(
                state = group.state.copy(anyOn = true, allOn = turnOn || group.state.allOn),
                action = group.action.copy(on = true, bri = bri),
            ) else roomId to group
        }
        sendDebounced("bri-group-$id") {
            it.setGroup(id, on = if (turnOn) true else null, bri = bri.coerceIn(1, 254))
        }
    }

    /** Sets every color-capable light in the room to a hue/sat value. */
    fun setRoomColor(id: String, hue: Int, sat: Int) {
        val room = rooms.toMap()[id] ?: return
        val turnOn = !room.state.anyOn
        lights = lights.mapValues { (lightId, light) ->
            if (lightId in room.lights && light.state.hue != null && (turnOn || light.state.on))
                light.copy(state = light.state.copy(on = true, hue = hue, sat = sat, colormode = "hs"))
            else light
        }
        rooms = rooms.map { (roomId, group) ->
            if (roomId == id) roomId to group.copy(
                state = group.state.copy(anyOn = true, allOn = turnOn || group.state.allOn),
                action = group.action.copy(on = true, hue = hue, sat = sat, colormode = "hs"),
            ) else roomId to group
        }
        sendDebounced("color-group-$id") {
            it.setGroup(id, on = if (turnOn) true else null, hue = hue, sat = sat)
        }
    }

    /** Sets every tunable light in the room to a white color temperature (mireds). */
    fun setRoomWhite(id: String, ct: Int) {
        val room = rooms.toMap()[id] ?: return
        val turnOn = !room.state.anyOn
        lights = lights.mapValues { (lightId, light) ->
            if (lightId in room.lights && light.state.ct != null && (turnOn || light.state.on))
                light.copy(state = light.state.copy(on = true, ct = ct, colormode = "ct"))
            else light
        }
        rooms = rooms.map { (roomId, group) ->
            if (roomId == id) roomId to group.copy(
                state = group.state.copy(anyOn = true, allOn = turnOn || group.state.allOn),
                action = group.action.copy(on = true, ct = ct, colormode = "ct"),
            ) else roomId to group
        }
        sendDebounced("color-group-$id") {
            it.setGroup(id, on = if (turnOn) true else null, ct = ct)
        }
    }

    private fun runCommand(command: (HueApi) -> Unit) {
        val api = api() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                command(api)
                refresh()
            } catch (e: IOException) {
                _messages.tryEmit(e.message ?: "Bridge unreachable")
            }
        }
    }

    override fun onCleared() {
        discovery.stop()
    }
}
