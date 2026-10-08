package com.burry071.evenlight.core

/**
 * The evening vocabulary. A word is chosen by nearest neighbour in RGB, so the app is
 * structurally incapable of inventing one - which is why the model does not pick names
 * (spec section 7: 18/18 obedient on format, 1/6 reliable on judgement).
 */
object Vocabulary {
    val words: List<VocabWord> = listOf(
        VocabWord("still", "#4A5560"), VocabWord("smothered", "#5A5148"),
        VocabWord("clearing", "#A8C0CC"), VocabWord("brassy", "#C2854A"),
        VocabWord("tarnished", "#6B5D4A"), VocabWord("milky", "#CFC9BE"),
        VocabWord("flint", "#7E7A72"), VocabWord("ember", "#B4552C"),
        VocabWord("copper", "#A96A3C"), VocabWord("chalk", "#E3DED4"),
        VocabWord("slate", "#5C6B82"), VocabWord("ash", "#9B968D"),
        VocabWord("wax", "#D8B878"), VocabWord("honey", "#C89A46"),
        VocabWord("bruised", "#5A4560"), VocabWord("waited", "#6E5F52"),
        VocabWord("low", "#3A322A"), VocabWord("thin", "#BFC6CB"),
        VocabWord("wide", "#7C93A8"), VocabWord("close", "#44403A"),
        VocabWord("dusty", "#A08A72"), VocabWord("wet", "#2E3A42"),
        VocabWord("salt", "#D5D2C8"), VocabWord("iron", "#4C4A49"),
        VocabWord("paper", "#EDE8DE"), VocabWord("linen", "#D9CFC0"),
        VocabWord("wool", "#B8A894"), VocabWord("amber", "#B08A5E"),
        VocabWord("smoke", "#6A6A6A"), VocabWord("held", "#7A6A55"),
        VocabWord("lifted", "#9FB6C4"), VocabWord("sunken", "#262A33"),
        VocabWord("gathering", "#55606B"), VocabWord("fading", "#C79A7A"),
        VocabWord("bright", "#E8D9B8")
    )
    val size: Int get() = words.size
}

data class VocabWord(val word: String, val anchorHex: String) {
    val anchor: Int get() = Pixels.parseHex(anchorHex)
}
