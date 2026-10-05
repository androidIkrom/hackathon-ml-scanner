package com.nungil.core.people

/**
 * The name in words said on the add-person screen: "his name is Ali" is "Ali". A name said again replaces the
 * first one; the first could not be changed by voice, and "New myself" after "Myself" got "I did not
 * understand" (the logs).
 */
object SpokenName {
    private val LEADS = listOf(
        "my name is", "his name is", "her name is", "their name is", "the name is", "name is", "new name",
        "rename", "call him", "call her", "call them", "name", "이름은", "이름이", "이름",
    )

    fun of(text: String): String {
        var name = text.trim().trimEnd('.', '!', '?', ',').trim()
        val lower = name.lowercase()
        LEADS.firstOrNull { lower == it || lower.startsWith("$it ") }?.let { name = name.drop(it.length).trim() }
        return name
    }
}
