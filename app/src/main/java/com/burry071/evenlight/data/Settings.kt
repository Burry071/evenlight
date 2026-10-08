package com.burry071.evenlight.data

data class Settings(val place: String, val lat: Double, val lon: Double) {
    val isSet: Boolean get() = lat != 0.0 || lon != 0.0
    companion object { val UNSET = Settings("", 0.0, 0.0) }
}
