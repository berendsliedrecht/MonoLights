package com.monoapps.monolights.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LightState(
    val on: Boolean = false,
    val bri: Int = 0,
    val reachable: Boolean = true,
)

data class HueLight(
    val name: String = "",
    val state: LightState = LightState(),
)

data class GroupState(
    @SerializedName("any_on") val anyOn: Boolean = false,
    @SerializedName("all_on") val allOn: Boolean = false,
)

data class HueGroup(
    val name: String = "",
    val type: String = "",
    val lights: List<String> = emptyList(),
    val state: GroupState = GroupState(),
)

/**
 * Client for the Hue Bridge's local CLIP v1 REST API (http://<bridge>/api).
 * All calls are blocking; run them on a background dispatcher.
 */
class HueApi(private val ip: String, private val username: String = "") {

    private val gson = Gson()

    /**
     * Registers this app with the bridge. Returns the username on success,
     * null while the link button has not been pressed yet.
     */
    fun pair(): String? {
        val body = """{"devicetype":"MonoLights#kompakt"}"""
        val request = Request.Builder()
            .url("http://$ip/api")
            .post(body.toRequestBody(JSON))
            .build()
        val first = JsonParser.parseString(execute(request)).asJsonArray.first().asJsonObject
        first.getAsJsonObject("success")?.let { return it.get("username").asString }
        val error = first.getAsJsonObject("error")
        if (error.get("type").asInt == LINK_BUTTON_NOT_PRESSED) return null
        throw IOException(error.get("description").asString)
    }

    fun lights(): Map<String, HueLight> =
        gson.fromJson(get("/lights"), object : TypeToken<Map<String, HueLight>>() {}.type)

    fun groups(): Map<String, HueGroup> =
        gson.fromJson(get("/groups"), object : TypeToken<Map<String, HueGroup>>() {}.type)

    fun setLight(id: String, on: Boolean? = null, bri: Int? = null) =
        put("/lights/$id/state", stateBody(on, bri))

    fun setGroup(id: String, on: Boolean? = null, bri: Int? = null) =
        put("/groups/$id/action", stateBody(on, bri))

    private fun stateBody(on: Boolean?, bri: Int?): String {
        val state = mutableMapOf<String, Any>()
        on?.let { state["on"] = it }
        bri?.let { state["bri"] = it }
        return gson.toJson(state)
    }

    private fun get(path: String): String {
        val text = execute(Request.Builder().url("http://$ip/api/$username$path").build())
        // Errors (e.g. unauthorized user) come back as an array instead of an object.
        if (text.startsWith("[")) {
            val error = JsonParser.parseString(text).asJsonArray.first()
                .asJsonObject.getAsJsonObject("error")
            throw IOException(error?.get("description")?.asString ?: "Unexpected bridge response")
        }
        return text
    }

    private fun put(path: String, body: String) {
        execute(
            Request.Builder()
                .url("http://$ip/api/$username$path")
                .put(body.toRequestBody(JSON))
                .build()
        )
    }

    private fun execute(request: Request): String =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Bridge returned HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty bridge response")
        }

    companion object {
        private const val LINK_BUTTON_NOT_PRESSED = 101
        private val JSON = "application/json".toMediaType()
        private val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
