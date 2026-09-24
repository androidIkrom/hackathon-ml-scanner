package com.nungil.core.voice

import com.nungil.contract.Dest
import com.nungil.contract.ItemKind
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.ScanMode
import com.nungil.contract.VoiceCommand
import com.nungil.contract.VoiceCommand.Go
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {
    private fun p(text: String) = VoiceCommandParser.parse(text)

    // ---- microphone words come first ----------------------------------------------------------
    @Test fun stopListening() = assertEquals(VoiceCommand.StopListening, p("Stop listening"))
    @Test fun micOff() = assertEquals(VoiceCommand.StopListening, p("mic off"))
    @Test fun voiceOff() = assertEquals(VoiceCommand.StopListening, p("voice off please"))
    @Test fun microphoneBeatsStop() = assertEquals(VoiceCommand.StopListening, p("stop the microphone"))
    @Test fun koStopListening() = assertEquals(VoiceCommand.StopListening, p("듣기 중지"))
    @Test fun koMicOffWithoutSpaces() = assertEquals(VoiceCommand.StopListening, p("마이크꺼"))
    @Test fun koVoiceOff() = assertEquals(VoiceCommand.StopListening, p("음성 명령 꺼 줘"))

    // ---- search with a payload ------------------------------------------------------------------
    @Test fun findMyBag() = assertEquals(Go(Dest.Search("my bag")), p("Find my bag"))
    @Test fun searchFor() = assertEquals(Go(Dest.Search("the keys")), p("search for the keys"))
    @Test fun whereIs() = assertEquals(Go(Dest.Search("my phone")), p("Where is my phone?"))
    @Test fun wheresWithApostrophe() = assertEquals(Go(Dest.Search("Ali")), p("Where's Ali"))
    @Test fun lookFor() = assertEquals(Go(Dest.Search("a cup")), p("look for a cup"))
    @Test fun findAloneOpensSearch() = assertEquals(Go(Dest.Search(null)), p("find"))
    @Test fun helpMeFind() = assertEquals(Go(Dest.Search("my keys")), p("help me find my keys"))
    @Test fun koFind() = assertEquals(Go(Dest.Search("가방")), p("가방 찾아줘"))
    @Test fun koFindKeepsWordsBefore() = assertEquals(Go(Dest.Search("내 가방")), p("내 가방 찾아 줘"))
    @Test fun koFindWithParticle() = assertEquals(Go(Dest.Search("가방을")), p("가방을 찾아"))
    @Test fun koFindNoSpace() = assertEquals(Go(Dest.Search("가방")), p("가방찾아줘"))
    @Test fun koWhere() = assertEquals(Go(Dest.Search("휴대폰")), p("휴대폰 어디 있어"))
    @Test fun koWhereYa() = assertEquals(Go(Dest.Search("민준이")), p("민준이 어디야"))
    @Test fun koFindAfterVerb() = assertEquals(Go(Dest.Search("내 가방")), p("찾아줘 내 가방"))
    @Test fun koFindScreen() = assertEquals(Go(Dest.Search(null)), p("물건 찾기"))
    @Test fun koFindSomething() = assertEquals(Go(Dest.Search(null)), p("무엇 찾아줘"))

    // ---- questions ------------------------------------------------------------------------------
    @Test fun whoIsThis() = assertEquals(VoiceCommand.WhoIsThis, p("Who is this?"))
    @Test fun whatIsThis() = assertEquals(VoiceCommand.WhatIsThis, p("What's this"))
    @Test fun whatIsThisScreen() = assertEquals(VoiceCommand.Help(null), p("what is this screen"))
    @Test fun whereAmI() = assertEquals(VoiceCommand.Help(null), p("where am I"))
    @Test fun helpTopic() = assertEquals(VoiceCommand.Help("search"), p("What is search?"))
    @Test fun helpTopicTwoWords() = assertEquals(VoiceCommand.Help("walk mode"), p("what is walk mode"))
    @Test fun help() = assertEquals(VoiceCommand.Help(null), p("help"))
    @Test fun whatCanISay() = assertEquals(VoiceCommand.Help(null), p("what can I say"))
    @Test fun koWho() = assertEquals(VoiceCommand.WhoIsThis, p("이 사람 누구야"))
    @Test fun koWhat() = assertEquals(VoiceCommand.WhatIsThis, p("이게 뭐야"))
    @Test fun koHelp() = assertEquals(VoiceCommand.Help(null), p("도움말"))
    @Test fun koHelpMe() = assertEquals(VoiceCommand.Help(null), p("도와 주세요"))
    @Test fun koHelpTopic() = assertEquals(VoiceCommand.Help("검색"), p("검색이 뭐야"))
    @Test fun koHelpTopicTwoWords() = assertEquals(VoiceCommand.Help("걷기 모드"), p("걷기 모드가 뭐예요"))
    @Test fun koThisScreen() = assertEquals(VoiceCommand.Help(null), p("이 화면 뭐야"))
    @Test fun koWhatAlone() = assertEquals(VoiceCommand.WhatIsThis, p("뭐야"))

    // ---- adding people and things ---------------------------------------------------------------
    @Test fun addPersonCalledAli() = assertEquals(Go(Dest.AddPerson("Ali")), p("add person called Ali"))
    @Test fun addPersonWithoutName() = assertEquals(Go(Dest.AddPerson(null)), p("add a new person"))
    @Test fun nameIsKeptEvenWhenItIsACommandWord() = assertEquals(Go(Dest.AddPerson("Me")), p("add person Me"))
    @Test fun saveThisPersonAs() = assertEquals(Go(Dest.AddPerson("Kim Minjun")), p("save this person as Kim Minjun"))
    @Test fun addCar() = assertEquals(Go(Dest.AddItem(ItemKind.CAR, "my Kia")), p("add car my Kia"))
    @Test fun addObject() = assertEquals(Go(Dest.AddItem(ItemKind.OBJECT, "water bottle")), p("new object water bottle"))
    @Test fun koAddPerson() = assertEquals(Go(Dest.AddPerson("민준")), p("사람 추가 민준"))
    @Test fun koAddPersonNameFirst() = assertEquals(Go(Dest.AddPerson("민준")), p("민준 사람 등록"))
    @Test fun koAddPersonWithParticle() = assertEquals(Go(Dest.AddPerson("민준")), p("민준을 사람으로 등록해줘"))
    @Test fun koAddPersonNamedLikeACommand() = assertEquals(Go(Dest.AddPerson("나")), p("사람 추가 나"))
    @Test fun koAddPersonNoSpace() = assertEquals(Go(Dest.AddPerson(null)), p("사람추가"))
    @Test fun koAddCar() = assertEquals(Go(Dest.AddItem(ItemKind.CAR, null)), p("자동차 등록"))
    @Test fun koAddObject() = assertEquals(Go(Dest.AddItem(ItemKind.OBJECT, "물병")), p("물건 추가 물병"))
    @Test fun savedPeopleIsNotAdding() = assertEquals(Go(Dest.Saved(SavedTab.PEOPLE)), p("saved people"))
    @Test fun koSavedPeopleIsNotAdding() = assertEquals(Go(Dest.Saved(SavedTab.PEOPLE)), p("저장한 사람"))

    // ---- language and learner mode --------------------------------------------------------------
    @Test fun korean() = assertEquals(VoiceCommand.SetLanguage(Lang.KO), p("Korean"))
    @Test fun english() = assertEquals(VoiceCommand.SetLanguage(Lang.EN), p("speak English"))
    @Test fun koKorean() = assertEquals(VoiceCommand.SetLanguage(Lang.KO), p("한국어"))
    @Test fun koEnglish() = assertEquals(VoiceCommand.SetLanguage(Lang.EN), p("영어로 바꿔 줘"))
    @Test fun learnerOn() = assertEquals(VoiceCommand.Learner(true), p("learner mode on"))
    @Test fun learnerOff() = assertEquals(VoiceCommand.Learner(false), p("turn off learning mode"))
    @Test fun koLearnerOff() = assertEquals(VoiceCommand.Learner(false), p("학습 모드 꺼"))
    @Test fun koLearnerOn() = assertEquals(VoiceCommand.Learner(true), p("학습 모드 켜 줘"))

    // ---- actions --------------------------------------------------------------------------------
    @Test fun stop() = assertEquals(VoiceCommand.Stop, p("stop"))
    @Test fun stopBeatsDestination() = assertEquals(VoiceCommand.Stop, p("stop full scan"))
    @Test fun switchCamera() = assertEquals(VoiceCommand.SwitchCamera, p("switch camera"))
    @Test fun backCameraIsNotBack() = assertEquals(VoiceCommand.SwitchCamera, p("back camera"))
    @Test fun readText() = assertEquals(VoiceCommand.ReadText, p("read this"))
    @Test fun delete() = assertEquals(VoiceCommand.Delete, p("delete"))
    @Test fun back() = assertEquals(VoiceCommand.Back, p("go back"))
    @Test fun start() = assertEquals(VoiceCommand.Start, p("start"))
    @Test fun repeat() = assertEquals(VoiceCommand.Repeat, p("say that again"))
    @Test fun koStop() = assertEquals(VoiceCommand.Stop, p("멈춰"))
    @Test fun koEnough() = assertEquals(VoiceCommand.Stop, p("그만"))
    @Test fun koSwitchCamera() = assertEquals(VoiceCommand.SwitchCamera, p("카메라 전환"))
    @Test fun koRead() = assertEquals(VoiceCommand.ReadText, p("글자 읽어줘"))
    @Test fun koDelete() = assertEquals(VoiceCommand.Delete, p("삭제해"))
    @Test fun koBack() = assertEquals(VoiceCommand.Back, p("뒤로 가"))
    @Test fun koStart() = assertEquals(VoiceCommand.Start, p("시작"))
    @Test fun koRepeat() = assertEquals(VoiceCommand.Repeat, p("다시 말해 줘"))

    // ---- screens --------------------------------------------------------------------------------
    @Test fun fullScan() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("Full scan"))
    @Test fun startFullScanOpensIt() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("start full scan"))
    @Test fun lookAround() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("look around"))
    @Test fun liveScan() = assertEquals(Go(Dest.Scan(ScanMode.LIVE)), p("live scan"))
    @Test fun scanMenu() = assertEquals(Go(Dest.ScanHub), p("scan menu"))
    @Test fun saved() = assertEquals(Go(Dest.Saved(null)), p("saved"))
    @Test fun savedCars() = assertEquals(Go(Dest.Saved(SavedTab.CARS)), p("saved cars"))
    @Test fun history() = assertEquals(Go(Dest.History), p("scan history"))
    @Test fun settings() = assertEquals(Go(Dest.Settings), p("open settings"))
    @Test fun home() = assertEquals(Go(Dest.Home), p("home"))
    @Test fun walk() = assertEquals(Go(Dest.Walk), p("Walk mode"))
    @Test fun koFullScan() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("주변 둘러보기"))
    @Test fun koFullScanNoSpace() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("전체스캔"))
    @Test fun koLive() = assertEquals(Go(Dest.Scan(ScanMode.LIVE)), p("실시간 안내"))
    @Test fun koSaved() = assertEquals(Go(Dest.Saved(null)), p("저장한 것"))
    @Test fun koSavedMisheard() = assertEquals(Go(Dest.Saved(null)), p("저장된"))
    @Test fun koSavedObjects() = assertEquals(Go(Dest.Saved(SavedTab.OBJECTS)), p("저장한 물건"))
    @Test fun koHistory() = assertEquals(Go(Dest.History), p("기록"))
    @Test fun koSettings() = assertEquals(Go(Dest.Settings), p("설정 열어 줘"))
    @Test fun koHome() = assertEquals(Go(Dest.Home), p("처음으로"))
    @Test fun koWalk() = assertEquals(Go(Dest.Walk), p("걷기 모드"))

    // ---- mishearings (build guide §10.5) ----------------------------------------------------------
    @Test fun saveItMeansSaved() = assertEquals(Go(Dest.Saved(null)), p("save it"))
    @Test fun safeMeansSaved() = assertEquals(Go(Dest.Saved(null)), p("safe"))
    @Test fun savesMeansSaved() = assertEquals(Go(Dest.Saved(null)), p("saves"))
    @Test fun workingModeMeansWalk() = assertEquals(Go(Dest.Walk), p("Open Working mode"))
    @Test fun workModeMeansWalk() = assertEquals(Go(Dest.Walk), p("work mode"))
    @Test fun forScan() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("for scan"))
    @Test fun foolScan() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("fool scan"))
    @Test fun fullscanOneWord() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("fullscan"))
    @Test fun bareKindOpensItsTab() {
        assertEquals(Go(Dest.Saved(SavedTab.PEOPLE)), p("person"))
        assertEquals(Go(Dest.Saved(SavedTab.CARS)), p("car"))
        assertEquals(Go(Dest.Saved(SavedTab.OBJECTS)), p("object"))
        assertEquals(Go(Dest.Saved(SavedTab.PEOPLE)), p("사람"))
        assertEquals(Go(Dest.Saved(SavedTab.CARS)), p("자동차"))
        assertEquals(Go(Dest.Saved(SavedTab.OBJECTS)), p("물건"))
    }
    @Test fun corazonIsUnknown() = assertEquals(VoiceCommand.Unknown("Corazon"), p("Corazon"))
    @Test fun googleIsUnknown() = assertEquals(VoiceCommand.Unknown("Google"), p("Google"))
    @Test fun koStopSignIsASearch() = assertEquals(Go(Dest.Search("정지 표지판")), p("정지 표지판 찾아줘"))

    // ---- dictation text survives as Unknown -------------------------------------------------------
    @Test fun nameIsUnknownWithOriginalCase() = assertEquals(VoiceCommand.Unknown("Ali"), p("Ali"))
    @Test fun twoWordsAreUnknown() = assertEquals(VoiceCommand.Unknown("water bottle"), p("water bottle"))
    @Test fun koNameIsUnknown() = assertEquals(VoiceCommand.Unknown("민준"), p("민준"))
    @Test fun emptyIsUnknown() = assertEquals(VoiceCommand.Unknown(""), p("   "))
    @Test fun punctuationIsDropped() = assertEquals(VoiceCommand.Unknown("Kim Minjun"), p("  Kim,  Minjun!  "))
}
