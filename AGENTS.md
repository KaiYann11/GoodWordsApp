# AppGoodWords — 작업 지침

좋은 글귀를 모아 두고 알림·위젯으로 다시 만나게 하는 Android 앱(`app/`)과, 여러 기기가 같은
데이터를 쓰도록 하는 Node 서버(`server/`)입니다.

- `app/` — Kotlin, Jetpack Compose, Room, WorkManager, Glance 위젯
- `server/` — Node 표준 라이브러리만 쓰는 REST 서버와 웹 UI (`app_good_words_server.mjs`)

**이 파일에는 코드를 읽어도 알 수 없는 것만 둡니다** — 명령어, 이 PC의 환경, 어겼을 때
**조용히** 망가지는 제약. 어떤 규칙이 왜 그런지는 그 규칙을 지키는 파일의 주석에 있습니다.
여기서는 "무엇을 함께 고쳐야 하는가"만 짚습니다.

| 문서 | 담는 것 |
|---|---|
| `AGENTS.md` (이 파일) | 빌드·테스트·게시 명령, 환경, 제약 |
| `MANUAL.md` | 사람이 앱과 서버를 쓰는 법 |
| `server/README.md` | 서버 설치·운용과 동기화 규약, REST API |
| `BACKLOG.md` | 어디까지 왔고 무엇이 남았는지, 미룬 것과 그 이유 |

`CLAUDE.md`는 이 파일을 가리키기만 합니다. 지침은 한 곳에만 둡니다.

## 현재 상태는 문서가 아니라 저장소에서 봅니다

버전·개수를 문서에 적으면 반드시 어긋납니다. 이어서 작업할 때는 여기를 봅니다.

| 알고 싶은 것 | 보는 곳 |
|---|---|
| 앱 버전 | `app/build.gradle.kts`의 `versionCode`·`versionName` |
| 동기화 형식 | `AppDatabase`의 `version`, 서버 `/api/health`의 `schemaVersion` |
| 무엇을 언제 올렸나 | `git log --oneline` — 게시 커밋 제목은 `X.Y.Z / versionCode N 게시` |
| 무엇을 어떻게 검증했나 | 커밋 메시지. **검증 결과는 커밋 메시지에 적습니다** |
| 남은 일·미룬 이유 | `BACKLOG.md` |

`captures/`는 `.gitignore`에 있습니다. 스크린샷·로그·배포 스크립트가 거기 쌓이지만 **저장소에
들어가지 않으므로 다른 PC나 다른 세션에서는 볼 수 없습니다.** 남길 가치가 있는 것은 커밋
메시지나 위 네 문서에 적습니다.

## 빌드와 테스트

프로젝트 루트에서 실행합니다. PowerShell 기준이고, Git Bash에서는 `./gradlew`를 씁니다.

```powershell
.\gradlew.bat testDebugUnitTest        # JVM 유닛 테스트
.\gradlew.bat assembleDebug            # 디버그 빌드(게시하는 것이 이것입니다)
.\gradlew.bat assembleRelease          # R8까지 통과하는지 확인
node --test server/tests/server.test.mjs server/tests/transport.test.mjs server/tests/web.test.mjs server/tests/publish.test.mjs
```

새 시험 파일을 만들면 위 `node --test` 줄에 더하고 이 문서도 함께 고칩니다.

## 계측 테스트 (에뮬레이터)

**실기기가 붙어 있을 때 `connectedDebugAndroidTest`를 돌리지 마세요.** AGP가 붙어 있는 기기
**전부**를 대상으로 삼고, 끝난 뒤 앱을 지웁니다. 사용자 DB가 함께 사라집니다. 실기기가
연결된 동안에는 에뮬레이터를 지정해 직접 부릅니다.

```bash
adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e testServerUrl https://<test-server> -e testServerApiKey <synthetic-key> com.codex.appgoodwords.test/androidx.test.runner.AndroidJUnitRunner
```

- `assembleDebugAndroidTest`는 앱 APK를 다시 싸지 않습니다. **둘 다 빌드해 둘 다 설치합니다.**
- **묶음 사이에 `pm clear`를 넣습니다.** 백업 파일이 앱 안에 쌓여 목록 개수를 보는 시험이 깨집니다.
  **`pm clear` 뒤에는 알림 권한을 도로 줍니다** — `pm grant com.codex.appgoodwords
  android.permission.POST_NOTIFICATIONS`. 없으면 권한 창이 화면을 덮어 "No compose hierarchies found"가 납니다.
- 전체를 한 번에 돌리면 에뮬레이터가 자주 죽으므로 종류별로 나눠 부릅니다.
- **앱↔서버 시험에는 운영과 분리된 HTTPS 테스트 서버가 필요합니다.** 주소를 생략하면 그 시험이
  건너뛰므로 **skipped가 0인지 확인합니다.** 지정한 주소가 연결되지 않으면 실패해야 합니다.

```powershell
node server/app_good_words_server.mjs --host 127.0.0.1 --port 18875 --db captures/test-server.json
tailscale serve --bg --https=8443 18875     # 끝나면 tailscale serve --https=8443 off (운영 443은 그대로)
```

에뮬레이터는 **`-dns-server 100.100.100.100`을 붙여 띄웁니다.** 그래야 `*.ts.net` 이름이
풀립니다 — 기본 DNS로는 `UnknownHostException`만 나고, IP로 부르면 인증서 이름이 맞지 않아
앱이 막습니다. 인증서 검증을 끄거나 테스트용 평문·테스트 CA 예외를 넣지 않습니다.

## 이 PC의 서버와 게시

서버는 `%LOCALAPPDATA%\AppGoodWordsServer`에 **따로 설치되어 있습니다.** DB·첨부·접속 키가 그
폴더에 있고 시작프로그램 바로가기로 로그인 때 뜹니다. **저장소의 소스를 고쳐도 설치된 서버에는
반영되지 않습니다.** 휴대폰 주소는 Tailscale HTTPS(`https://<host>.ts.net` → 127.0.0.1:8765)이고,
설치된 Node는 127.0.0.1에만 바인드합니다. 앱 1.1.1부터 HTTP 동기화·첨부·AI 프록시를 차단합니다.

```powershell
# 1. versionCode를 올리고 같은 디버그 서명으로 빌드한 뒤 게시합니다.
.\gradlew.bat assembleDebug
.\server\publish_update.ps1 -WebRoot "$env:LOCALAPPDATA\AppGoodWordsServer\app\web"
# 2. 서버 소스가 바뀌었으면 설치본도 갈아 끼우고 재시작합니다(아래 주의).
```

- 서명 인증서가 지난 버전과 달라지면 **덮어 설치되지 않습니다.** `apksigner verify --print-certs`로
  SHA-256을 대조합니다.
- `/update/`에서 받는 파일은 `latest.json`의 해시와 같아야 합니다. 게시 뒤 실제로 받아 보고
  해시를 맞춰 보는 것까지가 확인입니다.
- **서버를 이 세션의 자식 프로세스로 띄우지 마세요.** 세션이 끝나면 함께 죽습니다. 시작프로그램
  바로가기를 실행하거나 WMI `Win32_Process.Create`로 `Start-Server.ps1`을 독립 실행합니다.
  WMI로 띄워야 사용자 레지스트리 PATH를 받아 `claude`·`codex` CLI도 찾습니다.
- 배포 전후로 레코드 개수·DB 해시·접속 키 해시를 대조하고, 바꾼 파일은 설치 폴더의 `backups\`에
  백업합니다. 실패하면 되돌립니다. **운영 서버에는 테스트 데이터를 넣지 않습니다.**
- **스키마를 바꿨으면 서버도 새 코드로 다시 띄웁니다.** `schemaVersion`이 다르면 앱이 동기화를
  막고 계측의 앱↔서버 시험이 통째로 깨집니다.

**서버는 Windows와 macOS 양쪽에서 돌아야 합니다.** 본체·웹·시험은 Node 표준 라이브러리만 쓰므로
그대로 돌고, 다른 것은 띄우는 방법과 파일 두는 자리뿐입니다(`run_server.cmd`·`run_server.sh`·
`launchd/`). **새 도구는 Node로 씁니다** — 게시 규칙은 `server/publish_update.mjs` 한 곳에 있고
`publish_update.ps1`은 그것을 부르기만 합니다. 두 벌 적어 두면 한쪽만 고쳐졌을 때 운영체제에 따라
다른 `latest.json`이 나가고, 그 사실은 앱이 업데이트를 못 찾는 꼴로만 드러납니다. macOS에서
`launchd`로 띄우면 PATH가 `/usr/bin:/bin:/usr/sbin:/sbin`뿐이라 Homebrew·nvm으로 깐 `node`와 CLI가
보이지 않습니다 — plist의 `PATH`, `run_server.sh`의 찾기, 서버 `extraCliDirs`가 그래서 있습니다.

## 실기기를 건드릴 때

**설치하기 전에 DB를 먼저 빼 둡니다.** 마이그레이션이 틀리면 `fallbackToDestructiveMigration()`이
조용히 전부 지웁니다. 설치 뒤 `user_version`과 레코드 수를 대조합니다.

```bash
adb -s <기기> exec-out run-as com.codex.appgoodwords cat databases/app-good-words.db > backup.db
```

기기에는 `sqlite3`가 없으니 뺀 파일을 에뮬레이터로 밀어 넣고 거기서 셉니다. Git Bash는
`/data/...`를 윈도 경로로 바꾸므로 `MSYS_NO_PATHCONV=1`을 붙입니다(단, 로컬 파일 경로는 `C:/...`
꼴로 줘야 `stat`이 됩니다).

## 윈도우 환경에서 조심할 것

- **PowerShell로 소스 파일을 일괄 치환하지 마세요.** Windows PowerShell 5.1은 UTF-8 파일을 ANSI로
  읽어서 `Get-Content ... | Set-Content`를 거치면 한글 주석과 UI 문구가 전부 깨집니다.
  **파일 수정은 편집 도구로 합니다.**
- **한글이 든 `.ps1`은 UTF-8 BOM을 붙여 저장합니다.** 없으면 따옴표가 어긋나 파싱조차 안 됩니다.
- **`Invoke-RestMethod -Body`에 한글을 쓰지 마세요.** 본문을 ANSI로 보내 보낸 글과 돌려받은 글이
  달라집니다. 멀쩡한 배포를 되돌리게 됩니다. 확인용 요청은 ASCII로 보냅니다.

## 비밀

- 서명 정보(`keystore.properties`, `*.jks`)는 커밋하지 않습니다(`keystore.properties.example` 참고).
- AI 열쇠는 기기(`SettingsStore`)나 서버 환경 변수(`OPENAI_API_KEY`·`ANTHROPIC_API_KEY`)에만 둡니다.
  스냅샷·백업·로그·문서·채팅에 넣지 않습니다. 서버 접속 키도 같습니다.
- **CLI 공급자는 서버가 돌립니다(`askCli`).** 물음을 명령줄 인자로 넘기지 마세요 — 인자는 `ps`로
  보이고 따옴표·`%`·줄바꿈이 셸에서 깨집니다. 사용자 글은 표준 입력, 일러 주는 말은 임시 파일로
  건네고, 인자로 나가는 사용자 값은 글자를 검사한 모델 이름뿐입니다(`aiModelPattern`).
  CLI는 **도구 없이**(`--tools ""`·`--disable shell_tool`) **빈 임시 폴더에서** **대화를 남기지
  않고**(`--no-session-persistence`·`--ephemeral`) 돌립니다. 하나라도 풀면 기록 속 문장이 서버
  파일을 읽게 하거나 일기가 CLI 세션 기록으로 디스크에 쌓입니다.
- **AI에 무엇이 나가는지는 사용자가 정합니다.** 일기 본문은 설정에서 켜야만 실립니다
  (`AiFeedbackSettings.includeDiaryBody`, 기본 꺼짐). **기본값을 바꾸지 마세요 — 한번 나가면
  되돌릴 수 없습니다.** 물음은 앱(`GrowthPrompt`·`DailyCoachPrompt`)만 만들고 서버
  `/api/growth-feedback`은 그대로 전달만 합니다. 서버가 기록을 다시 읽어 물음을 짜면 사용자가
  앱에서 정해 둔 범위가 조용히 뒤집힙니다.

## Room 스키마

**바꾸면 `Migration`과 `Migration{N}To{M}Test`를 함께 추가합니다.** `AppContainer`에
`fallbackToDestructiveMigration()`이 걸려 있어서, 마이그레이션이 없거나 틀리면 사용자 DB가 오류
없이 통째로 지워집니다. 동기화 JSON 포맷을 바꾸면 앱 `AppDataJson`과 서버를 함께 바꾸고
`schemaVersion`을 올립니다.

## 한 곳에서만 정하는 것

같은 셈을 화면마다 따로 하면 두 화면이 서로 다른 숫자를 말합니다. 새 화면이 같은 것을 필요로
하면 **여기 있는 함수를 부르고, 없으면 여기에 더합니다.**

| 무엇 | 유일한 자리 |
|---|---|
| 이어 온 날수 | `DayStreak` |
| 하루 점수·오늘 한 것 요약 | `DayScoreCalculator` (`digest`) |
| 하루의 걸음 진행 | `DailyLoopCalculator` / `DailyProgress.steps` |
| 그날의 기분 | `DayMood` (그래프는 `byDate`, 기분별 셈은 `settledByDate`) |
| 루틴 하나의 횟수·연속·요일 | `RoutineStatsCalculator` |
| 오늘 안 밟은 루틴 추리기 | `RoutineNudgeCalculator.restOfToday` |
| 날짜별 독서 진행 | `BookProgressCalculator` |
| 언제 돌아봤는지 | `GrowthCadence` (시간 차가 아니라 **날짜 차**로) |
| 담기 전 다듬기 | `ContentNormalizer` (담는 화면과 공유 두 길이 같이 씁니다) |
| 읽음으로 세지 않는 종류 | `ContentType.personalTypes` |
| AI 추천 글귀가 담기는 갈래 | `AI_CATEGORY` |
| 일기가 AI에 어디까지 나가는지 | `GrowthPrompt.diaryLine` (돌아보기·한마디 공용) |
| 루틴 차례 | `RoutineOrder` |
| 색·글자·모서리·움직임·진동 | `ui/theme`의 `Color`·`Type`·`Theme`·`Motion`, `ui/screen/Feedback.kt` |

## 함께 고쳐야 하는 것

하나만 빠뜨리면 오류 없이 어긋납니다. 괄호 안은 서버·웹 쪽 이름입니다.

| 바꾸는 것 | 함께 고치는 곳 |
|---|---|
| 새 레코드 종류 | `AppDataJson` · `SyncMerger` · `SyncDeduplicator` · `SnapshotReindexer` · `AppDataImporter` · 서버 `normalizeDb`/`mergeSnapshot`/`replaceSnapshot`/`reindex`/`deduplicate` · 웹 `server/web`(탭·화면·`emptySnapshot`) · 삭제 표식 종류(앱 `SyncEntityType`, 서버 `deletionEntityTypes`) · `AppDataSnapshotCountTest` |
| 보관함 종류 `ContentType` | 앱 · 서버 `contentTypes` · 웹 `app.js`의 고르개와 이름표 (읽음 제외 종류는 `personalContentTypes`/`personalTypes`도) |
| 첨부 주소 형식 | 앱 `AttachmentUris.SCHEME` · 서버 `attachmentScheme` · 웹 `app.js`의 `attachmentScheme` |
| 날씨·기분·일기 종류와 **물음 문구·순서** | 앱 `DiaryTags.kt` · 웹 `app.js`의 `weatherOptions`·`moodOptions`·`diaryKinds` |
| 루틴 차례 옮기기 | 앱 `RoutineOrder` · 서버 `sortedRoutines`/`moveRoutineTo`/`moveRoutine` · 웹 `sortedRoutines` |
| 같은 내용 합치기 | 앱 `SyncDeduplicator` · 서버 `deduplicate()` (판정과 승자 선택이 어긋나면 두 기기가 끝없이 서로를 고칩니다) |
| 삭제 표식 보관 기간 | 앱 `SyncCoordinator.DELETION_RETENTION_DAYS` · 서버 `deletionRetentionDays` |
| 루틴 통계 규칙 | 앱 `RoutineStatsCalculator` · 웹 `routineStatsOf` |
| 날짜별 독서 진행 규칙 | 앱 `BookProgressCalculator` · 웹 |
| AI 공급자 | 앱 `AiProvider` · 서버 `aiProviders` (열쇠는 공급자마다 따로, 모델 기본값은 `effectiveModel`) |
| 업데이트 매니페스트 꼴 | `server/publish_update.mjs` · 앱 `AppUpdateJson` |
| 하루의 걸음 추가 | `DailyLoopCalculator.daysByStep` · `DayScoreCalculator`의 `digest`와 `trend` · 홈의 탭 이동(`onOpenStep`) · 걸음 색(`stepColors`) · 설정 화면 |
| 글 쌓이는 새 자리 | `AppSearch`에 찾을 길을 함께 냅니다(딸린 글은 부모에 얹습니다) |
| `routineChecks`를 "실천했다"로 세는 곳 | `DailyLoop` · `StatsSummary` · `FeedbackWriter` · `MoodPractice` · `GrowthPrompt` · `DailyCoachPrompt` · `RoutineNudge` — **일곱 곳** |

## 조용히 망가지는 것들

- **기기 간 식별자는 `syncId`뿐입니다.** 숫자 id는 기기마다 따로 증가하고, 저장 직전에
  `SnapshotReindexer`(앱)·`reindex()`(서버)가 다시 매깁니다. **syncId가 있는 기록은 숫자 id를 같이
  보지 않습니다** — 둘 다 보면 다른 기기 기록의 숫자 id가 이쪽의 엉뚱한 레코드와 겹칩니다.
  부모를 못 찾는 메모는 버립니다(이력은 예외).
- **부모를 지워도 뽑아 둔 것은 남깁니다**(책↛글귀·독서 기록, 글귀↛루틴). 같은 내용 합치기로
  부모가 줄면 자식의 가리킴을 남은 쪽으로 옮깁니다.
- **일부만 실어 보내는 편집 폼은 기존 값을 지킵니다** — `saveBook`의 독후감·`startedAt`,
  `saveRoutine`의 출처. 서버도 `{...existing, ...payload}`입니다.
- **웹에서 저장·삭제할 때 `updatedAt`을 올리고 삭제 표식을 남깁니다**(`deleteWithTombstone()`,
  여러 편을 지워도 **한 편마다**). 빠지면 기기가 옛 사본을 올려 웹에서 한 일이 되돌아갑니다.
- **서버는 바뀐 레코드에만 새 리비전을 붙입니다**(`stampRevisions`·`withStableIds`). 부분 응답에
  없는 레코드는 지워진 것이 아니라 안 바뀐 것이라 앱은 `applyDelta`로 얹기만 합니다.
  루틴 차례도 실제로 달라진 것만 저장합니다.
- **일기 답(`answers`)은 물음 순서에 자리를 맞춘 목록입니다 — 가운데 빈칸을 버리지 않습니다**
  (버리면 뒤의 답이 앞 물음에 붙습니다). 앱 `DiaryAnswers.normalize`·서버 `normalizeAnswers()`,
  그리고 이 열에만 `DiaryAnswerConverters`(기본 `Converters`는 빈 문자열을 버립니다).
- **공유로 들어온 글**(`ShareTargetActivity`): `documentLaunchMode="always"` 유지 + `onNewIntent`
  에서도 저장(닫히는 화면에 다음 공유가 합쳐지면 원문을 놓칩니다). 저장은 `applicationScope`,
  토스트는 `Dispatchers.Main` — **배경 스레드에서 Toast를 만들면 저장 직후 앱이 죽습니다.**
- **분류·설명은 같은 기록의 종류만 바꿉니다**(`classifyInboxItem`·`saveConceptExplanation`).
  새 레코드를 만들거나 원본을 지우면 첨부·덧메모·syncId가 끊깁니다. AI 답은 **받은 뒤에** 종류를
  바꾸고, 다시 물으면 앞서 받은 `AI 설명` 메모의 같은 줄을 덮어씁니다(사람이 쓴 메모는 그대로).
- **보관함 탭 번호는 이름표일 뿐이고 차례는 `LibraryTab.ORDER`가 정합니다** — 검색이 번호로 탭을
  짚어 오므로 번호를 차례에 맞춰 바꾸면 찾아온 사람이 엉뚱한 탭에 떨어집니다. 메모·개념·임시저장소
  번호는 이제 노트 칸 안의 알약을 가리킵니다(`LibraryTab.NOTE_PILLS`) — 지우지 마세요.
- **앱 업데이트**(`AppUpdateJson`): 서버가 준 파일 이름을 그대로 믿지 않고(`app-good-words-….apk`
  꼴만), 해시가 다르면 받은 파일을 지우고, 동기화와 같은 HTTPS 규칙(`ServerUrlPolicy`)을 쓰고,
  설치는 안드로이드가 하고, 접속 키는 보내지 않습니다.
- **자판이 올라오면 창을 줄입니다**(`adjustResize`) — 화면에서 `imePadding()`을 또 붙이지 마세요.
  **긴 글을 쓰는 화면의 버튼은 글 위에 둡니다**(아래면 자판에 가려 저장을 못 누릅니다).

## 이 앱이 지키는 원칙

새 기능이 이것을 어기면 기능이 아니라 잔소리가 됩니다.

- **못 한 것을 세지 않습니다.** "0일 연속"·요일별 달성률을 적지 않고, 남은 개수 대신 **다음
  하나만** 말합니다. 돌아보는 자리가 성적표가 되면 열지 않게 됩니다.
- **셈한 것은 저장하지 않고 기록에서 다시 셉니다**(연속 날수·점수·통계·진행률). 저장하면 기록을
  고치거나 기준을 바꿨을 때 숫자와 기록이 어긋납니다.
- **하루 점수에 기분을 넣지 않습니다.** 점수의 뼈대는 사용자가 고른 걸음이고, 넘겨 한 것은
  상한 있는 덤입니다.
- **값이 드는 일은 사용자가 정하고 몰래 돌지 않습니다.** 횟수·주기는 설정에서(바닥·천장은 코드).
  **알리기만 하는 것(`GrowthNudgeWorker`)은 아무것도 만들지 않고**, **가벼운 쪽은 AI를 부르지
  않습니다**(`RoutineNudgeCalculator`). 오늘의 한마디는 사용자가 정해(2026-10-19) **주기마다 기록이
  그대로여도 묻고, 앱을 닫아 두어도 배경에서 돕니다**(`DailyCoachWorker`) — 대신 주기(10분~하루)·
  하루 횟수·배경 스위치가 설정에 보이고, 오늘 한 것이 없으면 묻지 않습니다. 묻는 규칙은
  `DailyCoachState.needsAsk` 한 곳이고 홈과 배경 일꾼이 같이 씁니다.
- **AI에 못 붙어도 막다른 길이 아닙니다** — 물음을 복사해 붙여넣고 답을 도로 넣는 길을 둡니다.
  **미리 보기와 복사되는 글은 같아야 하고**, 붙여넣은 답은 형식이 어긋나도 버리지 않습니다.
- **추천은 이미 가진 것을 되돌려 주지 않습니다**(`ownedQuoteLines`는 빼 달라는 목록입니다).
  지난번 조언은 싣되(`previousAdvice`) `strengths`·`guide`는 싣지 않습니다 — 가이드에는 그때
  읽은 일기가 묻어날 수 있습니다.
- **원칙(`PRINCIPLE`)에는 수행 버튼이 없습니다.** 지킨 날을 세면 또 하나의 루틴이 됩니다.
- **내가 쓴 것은 남의 좋은 말인 척 돌아오지 않습니다** — 번뜩인 것·메모·개념·임시저장소는 알림·
  위젯·지난해 글귀·AI 추천 원문에서 뺍니다(`personalTypes`·`readingTypes`).
- **진동은 무게 셋뿐**(`tap`·`commit`·`celebrate`)이고 **한 동작에 한 번** 울립니다. 숫자는 오르는
  것이 보람인 자리에만 굴립니다(`CountUpNumber`).

## 작업을 마칠 때

1. `testDebugUnitTest` · 서버 `node --test` · 필요하면 에뮬레이터 계측을 돌리고 **실패·건너뜀이
   0인지** 확인합니다.
2. 화면이 바뀌었으면 에뮬레이터에서 **실제로 눌러 봅니다.** 시험이 통과해도 화면에서는 안 보이는
   경우가 있습니다.
3. 커밋 메시지에 **무엇을 왜 바꿨는지와 검증 결과**를 적습니다. 다음 세션이 보는 것은 이것입니다.
4. 이 문서나 `MANUAL.md`·`BACKLOG.md`가 더는 맞지 않으면 함께 고칩니다. **문서를 새로 늘리지
   말고** 네 문서 중 맞는 자리에 넣습니다.
5. 게시·배포·푸시는 **사용자가 말했을 때만** 합니다.
