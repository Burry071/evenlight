package com.burry071.evenlight.wording

object Wording {
    /**
     * The fallback line, and tonight's only line. It carries the name the matcher chose and claims nothing else,
     * so it passes Verifier.check by construction (spec section 7).
     */
    fun template(name: String): String = "$name light, measured from this sky"
}
