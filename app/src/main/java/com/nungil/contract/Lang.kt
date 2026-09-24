package com.nungil.contract

/** Language for UI strings, speech output and speech input. */
enum class Lang(val tag: String, val speechTag: String) {
    EN("en", "en-US"),
    KO("ko", "ko-KR");

    companion object {
        /** "ko", "ko-KR", "ko_KR" map to KO; everything else (and null) maps to EN. */
        fun fromTag(tag: String?): Lang =
            if (tag != null && tag.lowercase().startsWith("ko")) KO else EN
    }
}
