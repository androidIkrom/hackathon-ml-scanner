package com.nungil.core.items

import com.nungil.contract.ItemKind

/** Keeps cars and objects apart: a car is enrolled only from vehicle boxes, an object from anything but a person. */
object ItemKinds {
    val VEHICLES = setOf("car", "truck", "bus", "motorcycle", "bicycle")

    fun allows(kind: ItemKind, label: String): Boolean = when (kind) {
        ItemKind.CAR -> label in VEHICLES
        ItemKind.OBJECT -> label != "person"
    }
}
