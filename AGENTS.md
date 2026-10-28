# AppGoodWords — 작업 지침

글귀를 모아 다시 만나게 하고, 하루를 쌓고 돌아보는 Android 앱(`app/` — Kotlin·Compose·Room·WorkManager·Glance)과,
여러 기기가 같은 데이터를 쓰게 하는 Node 서버·웹(`server/` — 표준 라이브러리만)입니다.

**여기에는 코드를 읽어도 알 수 없는 것만 둡니다** — 명령, 일하는 자리, 어겼을 때 **조용히** 망가지는 제약.
규칙의 이유는 그 규칙을 지키는 코드의 주석에 있습니다.

| 문서 | 담는 것 |
|---|---|
| `AGENTS.md` | 명령·환경·제약 (`CLAUDE.md`는 이 파일을 가리키기만 합니다) |
| `MANUAL.md` | 사람이 앱과 서버를 쓰는 법 |
| `server/README.md` | 서버 운용, 동기화 규약, REST API |
| `BACKLOG.md` | 남은 일과 미룬 이유 |

**지금 상태는 문서가 아니라 저장소에서 봅니다** — 앱 버전은 `app/build.gradle.kts`, 동기화 형식은 `AppDatabase.version`과
서버 `/api/health`의 `schemaVersion`, 무엇을 언제 올렸는지는 `git log`(게시 커밋 제목 `X.Y.Z / versionCode N 게시`),
**무엇을 어떻게 검증했는지는 커밋 메시지**입니다. `captures/`는 커밋되지 않습니다.

## 일하는 자리

- **운영 서버는 맥미니입니다.** launchd(`~/Library/LaunchAgents/com.appgoodwords.server.plist`)가 **이 저장소 폴더의**
  `server/`를 띄우고, DB·첨부는 `~/Library/Application Support/AppGoodWordsServer`에 있습니다. 휴대폰은 Tailscale Serve
  HTTPS(443 → 127.0.0.1:8765)로 붙습니다.
  - **이 폴더에서 브랜치를 바꾸거나 서버 코드를 고친 채로 두지 마세요.** launchd가 서버를 다시 띄우는 순간 그 코드가
    운영에 올라갑니다. 서버를 고칠 때는 `git worktree`로 옆 폴더에서 합니다.
  - 스키마를 바꿔 게시했으면 서버를 새 코드로 다시 띄웁니다: `launchctl kickstart -k gui/$(id -u)/com.appgoodwords.server`.
    `schemaVersion`이 다르면 앱이 동기화를 막습니다.
- **빌드와 에뮬레이터는 윈도우 데스크톱입니다.** 맥에는 JDK·Android SDK와 디버그 서명 열쇠가 없습니다(맥에서 빌드한
  APK는 덮어 설치되지 않습니다). worktree `C:/coding/ChatGPT/AppGoodWords-ollama`, AVD `AppGoodWords_API_35`.
  - 원격 실행은 Orca(`orca terminal create --environment win-desktop`), 보낼 파일은 `tailscale file cp … <윈도우 기기 이름>:`
    (윈도우의 Downloads로 갑니다). 맥으로 돌려받을 파일은 맥의 Downloads를 읽을 수 없으니 일회용 받는 곳(토큰을 보는 PUT
    서버를 127.0.0.1에 띄우고 `tailscale serve --bg --https=8444`)으로 받고, 끝나면 내립니다.
  - PowerShell에서 Gradle은 `Start-Process cmd.exe … -PassThru` + `WaitForExit`로 부릅니다. 감싸 부르면 데몬이 출력 관을
    물고 있어 끝나지 않습니다.
- **GitHub 쓰기(푸시·PR·머지)는 윈도우 저장소** `C:/coding/ChatGPT/AppGoodWords`의 `gh api`로 합니다. 맥의 `gh`는
  로그인되어 있지 않고, 맥은 `git fetch`(읽기)만 됩니다.

## 빌드와 테스트

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest   # 게시하는 것은 디버그 APK
.\gradlew.bat assembleRelease                                            # R8·lintVital 확인
node --test server/tests/server.test.mjs server/tests/transport.test.mjs server/tests/web.test.mjs server/tests/publish.test.mjs
```

새 서버 시험 파일을 만들면 위 줄에 더합니다. **실패·건너뜀이 0인지** 봅니다.

## 계측 테스트 (에뮬레이터)

- **실기기가 붙어 있으면 `connectedDebugAndroidTest`를 쓰지 마세요** — 붙은 기기 전부에서 돌고 끝난 뒤 앱을 지웁니다
  (사용자 DB가 사라집니다). `adb -s emulator-5554`로 두 APK를 설치하고 `am instrument -w -e class …`로 부릅니다.
  `assembleDebugAndroidTest`는 앱 APK를 다시 싸지 않으니 **둘 다 빌드해 둘 다 설치합니다.**
- 종류별로 나눠 부릅니다(한 번에 돌리면 에뮬레이터가 죽습니다). **묶음 사이에 `pm clear`**, 그 뒤 **알림 권한을 도로 줍니다**
  (`pm grant com.codex.appgoodwords android.permission.POST_NOTIFICATIONS` — 없으면 권한 창이 덮어 "No compose hierarchies found").
- **앱↔서버 시험은 운영과 분리된 HTTPS 시험 서버로** — `-e testServerUrl https://<맥>.ts.net:8443 -e testServerApiKey <가짜 키>`.
  주소를 빼면 그 시험이 건너뜁니다. 시험 서버는 고친 서버 코드로 띄웁니다:
  `node server/app_good_words_server.mjs --host 127.0.0.1 --port 18875 --db <임시 파일>` + `tailscale serve --bg --https=8443 18875`
  (끝나면 `--https=8443 off`. 운영 443은 그대로). AI는 `APP_GOOD_WORDS_OLLAMA_URL`로 답 시간을 정한 가짜 Ollama를 붙이면 됩니다.
- 에뮬레이터는 **`-dns-server 100.100.100.100`**으로 띄웁니다(`*.ts.net`을 풀려면). 그러면 바깥 이름이 안 풀려 망 검증이
  실패하고 망을 조건으로 건 일꾼이 영영 돌지 않으니, `settings put global captive_portal_mode 0`·`captive_portal_detection_enabled 0`
  뒤 와이파이·데이터를 껐다 켭니다. 인증서 검증을 끄거나 평문·시험 CA 예외를 넣지 않습니다.
- 자판으로 한글을 칠 수 없습니다(`adb shell input text`). 눌러 보기에서 적는 내용은 영어로 씁니다.

## 게시

1. 기능 커밋 뒤에 `versionCode`·`versionName`만 올린 **게시 커밋**(`X.Y.Z / versionCode N 게시`)을 둡니다.
2. 윈도우에서 그 커밋으로 `assembleDebug` — 서명 인증서 SHA-256이 지난 판과 같아야 덮어 설치됩니다(`apksigner verify --print-certs`).
3. PR을 머지하고, 맥 `main`의 파일 트리가 빌드한 트리와 같은지 본 뒤 `node server/publish_update.mjs --apk <app-debug.apk>`
   (`output-metadata.json`이 APK 옆에 있어야 합니다). 게시 규칙은 이 파일 한 곳에 있습니다(`publish_update.ps1`은 부르기만).
4. 휴대폰이 받는 길 그대로 `/update/latest.json`과 APK를 받아 **해시를 맞춰 보는 것까지**가 게시입니다.

- **운영 서버에는 시험 데이터를 넣지 않습니다.** 서버 프로세스를 작업 세션의 자식으로 띄우지 않습니다(세션과 함께 죽습니다).
- launchd는 PATH를 거의 비워 넘깁니다 — plist의 `PATH`·`run_server.sh`의 찾기·서버 `extraCliDirs`가 `node`와 CLI를 찾습니다.
  서버는 윈도우(`run_server.cmd`·`%LOCALAPPDATA%\AppGoodWordsServer` 설치본)에서도 돌아야 하니 새 도구는 Node로 씁니다.

## 실기기를 건드릴 때

**설치하기 전에 DB를 빼 둡니다**(`adb exec-out run-as com.codex.appgoodwords cat databases/app-good-words.db > backup.db`).
마이그레이션이 틀리면 `fallbackToDestructiveMigration()`이 조용히 전부 지웁니다. 설치 뒤 `user_version`과 레코드 수를 대조합니다
(기기에 `sqlite3`가 없어 에뮬레이터에서 셉니다. Git Bash는 `MSYS_NO_PATHCONV=1`).

## 윈도우에서 조심할 것

- **PowerShell로 소스를 일괄 치환하지 마세요** — 5.1은 UTF-8을 ANSI로 읽어 한글이 전부 깨집니다. 편집 도구로 고칩니다.
- **한글이 든 `.ps1`은 UTF-8 BOM**으로 저장합니다(없으면 파싱부터 어긋납니다).
- **`Invoke-RestMethod -Body`에 한글을 쓰지 마세요**(ANSI로 나갑니다). 확인용 요청은 ASCII로.

## 비밀

- 서명 정보(`keystore.properties`·`*.jks`), AI 열쇠, 서버 접속 키, 페어링 링크, Tailscale 이름·IP는 커밋·PR·문서·로그에
  넣지 않습니다. 열쇠는 기기(`SettingsStore`)나 서버 환경 변수에만 둡니다.
- **CLI 공급자는 서버가 돌립니다(`askCli`).** 물음을 명령줄 인자로 넘기지 않고(표준 입력·임시 파일), **도구 없이·빈 임시
  폴더에서·대화를 남기지 않고** 돌립니다. 하나라도 풀면 기록 속 문장이 서버 파일을 읽게 하거나 일기가 디스크에 쌓입니다.
- **AI에 무엇이 나가는지는 사용자가 정합니다** — 일기 본문(`includeDiaryBody`)·목표 탭(`includeGoals`) 모두 **기본 꺼짐,
  기본값을 바꾸지 마세요.** 물음은 앱만 짓고 서버 `/api/growth-feedback`은 전달만 합니다.

## Room 스키마

바꾸면 **`Migration`과 `Migration{N}To{M}Test`를 함께** 더합니다(없거나 틀리면 DB가 오류 없이 지워집니다). 동기화 JSON을
바꾸면 앱 `AppDataJson`과 서버를 함께 바꾸고 `schemaVersion`을 올립니다.

## 한 곳에서만 정하는 것

새 화면이 같은 셈을 필요로 하면 **여기 있는 것을 부르고, 없으면 여기에 더합니다.** 따로 세면 두 화면이 다른 숫자를 말합니다.

| 무엇 | 자리 |
|---|---|
| 이어 온 날수 · 하루 점수·오늘 한 것 · 하루의 걸음 | `DayStreak` · `DayScoreCalculator.digest` · `DailyLoopCalculator` |
| 그날의 기분 | `DayMood` (그래프 `byDate`, 기분별 셈 `settledByDate`) |
| 루틴 하나의 횟수·연속·요일 · 오늘 안 밟은 루틴 · 루틴 차례 | `RoutineStatsCalculator` · `RoutineNudgeCalculator.restOfToday` · `RoutineOrder` |
| 목표가 어떻게 따라가지는지(되새김·이어 둔 루틴·롤모델 글귀·홈 한 줄) | `GoalTracking` |
| 날짜별 독서 진행 · 언제 돌아봤는지(날짜 차) | `BookProgressCalculator` · `GrowthCadence` |
| 담기 전 다듬기 · 읽음으로 세지 않는 종류 · AI 추천 글귀의 갈래 | `ContentNormalizer` · `ContentType.personalTypes` · `AI_CATEGORY` |
| AI에 일기·목표가 어디까지 나가는지 | `GrowthPrompt.diaryLine` · `GrowthPrompt.directionLines` (돌아보기·한마디 공용) |
| 지금의 글귀(홈 맨 위·위젯·알림이 함께 보는 것) | `SettingsStore.widgetContentIdFlow` · 고르기 `QuoteWidget.pickNextItem` |
| 오늘의 한마디를 물을 때인지 · 다음에 깨울 때 | `DailyCoachState.needsAsk` · `DailyCoachSchedule` |
| 색·글자·모서리·움직임·진동 | `ui/theme`(`Color`·`Type`·`Theme`·`Motion`) · `ui/screen/Feedback.kt` |

## 함께 고쳐야 하는 것

하나만 빠뜨리면 오류 없이 어긋납니다(괄호는 서버·웹 이름).

| 바꾸는 것 | 함께 고치는 곳 |
|---|---|
| 새 레코드 종류 | `AppDataJson` · `SyncMerger` · `SyncDeduplicator` · `SnapshotReindexer` · `AppDataImporter` · 서버 `normalizeDb`/`mergeSnapshot`/`replaceSnapshot`/`reindex`/`deduplicate` · 웹(탭·화면·`emptySnapshot`) · 삭제 표식 종류(`SyncEntityType`·`deletionEntityTypes`) · `AppDataSnapshotCountTest` |
| 보관함 종류 `ContentType` | 서버 `contentTypes`(모르는 종류는 글귀로 바뀝니다) · 웹 `app.js` 이름표 · 내가 쓴 것이면 `personalTypes`/`personalContentTypes` · 제 탭에서만 보는 것이면 웹 `ownTabTypes` · `ContentType`을 `when`으로 가르는 이름표들 |
| 보관함 기록의 칸(`ContentItemEntity`) | `AppDataJson` · 편집 저장이 칸을 지키는지(`saveContent`·서버 `saveContent`) · 서버 `normalizeContent`(빠뜨리면 병합 때마다 사라집니다) |
| 목표에 이어 둔 루틴(`linkedRoutineSyncIds`) | 같은 내용 합치기로 루틴이 줄 때 앱 `SyncDeduplicator`·서버 `deduplicate`가 같이 옮깁니다 |
| 첨부 주소 형식 | 앱 `AttachmentUris.SCHEME` · 서버·웹 `attachmentScheme` |
| 날씨·기분·일기 종류와 **물음 문구·순서** | 앱 `DiaryTags.kt` · 웹 `weatherOptions`·`moodOptions`·`diaryKinds` |
| 루틴 차례 옮기기 · 루틴 통계 · 날짜별 독서 | 앱 `RoutineOrder`·`RoutineStatsCalculator`·`BookProgressCalculator` · 서버·웹의 같은 이름 함수 |
| 같은 내용 합치기 | 앱 `SyncDeduplicator` · 서버 `deduplicate()` (판정·승자가 어긋나면 두 기기가 끝없이 서로를 고칩니다) |
| 삭제 표식 보관 기간 | `SyncCoordinator.DELETION_RETENTION_DAYS` · 서버 `deletionRetentionDays` |
| AI 공급자 · 모델 이름 | 앱 `AiProvider` · 서버 `aiProviders`의 기본 모델. 이름은 Codex `~/.codex/models_cache.json`·공급자 문서에서 보고, 시험 서버 `/api/growth-feedback`로 하나씩 불러 실제로 도는지 확인합니다(구독에서 막힌 모델은 CLI 목록에 넣지 않습니다) |
| 업데이트 매니페스트 꼴 | `server/publish_update.mjs` · 앱 `AppUpdateJson` |
| 하루의 걸음 추가 | `DailyLoopCalculator.daysByStep` · `DayScoreCalculator`(`digest`·`trend`) · 홈 `onOpenStep` · `stepColors` · 설정 |
| 글 쌓이는 새 자리 | `AppSearch`에 찾을 길(딸린 글은 부모에 얹기) |
| `routineChecks`를 "실천했다"로 세는 곳 | `DailyLoop` · `StatsSummary` · `FeedbackWriter` · `MoodPractice` · `GrowthPrompt` · `DailyCoachPrompt` · `RoutineNudge` |
| 홈 목록의 줄(`HomeScreen`) | `QUOTE_ITEM_INDEX`·`TAB_ROW_ITEM_INDEX`·`PAGES_ITEM_INDEX` |

## 조용히 망가지는 것들

- **기기 간 식별자는 `syncId`뿐입니다.** 숫자 id는 기기마다 다르고 저장 직전에 다시 매겨집니다. syncId가 있는 기록은 숫자 id를
  같이 보지 않습니다. 부모를 지워도 뽑아 둔 것은 남기고(책↛글귀·독서 기록, 글귀↛루틴), 합치기로 부모가 줄면 남은 쪽으로 옮깁니다.
- **일부만 실어 보내는 편집은 기존 값을 지킵니다**(앱 `saveContent`·`saveGoal`·`saveBook`·`saveRoutine`, 서버 `{...existing, ...payload}`).
- **웹에서 저장·삭제할 때 `updatedAt`을 올리고 한 편마다 삭제 표식을 남깁니다**(`deleteWithTombstone`). 빠지면 기기의 옛 사본이 되살립니다.
- 서버는 바뀐 레코드에만 새 리비전을 붙이고, 앱은 부분 응답을 `applyDelta`로 얹기만 합니다(없는 것은 지운 것이 아니라 안 바뀐 것).
- **일기 답(`answers`)은 물음 자리에 맞춘 목록이라 가운데 빈칸을 버리지 않습니다**(`DiaryAnswers.normalize`·`normalizeAnswers`·
  `DiaryAnswerConverters`).
- 공유로 들어온 글(`ShareTargetActivity`)은 `documentLaunchMode="always"`와 `onNewIntent` 저장을 지키고, 토스트는 메인 스레드에서만.
- 분류·설명은 같은 기록의 종류만 바꿉니다(새로 만들면 첨부·덧메모·syncId가 끊깁니다).
- **탭 번호는 이름표일 뿐입니다** — 보관함 `LibraryTab`(메모·개념·임시저장소 번호는 노트 칸 알약), 홈 `HomeTab`. 검색이 번호로
  짚어 오므로 차례에 맞춰 번호를 바꾸지 않습니다.
- **오늘의 한마디의 배경은 알람이 깨웁니다**(`DailyCoachAlarm` → 곧바로 도는 일꾼). WorkManager에 "주기 뒤"로 미루면 휴대폰을
  내려놓은 동안 몇 시간씩 밀려 앱을 열 때만 왔습니다. 알람은 재부팅·업데이트로 사라지니 `DailyCoachAlarmReceiver`가 다시 겁니다.
  홈의 물음은 앱이 화면에 있을 때만 나갑니다(Android 15는 화면 밖 앱의 통신을 끊습니다).
- **목록 화면에서 다른 경로로 넘어갈 때 대화상자를 먼저 닫습니다** — 열린 채로 넘어가면 그 경로의 상태와 함께 저장되어,
  돌아왔을 때 창이 다시 뜨고 앞서 친 글이 다음 창에 남습니다(`GoalTabsScreen`).
- 앱 업데이트는 서버가 준 파일 이름을 그대로 믿지 않고, 해시가 다르면 지우고, 접속 키를 보내지 않습니다(`AppUpdateJson`).
- 자판이 오르면 창을 줄입니다(`adjustResize`) — 화면에서 `imePadding()`을 또 붙이지 않고, 긴 글 화면의 버튼은 글 위에 둡니다.

## 이 앱이 지키는 원칙

어기면 기능이 아니라 잔소리가 됩니다.

- **못 한 것을 세지 않습니다.** "0일 연속"·빠진 날·남은 개수 대신 **다음 하나**와 한 것만 말합니다.
- **셈한 것은 저장하지 않고 기록에서 다시 셉니다**(연속·점수·통계·목표의 실천).
- 하루 점수에 기분을 넣지 않습니다.
- **값이 드는 일은 사용자가 정하고 몰래 돌지 않습니다** — 횟수·주기·배경 스위치가 설정에 보입니다. 알리기만 하는 것은 아무것도
  만들지 않고, 가벼운 쪽은 AI를 부르지 않습니다.
- AI에 못 붙어도 막다른 길이 아닙니다 — 미리 보기와 복사되는 글은 같고, 붙여넣은 답은 형식이 어긋나도 버리지 않습니다.
- 추천은 이미 가진 것을 되돌려 주지 않습니다(`ownedQuoteLines`). 지난 조언의 `strengths`·`guide`는 다시 싣지 않습니다.
- **내가 쓴 것은 남의 좋은 말인 척 돌아오지 않습니다**(`personalTypes` — 메모·개념·원칙·이상향·목표·롤모델 등은 알림·위젯·
  읽음·지난해 오늘·AI 추천 원문에서 빠집니다). 원칙에는 수행 버튼이 없습니다.
- 끈 기능을 켜 보라고 홈에 매일 띄우지 않습니다(한마디 칸·목표 한 줄은 켰거나 적었을 때만).
- 진동은 무게 셋(`tap`·`commit`·`celebrate`)뿐이고 한 동작에 한 번입니다.

## 작업을 마칠 때

1. 유닛·서버 시험(필요하면 계측)의 **실패·건너뜀이 0**인지 봅니다. 화면이 바뀌었으면 **에뮬레이터에서 실제로 눌러 봅니다.**
2. 커밋 메시지에 **무엇을 왜 바꿨는지와 검증 결과**를 적습니다. 다음 세션이 보는 것은 이것입니다.
3. 맞지 않게 된 문서는 함께 고칩니다. 문서를 새로 늘리지 않습니다.
4. 게시·배포·푸시는 **사용자가 말했을 때만** 합니다.
