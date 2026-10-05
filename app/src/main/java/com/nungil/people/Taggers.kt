package com.nungil.people

import android.content.Context
import android.util.Log
import com.nungil.contract.app.NameTag
import com.nungil.contract.app.NameTagger
import com.nungil.contract.app.SavedFinder
import com.nungil.contract.app.VisionFrame
import com.nungil.core.items.Reach
import com.nungil.core.people.EveryNth
import com.nungil.core.people.FaceBoxes
import com.nungil.items.ItemFinder
import com.nungil.items.ItemRecognizer

/**
 * The taggers a scan screen runs on its extras thread: saved faces, then saved items, from the same finders Find
 * uses (SavedFinder). Called once per screen, off the main thread; the screen closes every tagger when it closes.
 * A tagger that cannot load (model missing, nothing saved) is left out, so the screen still works without names.
 * [reach]: Look around and Live search the whole frame for saved items; Walk the detector's boxes and near them.
 */
fun createNameTaggers(context: Context, reach: Reach = Reach.BOXES_AND_NEAR): List<NameTagger> {
    val app = context.applicationContext
    val taggers = mutableListOf<NameTagger>()
    if (FaceRecognizer.hasPeople(app)) {
        tryCreate("face") {
            SavedTagger(PersonFinder(app), everyNth = EVERY_NTH_PERSON_FRAME) { f -> f.detections.none { it.label == FaceBoxes.PERSON } }
        }?.let(taggers::add)
    }
    if (ItemRecognizer.hasItems(app)) tryCreate("item") { SavedTagger(ItemFinder(app, reach)) }?.let(taggers::add)
    return taggers
}

/**
 * A [SavedFinder] as a scan screen's tagger: every saved thing in the frame, on its detector box, or with its own
 * place (detectionIndex -1) when there is none. Only every [everyNth] frame that is not [skip]ped is looked at.
 */
class SavedTagger(
    private val finder: SavedFinder,
    everyNth: Int = 1,
    private val skip: (VisionFrame) -> Boolean = { false },
) : NameTagger {
    private val throttle = EveryNth(everyNth)

    override fun tag(frame: VisionFrame): List<NameTag> {
        if (skip(frame) || !throttle.take()) return emptyList()
        return finder.find(frame).map {
            NameTag(it.detectionIndex ?: -1, it.name, it.kind, box = if (it.detectionIndex == null) it.box else null)
        }
    }

    override fun close() = finder.close()
}

/** Faces are looked for on every 3rd frame with a person box. */
private const val EVERY_NTH_PERSON_FRAME = 3

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
