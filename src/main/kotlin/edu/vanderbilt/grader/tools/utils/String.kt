package edu.vanderbilt.grader.tools.utils

private val ES_GROUP = setOf("s", "sh", "ss", "ch", "x", "z", "is")

private val EXCEPTIONS = mapOf(
    // f ending exceptions
    "belief" to "beliefs",
    "chief" to "chief",
    "chef" to "chef",
    "roof" to "roofs",
    // o ending exceptions
    "photo" to "photos",
    "piano" to "pianos",
    "halo" to "halos",
    // s,z ending exceptions
    "gas" to "gasses",
    "fez" to "fezzes",
    // other exceptions
    "child" to "children",
    "goose" to "geese",
    "man" to "men",
    "woman" to "women",
    "tooth" to "teeth",
    "foot" to "feet",
    "mouse" to "mice",
    "person" to "people",
    // no plurals
    "sheep" to "sheep",
    "series" to "series",
    "species" to "species",
    "deer" to "deer",
    "fish" to "fish"
)

fun String.s(count: Int): String =
    when {
        count == 1 -> this

        // Must be before other rules below
        EXCEPTIONS.containsKey(this) -> EXCEPTIONS[this] ?: error("impossible")

        ES_GROUP.any { endsWith(it) } -> this + "es"

        endsWith('y') -> {
            if (lastIndex - 1 >= 0 && this[lastIndex - 1] !in "aeiou") {
                removeSuffix("y") + "ies"
            } else {
                this + 's'
            }
        }

        endsWith('f') -> removeSuffix("f") + "ves"

        endsWith("fe") -> removeSuffix("fe") + "ves"

        endsWith("o") -> this + "es"

        endsWith("us") -> removeSuffix("us") + 'i'

        endsWith("on") -> removeSuffix("on") + 'a'

        else -> this + 's'
    }

/** for readability */
fun String.ies(count: Int): String = s(count)

fun String.pl(count: Int, one: String? = null, many: String): String =
    this + if (count == 1) one ?: "" else many
