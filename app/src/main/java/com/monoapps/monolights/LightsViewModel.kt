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

    private val brightnessJobs = mutableMapOf<String, Job>()

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

    /** Debounced so repeated taps don't flood the bridge. Turns the light on if it was off. */
    fun setBrightness(id: String, bri: Int) {
        val light = lights[id] ?: return
        val turnOn = !light.state.on
        lights = lights + (id to light.copy(state = light.state.copy(on = true, bri = bri)))
        brightnessJobs[id]?.cancel()
        brightnessJobs[id] = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            try {
                api()?.setLight(id, on = if (turnOn) true else null, bri = bri.coerceIn(1, 254))
            } catch (e: IOException) {
                _messages.tryEmit("Bridge unreachable")
            }
        }
    }

    private fun runCommand(command: (HueApi) -> Unit) {
        val api = api() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                command(api)
                refresh()
            } catch (e: IOException) {
                _messages.tryEmit("Bridge unreachable")
            }
        }
    }

    override fun onCleared() {
        discovery.stop()
    }
}
