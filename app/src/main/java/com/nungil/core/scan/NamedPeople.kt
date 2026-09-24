package com.nungil.core.scan

import kotlin.math.abs

/** Nobody is announced twice: a plain "person" next to a recognised person is that same person. */
object NamedPeople {
    const val SHADOW_DEG = 20f

    fun dropShadowedPersons(objects: List<ObjectSummary>, shadowDeg: Float = SHADOW_DEG): List<ObjectSummary> {
        val named = objects.filter { it.isName && it.wasPerson }
        return objects.filterNot { o ->
            o.label == "person" && !o.isName && named.any { abs(AngleMath.diff(it.angle, o.angle)) <= shadowDeg }
        }
    }
}
