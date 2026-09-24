package com.nungil.contract

enum class ScanMode { FULL, LIVE }

enum class Facing { BACK, FRONT }

/** Tabs of the Saved screen. The nav argument `tab` carries the ordinal, or -1 for "no preference". */
enum class SavedTab { PEOPLE, CARS, OBJECTS }

/** What a saved item is. Stored in ItemEntity.kind as the enum name. */
enum class ItemKind { CAR, OBJECT }

/** Haptic vocabulary. Each value has one distinct vibration pattern, owned by I. */
enum class Buzz { TAP, FOUND, CENTERED, LOST, OBSTACLE, DONE, ERROR }
