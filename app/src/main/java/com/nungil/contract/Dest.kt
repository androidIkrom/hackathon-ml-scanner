package com.nungil.contract

/** Every place the app can go. AppNavigator (I) maps these to navigation destinations. */
sealed interface Dest {
    data object Home : Dest
    data object ScanHub : Dest
    data class Scan(val mode: ScanMode) : Dest
    data object Walk : Dest
    data class Search(val query: String? = null) : Dest
    data class Saved(val tab: SavedTab? = null) : Dest
    data object Settings : Dest
    data object History : Dest
    data class AddPerson(val name: String? = null) : Dest
    data class AddItem(val kind: ItemKind, val name: String? = null) : Dest
    data object Reader : Dest
    data object Onboarding : Dest
}
