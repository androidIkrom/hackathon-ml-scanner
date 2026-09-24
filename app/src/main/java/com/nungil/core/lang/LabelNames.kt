package com.nungil.core.lang

import com.nungil.contract.Lang

/** Display names for the 80 COCO labels, in English and Korean, with the Korean counter word. */
object LabelNames {
    private class Ko(val name: String, val counter: String)

    private const val PEOPLE = "명"
    private const val ANIMALS = "마리"
    private const val MACHINES = "대"
    private const val BOOKS = "권"
    private const val THINGS = "개"

    private val table: Map<String, Ko> = linkedMapOf(
        "person" to Ko("사람", PEOPLE),
        "bicycle" to Ko("자전거", MACHINES),
        "car" to Ko("자동차", MACHINES),
        "motorcycle" to Ko("오토바이", MACHINES),
        "airplane" to Ko("비행기", MACHINES),
        "bus" to Ko("버스", MACHINES),
        "train" to Ko("기차", MACHINES),
        "truck" to Ko("트럭", MACHINES),
        "boat" to Ko("보트", MACHINES),
        "traffic light" to Ko("신호등", THINGS),
        "fire hydrant" to Ko("소화전", THINGS),
        "stop sign" to Ko("정지 표지판", THINGS),
        "parking meter" to Ko("주차 요금기", THINGS),
        "bench" to Ko("벤치", THINGS),
        "bird" to Ko("새", ANIMALS),
        "cat" to Ko("고양이", ANIMALS),
        "dog" to Ko("개", ANIMALS),
        "horse" to Ko("말", ANIMALS),
        "sheep" to Ko("양", ANIMALS),
        "cow" to Ko("소", ANIMALS),
        "elephant" to Ko("코끼리", ANIMALS),
        "bear" to Ko("곰", ANIMALS),
        "zebra" to Ko("얼룩말", ANIMALS),
        "giraffe" to Ko("기린", ANIMALS),
        "backpack" to Ko("배낭", THINGS),
        "umbrella" to Ko("우산", THINGS),
        "handbag" to Ko("핸드백", THINGS),
        "tie" to Ko("넥타이", THINGS),
        "suitcase" to Ko("여행 가방", THINGS),
        "frisbee" to Ko("프리스비", THINGS),
        "skis" to Ko("스키", THINGS),
        "snowboard" to Ko("스노보드", THINGS),
        "sports ball" to Ko("공", THINGS),
        "kite" to Ko("연", THINGS),
        "baseball bat" to Ko("야구 방망이", THINGS),
        "baseball glove" to Ko("야구 글러브", THINGS),
        "skateboard" to Ko("스케이트보드", THINGS),
        "surfboard" to Ko("서핑보드", THINGS),
        "tennis racket" to Ko("테니스 라켓", THINGS),
        "bottle" to Ko("병", THINGS),
        "wine glass" to Ko("와인 잔", THINGS),
        "cup" to Ko("컵", THINGS),
        "fork" to Ko("포크", THINGS),
        "knife" to Ko("칼", THINGS),
        "spoon" to Ko("숟가락", THINGS),
        "bowl" to Ko("그릇", THINGS),
        "banana" to Ko("바나나", THINGS),
        "apple" to Ko("사과", THINGS),
        "sandwich" to Ko("샌드위치", THINGS),
        "orange" to Ko("오렌지", THINGS),
        "broccoli" to Ko("브로콜리", THINGS),
        "carrot" to Ko("당근", THINGS),
        "hot dog" to Ko("핫도그", THINGS),
        "pizza" to Ko("피자", THINGS),
        "donut" to Ko("도넛", THINGS),
        "cake" to Ko("케이크", THINGS),
        "chair" to Ko("의자", THINGS),
        "couch" to Ko("소파", THINGS),
        "potted plant" to Ko("화분", THINGS),
        "bed" to Ko("침대", THINGS),
        "dining table" to Ko("탁자", THINGS),
        "toilet" to Ko("변기", THINGS),
        "tv" to Ko("텔레비전", MACHINES),
        "laptop" to Ko("노트북", MACHINES),
        "mouse" to Ko("마우스", THINGS),
        "remote" to Ko("리모컨", THINGS),
        "keyboard" to Ko("키보드", THINGS),
        "cell phone" to Ko("휴대폰", MACHINES),
        "microwave" to Ko("전자레인지", MACHINES),
        "oven" to Ko("오븐", MACHINES),
        "toaster" to Ko("토스터", MACHINES),
        "sink" to Ko("싱크대", THINGS),
        "refrigerator" to Ko("냉장고", MACHINES),
        "book" to Ko("책", BOOKS),
        "clock" to Ko("시계", THINGS),
        "vase" to Ko("꽃병", THINGS),
        "scissors" to Ko("가위", THINGS),
        "teddy bear" to Ko("곰 인형", THINGS),
        "hair drier" to Ko("헤어드라이어", THINGS),
        "toothbrush" to Ko("칫솔", THINGS),
    )

    private val byKorean: Map<String, String> = table.entries.associate { (label, ko) -> ko.name to label }

    /** The 80 COCO labels, in model order. */
    val labels: List<String> = table.keys.toList()

    fun isKnown(label: String): Boolean = label in table

    /** Name to speak or show. Unknown labels (for example a saved name) are returned unchanged. */
    fun name(label: String, lang: Lang): String =
        if (lang == Lang.KO) table[label]?.name ?: label else label

    /** Korean counter word: 명 for people, 마리 for animals, 대 for machines, 권 for books, else 개. */
    fun counterKo(label: String): String = table[label]?.counter ?: THINGS

    /** COCO label for an exact Korean display name ("의자" gives "chair"), or null. */
    fun labelForKorean(name: String): String? = byKorean[name.trim()]
}
