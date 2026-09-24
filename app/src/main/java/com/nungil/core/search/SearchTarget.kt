package com.nungil.core.search

/** What a search looks for. The name matches the nav argument `targetType` of search_camera. */
enum class TargetType { LABEL, PERSON, ITEM }

/** A saved person or item that can be searched for by name. [label] is "person" or the item's COCO label. */
data class SavedName(val id: Long, val name: String, val type: TargetType, val label: String)

/**
 * The resolved target of a search.
 * @param id saved person or item id, or -1 for a COCO label.
 * @param label COCO label ("backpack"), "person" for a saved person, the item's label for a saved item.
 * @param spokenName what the app says: the saved name, or the label's display name in the current language.
 */
data class SearchTarget(val type: TargetType, val id: Long, val label: String, val spokenName: String)
