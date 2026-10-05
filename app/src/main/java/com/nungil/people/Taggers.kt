package com.nungil.people

import android.content.Context
import android.util.Log
import com.nungil.contract.app.NameTagger
import com.nungil.items.ItemRecognizer
import com.nungil.items.ItemTagger

/**
 * The taggers A's scan screen runs on its extras thread: saved faces, then saved items. Called once per scan
 * screen, off the main thread; A closes every tagger when the screen closes. A tagger that cannot load (model
 * missing, nothing saved) is left out, so the scan still works without names. [itemsByLook]: saved items are also
 * looked for all over the frame, as Find does (ItemTagger.byLook); about a second a frame, so for the scan only.
 */
fun createNameTaggers(context: Context, itemsByLook: Boolean = false): List<NameTagger> {
    val app = context.applicationContext
    val taggers = mutableListOf<NameTagger>()
    if (FaceRecognizer.hasPeople(app)) tryCreate("face") { FaceTagger(app) }?.let(taggers::add)
    if (ItemRecognizer.hasItems(app)) tryCreate("item") { ItemTagger(app, itemsByLook) }?.let(taggers::add)
    return taggers
}

internal inline fun tryCreate(what: String, make: () -> NameTagger): NameTagger? =
    try {
        make()
    } catch (e: Exception) {
        Log.i("Nungil", "No $what tagger: ${e.message}")
        null
    } catch (e: LinkageError) {
        Log.i("Nungil", "No $what tagger: ${e.message}")
        null
    }
