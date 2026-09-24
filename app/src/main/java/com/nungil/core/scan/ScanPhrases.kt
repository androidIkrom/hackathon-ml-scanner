package com.nungil.core.scan

import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.core.lang.Josa

/** Every other sentence the scan screens speak, in English and Korean (해요체). */
object ScanPhrases {
    private fun pick(lang: Lang, en: String, ko: String) = if (lang == Lang.KO) ko else en

    fun intro(mode: ScanMode, lang: Lang): String = when (mode) {
        ScanMode.FULL -> pick(lang, "Hold the phone upright and turn slowly in a full circle.", "휴대폰을 세우고 천천히 한 바퀴 돌아 주세요.")
        ScanMode.LIVE -> pick(lang, "Point the phone around. I will say what I find.", "휴대폰을 천천히 움직여 보세요. 찾는 대로 알려 드릴게요.")
    }

    fun slowDown(lang: Lang) = pick(lang, "Slow down.", "천천히 돌아 주세요.")

    fun noCompass(lang: Lang) = pick(lang, "Compass not available. Switching to live scan.", "나침반을 쓸 수 없어서 실시간 안내로 바꿀게요.")

    fun stopped(lang: Lang) = pick(lang, "Stopped.", "멈췄어요.")

    fun cameraNeeded(lang: Lang) = pick(lang, "To see what is around you, allow the camera.", "주변을 보려면 카메라를 허용해 주세요.")

    fun cameraProblem(lang: Lang) = pick(lang, "The camera has a problem. Go back and try again.", "카메라에 문제가 있어요. 뒤로 갔다가 다시 해 주세요.")

    fun cameraSwitched(facing: Facing, lang: Lang) = when (facing) {
        Facing.FRONT -> pick(lang, "Front camera.", "전면 카메라예요.")
        Facing.BACK -> pick(lang, "Back camera.", "후면 카메라예요.")
    }

    /** [name] is a COCO display name or an English ImageNet word (the classifier has no Korean names). */
    fun looksLike(name: String, lang: Lang) =
        pick(lang, "It looks like ${SummaryBuilder.article(name)} $name.", "$name 같아요.")

    fun unknownThing(lang: Lang) = pick(lang, "I can't tell what this is.", "무엇인지 모르겠어요.")

    fun thisIs(name: String, lang: Lang) =
        pick(lang, "This is $name.", name + if (Josa.hasBatchim(name)) "이에요." else "예요.")

    fun unknownPerson(lang: Lang) = pick(lang, "I don't know this person.", "누군지 모르겠어요.")

    fun nobody(lang: Lang) = pick(lang, "I don't see anyone.", "사람이 보이지 않아요.")

    fun walkNotReady(lang: Lang) = pick(lang, "Walk mode is not ready yet.", "걷기 모드는 아직 준비 중이에요.")
}
