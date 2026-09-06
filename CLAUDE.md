# AppGoodWords

좋은 글귀를 모아 두고 알림·위젯으로 다시 만나게 하는 Android 앱과, 여러 기기가 같은 데이터를 쓰도록
하는 Node 서버입니다.

- `app/` — Kotlin, Jetpack Compose, Room, WorkManager, Glance 위젯
- `server/` — Node 표준 라이브러리만 쓰는 REST 서버와 웹 UI (`app_good_words_server.mjs`)

## 빌드와 테스트

프로젝트 루트에서 실행합니다. PowerShell 기준이고, Git Bash에서는 `./gradlew`를 씁니다.

```powershell
.\gradlew.bat testDebugUnitTest        # JVM 유닛 테스트
.\gradlew.bat assembleDebug            # 디버그 빌드
.\gradlew.bat assembleRelease          # R8까지 통과하는지 확인
node --test server/tests/server.test.mjs
```

계측 테스트는 기기나 에뮬레이터가 필요합니다. **서버를 띄운 상태로 돌려야** 앱↔서버 경로가 실제로
실행됩니다. 서버가 없으면 그 테스트들은 조용히 건너뛰므로, 결과에서 skipped가 0인지 확인해야 합니다.

```powershell
node server/app_good_words_server.mjs --host 0.0.0.0 --port 8765
.\gradlew.bat connectedDebugAndroidTest
```

에뮬레이터에서 호스트 PC 주소는 `http://10.0.2.2:8765`입니다.

**실기기가 붙어 있을 때 `connectedDebugAndroidTest`를 돌리지 마세요.** AGP가 붙어 있는 기기
**전부**를 대상으로 삼고, 끝난 뒤 앱을 지웁니다. 사용자 DB가 함께 사라집니다. 실기기가
연결된 동안에는 에뮬레이터를 지정해 직접 부릅니다.

```bash
adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w com.codex.appgoodwords.test/androidx.test.runner.AndroidJUnitRunner
```

**묶음 사이에 `pm clear`를 넣습니다.** 백업 파일이 앱 안에 쌓여서, 이어 돌리면
`SyncStatusRefreshTest`처럼 목록 개수를 보는 시험이 옛 파일 때문에 깨집니다.
계측 전체를 한 번에 돌리면 에뮬레이터가 자주 죽으므로 종류별로 나눠 부르는 편이 낫습니다.

**실기기에 설치하기 전에 DB를 먼저 빼 둡니다.** 마이그레이션이 틀리면
`fallbackToDestructiveMigration()`이 조용히 전부 지웁니다.

```bash
adb -s <기기> exec-out run-as com.codex.appgoodwords cat databases/app-good-words.db > backup.db
```

설치 뒤에는 `user_version`과 레코드 수를 대조합니다. 통째로 지워졌으면 파일이 급격히
작아지므로 크기만 봐도 대개 드러납니다. 기기에는 `sqlite3`가 없으니 뺀 파일을
에뮬레이터로 밀어 넣고 거기서 셉니다. Git Bash는 `/data/...`를 윈도 경로로 바꾸므로
`MSYS_NO_PATHCONV=1`을 붙여야 합니다.

**스키마를 바꿨으면 서버도 새 코드로 다시 띄웁니다.** `schemaVersion`이 다르면 앱이
동기화를 막고, 계측의 앱↔서버 시험이 통째로 깨집니다.

## 규칙

**PowerShell로 소스 파일을 일괄 치환하지 마세요.** Windows PowerShell 5.1은 UTF-8 파일을 ANSI로 읽어서
`Get-Content ... | Set-Content`를 거치면 한글 주석과 UI 문구가 전부 깨집니다. 파일 수정은 Edit 도구로 합니다.

주석과 사용자에게 보이는 문구는 한국어로 씁니다.

**Room 스키마를 바꾸면 `Migration`과 `Migration{N}To{M}Test`를 함께 추가합니다.**
`AppContainer`에 `fallbackToDestructiveMigration()`이 걸려 있어서, 마이그레이션이 없거나 틀리면
사용자 DB가 오류 없이 통째로 지워집니다. 현재 버전은 17입니다.

**새 레코드 종류를 추가하면 여섯 군데를 함께 고칩니다.** 하나만 빠져도 조용히 어긋납니다.
`AppDataJson`(직렬화) · `SyncMerger`(병합) · `SyncDeduplicator`(같은 내용 합치기) ·
`SnapshotReindexer`(id 재부여) · `AppDataImporter`(저장) ·
서버의 `normalizeDb`/`mergeSnapshot`/`replaceSnapshot`/`reindex`/`deduplicate` ·
웹 `server/web`(탭·화면·`emptySnapshot`).
특히 서버 `replaceSnapshot`을 빠뜨리면 업로드가 그 종류만 남겨 두어, 사용자가 지운 레코드가
다음 병합에 되살아납니다. **삭제 표식 종류도 함께 늘립니다** — 앱 `SyncEntityType`와 서버
`deletionEntityTypes`. 서버 쪽을 빠뜨리면 그 종류의 표식이 조용히 버려져, 지운 레코드가
역시 되살아납니다. `AppDataSnapshotCountTest`에 새 종류를 한 줄 더해 두면 다음 사람이
빠뜨렸을 때 시험이 걸립니다.

**첨부 주소 형식은 앱·서버·웹 세 곳이 같아야 합니다.** `appgoodwords://attachment/{sha256}.{확장자}`이고,
앱 `AttachmentUris.SCHEME` · 서버 `attachmentScheme` · 웹 `app.js`의 `attachmentScheme`에 각각 있습니다.
파일은 DB JSON 밖 `attachments/` 폴더에 둡니다. DB에 넣으면 스냅샷마다 사진이 통째로 오갑니다.

**그날의 기분은 `DayMood`가 정합니다.** 기분이 두 곳에 남습니다. 톡 찍어 둔 것(`MoodLogEntity`)과
일기에 딸린 것입니다. 화면마다 각자 셈하면 통계와 그래프가 서로 다른 말을 합니다.
찍어 둔 것이 먼저고, 없으면 일기에서 봅니다. 같은 날 일기 둘이 서로 다른 기분이면 마지막에
남긴 것을 쓰되 `settled = false`로 표시합니다. **그래프는 `byDate`(빈 날을 만들지 않음),
기분별 실천·상위 기분은 `settledByDate`(어느 쪽이라 할 수 없는 날은 뺌)를 씁니다.**
그래프에서 빼면 실제로는 쓴 날이 안 쓴 날처럼 보이고, 평균에 넣으면 그 칸이 흔들립니다.

**번뜩인 것(`ContentType.IDEA`)은 담기는 자리만 글귀와 같습니다.** 떠오르는 자리는 다릅니다.
`pickLeastRecentlySurfaced`가 `type <> :excluded`로 뺍니다. 알림과 위젯이 말하는 것은
"오늘의 글귀"인데 내가 적어 둔 생각이 거기 뜨면 남의 좋은 말인 척 돌아오고, 새로 담은 것은
`lastSurfacedAt`이 없어 **가장 먼저** 뽑힙니다. 목록도 나눕니다 — 보관함의 아이디어 탭
(`LibraryTab`)입니다. 앞으로 "내가 쓴 것" 종류를 더하면 이 두 곳에서 함께 빼야 합니다.

**보관함 종류(`ContentType`)는 앱·서버·웹이 같아야 합니다.** 앱 `ContentType` · 서버 `contentTypes` ·
웹 `app.js`의 종류 고르개와 이름표. 서버가 모르는 종류로 보면 글귀로 바꿔 버려, 담아 둔 것이
보관함에서 그 종류로 골라지지 않습니다. **`IDEA`는 담는 사람이 짚어 줍니다** — 주소로 종류를
알아내는 `ContentNormalizer.detectType`이 번뜩인 것은 알아볼 수 없습니다.

**담기 전에 다듬는 규칙은 `ContentNormalizer` 한 곳에만 둡니다.** 담는 길이 둘입니다 —
담는 화면(`MainViewModel.saveContent`)과 다른 앱에서 공유해 온 것(`ShareTargetActivity`).
한쪽에 규칙을 따로 두면 같은 유튜브 링크가 담는 길에 따라 영상이 되기도 하고 링크가 되기도 합니다.

**공유로 들어온 것은 화면 없이 바로 담깁니다.** `AndroidManifest.xml`의 SEND 거르개가
`ShareTargetActivity`에 붙어 있습니다. 유튜브는 **제목 한 줄과 주소를 함께** 보내므로
"http로 시작하면 주소"라고 보면 안 됩니다 — 제목이 앞에 붙어 있어 주소가 본문에 통째로 박히고,
종류를 못 알아내 영상이 아니라 글귀가 됩니다. 나누는 규칙은 `SharedText`에 있습니다.
**주소가 없으면 글을 본문에 담습니다** — 제목만 채우면 "본문·링크·사진·영상 중 하나는 있어야
한다"는 `validate`에 걸려 조용히 담기지 않습니다. 저장은 화면이 아니라
`AppGoodWordsApplication.applicationScope`에 겁니다. 이 화면은 곧바로 사라져서, 화면에 매인
코루틴에 걸면 저장이 도중에 끊깁니다.

**날씨·기분·일기 종류 선택지는 앱과 웹이 같아야 합니다.** 앱 `DiaryTags.kt`의
`DiaryWeather`·`DiaryMood`·`DiaryKind`와 웹 `server/web/app.js`의
`weatherOptions`·`moodOptions`·`diaryKinds`가 같은 코드 값을 씁니다. 한쪽만 늘리면
다른 쪽에서는 고르지 않은 것처럼 보입니다. 서버는 값을 검사하지 않으므로 서버는 고칠 필요가 없습니다.

**감사·반성 일기의 답(`answers`)은 물음 순서에 자리를 맞춘 목록입니다.** 물음 문구와 순서도 앱
`DiaryKind.prompts`와 웹 `diaryKinds`가 같아야 합니다. 답은 물음 번호로만 이어져 있어서, 한쪽에서
물음을 끼워 넣거나 순서를 바꾸면 답이 다른 물음에 가서 붙습니다. **가운데 빈칸은 버리지 않습니다.**
버리면 뒤의 답이 앞으로 밀립니다(마지막 물음에만 답한 날 그 답이 첫 물음의 답이 됩니다).
뒤쪽 빈칸만 떼는 규칙이 앱 `DiaryAnswers.normalize`와 서버 `normalizeAnswers()`에 같이 있습니다.
Room의 기본 `Converters`는 빈 문자열을 버리므로, 이 열에만 `DiaryAnswerConverters`를 따로 붙였습니다.

**웹에서 저장·삭제할 때는 `updatedAt`을 올리고 삭제 표식을 남깁니다.** 둘 중 하나라도 빠지면
기기가 다음 병합에서 옛 사본을 다시 올려 주어 웹에서 한 일이 조용히 되돌아갑니다.
서버의 `deleteWithTombstone()`을 쓰고, `save*()`에서 `updatedAt: nowMs()`를 넣습니다.

**루틴을 늘어놓고 옮기는 규칙은 세 곳이 같아야 합니다.** 루틴에는 하루에 밟는 차례(`orderIndex`)가
있습니다. 앱 `RoutineOrder`(`sorted`·`movedTo`·`moved`) · 서버 `sortedRoutines`·`moveRoutineTo`·
`moveRoutine` · 웹 `server/web/app.js`의 `sortedRoutines`. 번호가 겹치면 만든 지 오래된 쪽이 앞이고,
옮긴 뒤에는 0부터 빈틈없이 다시 매깁니다. 자리는 앱·서버 안에서 0부터 세고, 화면과 REST의
`position`만 1부터입니다. 줄 밖을 가리키면 맨 위/맨 아래로 당겨 붙입니다. 한쪽만 다르게 옮기면 두 기기가 병합할 때마다 서로의 차례를 고쳐
끝나지 않습니다. **차례가 실제로 달라진 루틴만 저장합니다.** 안 바뀐 것까지 `updatedAt`을 올리면
서버가 새 리비전을 붙여 증분 동기화가 매번 루틴 전부를 실어 나릅니다.

**`SyncDeduplicator`(앱)와 서버 `deduplicate()`는 규칙이 같아야 합니다.** 판정 기준과 승자 선택
(최신 `updatedAt`, 같으면 큰 `syncId`)이 어긋나면 두 기기가 병합할 때마다 서로를 고쳐 끝나지 않습니다.

**글귀는 뽑아낸 책을 `bookSyncId`로, 루틴은 뽑아낸 글귀를 `sourceContentSyncId`로 가리킵니다.**
숫자 id로 가리키면 다른 기기에서 엉뚱한 것이 됩니다. 같은 내용 합치기로 부모가 하나로 줄면
사라진 쪽을 가리키던 것을 남은 쪽으로 옮겨 붙여야 합니다(앱 `SyncDeduplicator`, 서버 `deduplicate`).
**부모를 지워도 뽑아 둔 것은 남깁니다** — 책을 지워도 글귀는, 글귀를 지워도 루틴은 남습니다.
밟기로 한 것은 그 글귀와 별개로 이미 내 것입니다. **이름을 고쳐도 출처는 지우지 않습니다** —
편집 화면이 출처를 실어 보내지 않을 수 있어서 `AppRepository.saveRoutine`이 기존 값을 먼저 지킵니다.

**글귀에 다는 메모(`ContentMemoEntity`)는 실천으로 세지 않습니다.** 루틴 메모는 저장할 때 체크를
함께 남기지만(`saveRoutineMemo`), 글귀 메모는 남기지 않습니다. 여기 적는 것은 실천이 아니라
생각이라, 체크를 남기면 `routineChecks`를 "실천했다"로 세는 다섯 곳(`DailyLoop` · `StatsSummary` ·
`FeedbackWriter` · `MoodPractice` · `GrowthPrompt`)이 한 일보다 부풀어 보입니다.

**기기 간 식별자는 `syncId`뿐입니다.** Room의 숫자 id는 기기마다 따로 증가해서 A기기 id=5와 B기기 id=5가
서로 다른 레코드입니다. 자식은 부모를 `contentItemSyncId`(이벤트·글귀 메모) 또는
`routineSyncId`(체크·루틴 메모)로 가리키고, 저장 직전에 `SnapshotReindexer`(앱)와 `reindex()`(서버)가
숫자 id를 다시 매깁니다. 병합 결과를 숫자 id 그대로 넣으면 서로를 덮어씁니다.
**부모를 못 찾는 메모는 버립니다** — 화면이 부모 안에서만 그리므로 남겨도 볼 방법이 없습니다.
이력은 예외로 남깁니다(제목만으로도 읽힙니다).

**삭제 표식 보관 기간은 양쪽이 같아야 합니다.** 앱 `SyncCoordinator.DELETION_RETENTION_DAYS`와
서버 `deletionRetentionDays`(현재 90일). 한쪽만 바꾸면 다른 쪽이 매번 되돌려 줍니다.

**서버는 바뀐 레코드에만 새 리비전 번호를 붙입니다.** 안 바뀐 것까지 번호가 오르면 증분 동기화가
매번 전부를 보냅니다. 그래서 (1) 저장 직전에 내용을 비교하고(`stampRevisions`), (2) 멀쩡한 숫자 id는
그대로 두고(`withStableIds`), (3) 지웠거나 합쳐서 사라진 레코드는 삭제 표식을 남깁니다.
부분 응답(`partial`)에 없는 레코드는 지워진 것이 아니라 안 바뀐 것이라, 앱은 `applyDelta`로 얹기만 합니다.

**AI 피드백에 나가는 것은 사용자가 정합니다.** 일기 본문은 설정에서 켜야만 실립니다
(`AiFeedbackSettings.includeDiaryBody`). 기본값을 바꾸지 마세요. 한번 나가면 되돌릴 수 없습니다.
물음은 앱 `GrowthPrompt`만 만들고, 서버 `/api/growth-feedback`은 그대로 전달만 합니다.
서버가 기록을 다시 읽어 물음을 짜면 사용자가 앱에서 정해 둔 범위가 조용히 뒤집힙니다.
AI 열쇠는 기기(`SettingsStore`)나 서버(`OPENAI_API_KEY`·`ANTHROPIC_API_KEY`)에만 두고,
스냅샷·백업에는 넣지 않습니다.

**"언제 돌아봤는지"는 남아 있는 돌아보기로 셉니다(`GrowthCadence`).** 설정의
`AiFeedbackSettings.lastRunAt`은 이 기기에만 남는 값이라, 다른 기기에서 돌렸거나 백업을 되넣으면
실제와 어긋납니다. 돌아보기는 동기화되므로 기록을 보면 어느 기기에서 돌렸든 같은 답이 나옵니다.
날수는 시간 차가 아니라 **날짜 차로** 셉니다 — 어젯밤에 돌렸으면 "어제"입니다.
화면(`GrowthFeedbackScreen`)과 알림(`GrowthNudgeWorker`)이 같은 함수를 써야 합니다.
한쪽에서 따로 셈하면 "3일 전"이라 적힌 화면을 보며 "7일째"라는 알림을 받게 됩니다.

**뜸하다고 알리는 것은 아무것도 만들지 않습니다.** `GrowthNudgeWorker`는 알리기만 하고,
피드백을 만드는 `GrowthFeedbackWorker`와 예약도 따로 겁니다. 값이 드는 요청을 사용자 몰래
보내면 안 됩니다. 그래서 자동 실행을 꺼 둔 사람에게도 돌고, **오히려 그런 사람에게 더 필요합니다.**
한 번 알린 뒤에는 `lastNudgedAt`을 보고 같은 기간만큼 쉽니다. 매일 알리면 잔소리가 됩니다.
**한 편도 없으면 알리지 않습니다** — 앱을 막 깐 사람에게 첫날부터 알리면 그저 성가십니다.

**하루의 걸음은 고정이 아닙니다.** 무엇을 축으로 삼을지는 설정에서 고릅니다
(`DailyStep` · `SettingsStore.dailySteps`). `DailyStep.entries`를 화면이나 셈에 그대로 쓰지 말고
`DailyProgress.steps`를 쓰세요. 안 고른 걸음이 남으면 채울 수 없는 하나 때문에 이어 온 날이
매일 끊깁니다. **연속 날수는 저장하지 않고 기록에서 다시 셉니다.** 그래서 축을 바꾸면 지난
날수도 곧바로 새 기준이 됩니다. 저장해 두면 옛 기준으로 쌓인 숫자와 새 기준이 섞입니다.
걸음을 늘리면 `DailyLoopCalculator`의 `daysByStep`과 앱의 탭 이동(`selectTab`)을 함께 늘립니다.

**하루 점수(`DayScore`)에 기분을 넣지 않습니다.** 슬픈 날이 낮은 점수가 되면 앱이 감정을
잘못한 일로 세는 셈이고, 힘든 날일수록 열기 싫어집니다. 기분은 점수 **곁에** 둡니다.
점수의 뼈대는 사용자가 고른 걸음(`DailyStep`)입니다 — 앱이 따로 정한 잣대로 매기면 자기가
고른 축과 점수가 어긋납니다. 걸음을 넘겨 더 한 것은 덤으로 세되 상한을 둡니다. 끝없이 오르면
개수 채우기가 됩니다. **점수도 저장하지 않고 기록에서 다시 셉니다** — 연속 날수와 같은
이유입니다. 걸음을 바꾸면 지난날 점수도 곧바로 새 기준이 됩니다.

**돌아보기는 쌓이는 것을 전제로 둡니다.** 하루 주기로 돌리면 한 해에 삼백 장이 넘습니다.
검색(`AppSearch`의 `SearchKind.GROWTH`) · 목록 거르개 · 한 번에 정리 셋이 함께 있어야 합니다.
정리는 **몇 편을 남길지**로 묻습니다. 지울 개수로 물으면 무엇이 사라지는지 세어 봐야 알고,
잘못 세면 되돌릴 수 없습니다. 여러 편을 지울 때도 삭제 표식은 **한 편마다** 남깁니다
(`deleteGrowthReports`). 빠뜨리면 지운 것이 다음 병합에 되살아납니다.

**추천 글귀는 이미 가진 것을 되돌려 주지 않습니다.** 물음에 보관함 목록을 `ownedQuoteLines`로
함께 싣습니다. **추천을 받으려는 것이 아니라 빼 달라는 목록입니다.** 기록만 보여 주면 모델은
거기 있던 글귀를 그대로 돌려주어, 자기가 쓴 것을 자기가 추천받게 됩니다. `isEmpty`에는 세지
않습니다. 담아 둔 것만 있고 이 기간에 한 일이 없으면 돌아볼 것이 없는 것입니다.

**돌아보기는 지난번에 권한 것에서 이어집니다.** 물음에 직전 `GrowthReport`의 `improvements`와
`suggestedRoutines`를 함께 싣습니다(`GrowthPrompt.previousAdvice`). 빼면 매번 처음 만난 사람처럼
말하고 사용자는 같은 조언을 몇 번이고 다시 받습니다. **`strengths`와 `guide`는 싣지 않습니다.**
잘한 점은 같은 칭찬을 되풀이하게 하고, 가이드는 자유롭게 쓴 글이라 그때 읽은 일기가 묻어날 수
있습니다. 사용자가 그 뒤로 일기 본문 보내기를 껐다면 껐다는 뜻이 지난 글을 통해 조용히
뒤집힙니다. 지난 조언은 `isEmpty`에 세지 않습니다. 이 기간에 한 일이 없는데 조언만 들고
다시 물으면 같은 말이 돌아옵니다.

**AI 공급자를 늘리면 앱 `AiProvider`와 서버 `aiProviders`를 함께 늘립니다.** 어디에 물어볼지는
기기가 정하고(`provider`), 서버는 그에 맞는 열쇠만 골라 씁니다. 서버 쪽을 빠뜨리면 사용자가 고른
곳이 아닌 데로 기록이 나갑니다. 열쇠는 공급자마다 따로 둡니다(`openAiKey`·`anthropicKey`).
한 자리를 나눠 쓰면 바꿔 보는 사이에 앞서 넣은 열쇠가 지워집니다. 모델도 공급자마다 다르므로
`effectiveModel`이 그 공급자에 없는 이름을 기본값으로 당겨 붙입니다.

**AI에 못 붙어도 막다른 길이 아니어야 합니다.** 물음을 복사해 채팅창에 붙여넣고 받아 온 답을
도로 넣는 길이 있습니다(`GrowthPrompt.chatPrompt` · `GrowthFeedbackCoordinator.saveManualAnswer`).
미리 보기와 복사되는 글은 **같아야 합니다.** 달라지면 무엇이 밖으로 나가는지 확인할 방법이
없어집니다. 붙여넣은 답은 형식이 어긋나도 버리지 않고 통째로 `guide`에 담습니다.
사람이 옮겨 온 글을 "못 읽었다"며 되돌려 주면 그 자리에서 사라집니다.

**동기화 JSON 포맷을 바꾸면 앱 `AppDataJson`과 서버를 함께 바꾸고 `schemaVersion`을 올립니다.**

서명 정보(`keystore.properties`, `*.jks`)는 커밋하지 않습니다. `keystore.properties.example`을 참고하세요.

동기화 규칙과 서버 운용은 `server/README.md`에 자세히 적혀 있습니다.

어디까지 왔고 무엇이 남았는지는 `BACKLOG.md`에 있습니다. 미룬 것과 그 이유도 거기 적습니다.
이 파일은 지켜야 할 규칙만 담습니다 — 어겼을 때 조용히 망가지는 것들입니다.
