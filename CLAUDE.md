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

**이 PC의 서버는 `%LOCALAPPDATA%\AppGoodWordsServer`에 따로 설치되어 있습니다.**
DB·첨부·접속 키는 그 폴더에 있고, 시작프로그램 바로가기로 로그인 때 실행합니다.
저장소의 소스만 고치면 설치된 서버에는 반영되지 않으므로 배포할 때 서버 소스·웹 파일도 갱신합니다.
사용자 데이터와 `api-key.txt`는 보존합니다.
휴대폰용 APK를 갱신할 때는 `versionCode`를 올리고 같은 디버그 서명으로 빌드한 뒤
`server/publish_update.ps1 -WebRoot "$env:LOCALAPPDATA\AppGoodWordsServer\app\web"`로 게시합니다.
`/update/`에서 받는 설치 파일은 `latest.json`과 해시가 같아야 합니다.

휴대폰의 서버 연결은 같은 Tailscale의 HTTPS 주소를 사용합니다. 설치된 Node는 127.0.0.1에만
바인드하고 Tailscale Serve가 HTTPS를 제공합니다. 앱 1.1.1부터 HTTP 동기화·첨부·AI 프록시 연결을
차단합니다. 인증서 검증을 끄거나 테스트 목적으로 앱에 평문·테스트 CA 예외를 넣지 않습니다.

계측 테스트는 기기나 에뮬레이터가 필요합니다. **운영 DB와 분리된 HTTPS 테스트 서버**를 띄워
`testServerUrl`·`testServerApiKey` 계측 인자를 지정해야 앱↔서버 경로가 실행됩니다.
주소를 생략하면 통합 테스트가 건너뛰므로 skipped가 0인지 확인합니다. 지정한 주소가 연결되지
않으면 실패해야 합니다. 운영 서버에는 테스트 데이터를 넣지 않습니다.

```powershell
node server/app_good_words_server.mjs --host 127.0.0.1 --port 18875 --db captures/test-server.json
# 위 별도 서버를 HTTPS로 제공한 뒤 에뮬레이터를 지정해 아래처럼 계측합니다.
```

에뮬레이터에서도 인증서가 정상 검증되는 HTTPS 테스트 서버 주소를 사용합니다. 이 PC에서는
`tailscale serve --bg --https=8443 18875`로 위 서버를 잠깐 내보내고(끝나면
`tailscale serve --https=8443 off`; 운영 443은 그대로), 에뮬레이터는 **`-dns-server 100.100.100.100`을
붙여 띄웁니다.** 그래야 `*.ts.net` 이름이 풀립니다 — 기본 DNS로는 `UnknownHostException`만 나고,
IP로 부르면 인증서 이름이 맞지 않아 앱이 막습니다.

**실기기가 붙어 있을 때 `connectedDebugAndroidTest`를 돌리지 마세요.** AGP가 붙어 있는 기기
**전부**를 대상으로 삼고, 끝난 뒤 앱을 지웁니다. 사용자 DB가 함께 사라집니다. 실기기가
연결된 동안에는 에뮬레이터를 지정해 직접 부릅니다.

```bash
adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e testServerUrl https://<test-server> -e testServerApiKey <synthetic-key> com.codex.appgoodwords.test/androidx.test.runner.AndroidJUnitRunner
```

**묶음 사이에 `pm clear`를 넣습니다.** 백업 파일이 앱 안에 쌓여서, 이어 돌리면
`SyncStatusRefreshTest`처럼 목록 개수를 보는 시험이 옛 파일 때문에 깨집니다.
계측 전체를 한 번에 돌리면 에뮬레이터가 자주 죽으므로 종류별로 나눠 부르는 편이 낫습니다.
**`pm clear` 뒤에는 알림 권한을 도로 줍니다** — `pm grant com.codex.appgoodwords
android.permission.POST_NOTIFICATIONS`. 지우면 `MainActivity`가 뜨자마자 권한 창이 덮어서,
`NotToDoFlowTest`처럼 실제 화면을 여는 시험이 "No compose hierarchies found"로 깨집니다.

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
같은 이유로 **한글이 든 `.ps1`은 BOM을 붙여 저장합니다** — 없으면 따옴표가 어긋나 스크립트가 파싱조차
안 됩니다. 그리고 `Invoke-RestMethod -Body`는 본문을 ANSI로 보내므로, **배포 확인용 요청에는 한글을
쓰지 마세요.** 보낸 글과 돌려받은 글이 달라 멀쩡한 서버를 되돌리게 됩니다.

주석과 사용자에게 보이는 문구는 한국어로 씁니다.

**Room 스키마를 바꾸면 `Migration`과 `Migration{N}To{M}Test`를 함께 추가합니다.**
`AppContainer`에 `fallbackToDestructiveMigration()`이 걸려 있어서, 마이그레이션이 없거나 틀리면
사용자 DB가 오류 없이 통째로 지워집니다. 현재 버전은 22입니다.

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
`pickLeastRecentlySurfaced`가 `ContentType.readingTypes`에 있는 글귀·링크·영상만 고릅니다. 알림과 위젯이 말하는 것은
"오늘의 글귀"인데 내가 적어 둔 생각이 거기 뜨면 남의 좋은 말인 척 돌아오고, 새로 담은 것은
`lastSurfacedAt`이 없어 **가장 먼저** 뽑힙니다. 목록도 나눕니다 — 보관함의 아이디어 탭
(`LibraryTab`)입니다. 앞으로 "내가 쓴 것" 종류를 더하면 이 두 곳에서 함께 빼야 합니다.

**읽음으로 세지 않는 종류는 `ContentType.personalTypes` 한 곳에만 적습니다.** 예전에는 이 목록이
다섯 군데에 따로 적혀 있어서, 종류를 늘릴 때 한 곳만 빠뜨리면 내가 적어 둔 기준이 "오늘의 글귀"라며
알림에 뜨거나 읽음 횟수로 세어졌습니다. 서버는 `personalContentTypes`, 웹은 `personalTypes`로
같은 목록을 둡니다. **세 곳이 같아야 합니다.**

**원칙(`PRINCIPLE`)은 하지 않을 일의 반대편입니다.** 지키기로 한 기준을 적는 자리이고,
**수행 버튼이 없습니다.** 지킨 날을 세기 시작하면 원칙이 또 하나의 루틴이 되어, 못 지킨 날
목록만 쌓입니다. 하지 않을 일과 화면을 함께 씁니다(`RuleListScreen` + `RuleListLabels`) —
방향만 반대이고 하는 일이 같아서, 따로 두면 한쪽만 고쳐져 어느 날부터 두 탭이 다르게 동작합니다.
루틴 탭 안의 세 자리는 `RoutineTab`이 정합니다.

**보관함 종류(`ContentType`)는 앱·서버·웹이 같아야 합니다.** 앱 `ContentType` · 서버 `contentTypes` ·
웹 `app.js`의 종류 고르개와 이름표. 서버가 모르는 종류로 보면 글귀로 바꿔 버려, 담아 둔 것이
보관함에서 그 종류로 골라지지 않습니다. **`IDEA`는 담는 사람이 짚어 줍니다** — 주소로 종류를
알아내는 `ContentNormalizer.detectType`이 번뜩인 것은 알아볼 수 없습니다.

**담기 전에 다듬는 규칙은 `ContentNormalizer` 한 곳에만 둡니다.** 담는 길이 둘입니다 —
담는 화면(`MainViewModel.saveContent`)과 다른 앱에서 공유해 온 것(`ShareTargetActivity`).
한쪽에 규칙을 따로 두면 같은 유튜브 링크가 담는 길에 따라 영상이 되기도 하고 링크가 되기도 합니다.

**공유·텍스트 선택·붙여넣기로 가져온 글은 임시저장소(`INBOX`)에 먼저 담습니다.**
`ShareTargetActivity`가 SEND와 PROCESS_TEXT를 받고 `saveSharedContent`가 저장합니다.
제목과 주소를 나누는 규칙은 `SharedText`에 있습니다. 주소가 없으면 원문은 본문에 담습니다.
같은 임시저장 글은 중복을 막되, 같은 주소에서 가져온 서로 다른 문장은 각각 남깁니다.
저장은 `AppGoodWordsApplication.applicationScope`에 걸고, 저장 안내 토스트는
`Dispatchers.Main`에서 띄웁니다. 배경 스레드에서 Toast를 만들면 저장 직후 앱이 종료됩니다.
`documentLaunchMode="always"`를 유지합니다. 닫히는 담기 화면에 다음 공유가 합쳐지면 원문을 놓칩니다.
보내는 앱이 `SINGLE_TOP`을 붙여 기존 화면을 재사용할 수도 있어 `onNewIntent`에서도 저장해야 합니다.

**분류는 같은 기록의 종류만 바꿉니다.** `classifyInboxItem`이 메모(`MEMO`)·글귀 모음·아이디어로
옮깁니다. 새 레코드를 만들거나 원본을 지우면 첨부·덧메모·syncId가 끊깁니다. `updatedAt`은
기존 값보다 커야 오래된 기기의 사본이 분류를 되돌리지 않습니다. 메모는 아이디어와 별개입니다.
메모·임시저장소는 읽음으로 세지 않고, 글귀 알림과 AI 글귀 추천용 원문 목록에서도 제외합니다.
편집할 때도 그 종류를 유지합니다. 17→18은 테이블 변경 없이 동기화 종류를 늘리는 마이그레이션입니다.

**개념(`CONCEPT`)은 임시저장 원문과 AI 설명을 함께 둡니다.** AI를 부르기 전에는 기록을 바꾸지 않고,
답을 받은 뒤 `saveConceptExplanation`이 같은 트랜잭션에서 원문 항목의 종류를 바꾸고 설명을
`ContentMemoEntity`로 붙입니다. 실패했는데 임시저장 글부터 사라지는 순서로 바꾸지 마세요. AI 설명은
`ConceptExplanation`이 만드는 `AI 설명` 머리말로 구분합니다. 개념은 읽음·알림·위젯·지난해 글귀와
AI 추천 원문에서 제외하고, 개념 탭과 검색에서는 연결된 설명까지 보여 줍니다. 19→20은 테이블 변경 없이
종류를 늘리는 마이그레이션이며 형식 20에는 앱 1.3.0 이상을 연결합니다.
**개념에도 직접 적고 다시 물을 수 있습니다.** 이미 개념인 것에 다시 물으면 `saveConceptExplanation`이
앞서 받은 `AI 설명` 메모의 **같은 줄을 덮어씁니다** — 새 줄로 쌓으면 카드가 옛 답을 먼저 집고, 지우고
새로 넣으면 삭제 표식이 필요해집니다. 사용자가 직접 적은 메모는 건드리지 않습니다. 답은 짧게 받습니다 —
물음에 길이를 일러 두고, 그래도 길면 `parse`가 갈래마다 자릅니다(`SECTION_LIMIT`). 개념 카드는 훑어보는
자리라 길면 접히고, 접힌 설명은 다시 읽지 않습니다.

**보관함 탭의 차례는 `LibraryTab.ORDER`가 정합니다.** 번호(`QUOTE = 0` …)는 이름표일 뿐이라 차례로
쓰지 마세요. 검색이 번호로 탭을 짚어 오므로, 번호를 차례에 맞춰 바꾸면 찾아온 사람이 엉뚱한 탭에
떨어집니다. 글귀가 맨 앞, 임시저장소가 맨 뒤입니다. **임시저장소를 거치지 않고도 담을 수 있어야 합니다** —
메모·개념은 `openFreshAddForm(type)`으로 종류를 짚어 담는 화면을 엽니다(`add:MEMO` 길). 담는 화면은
그 종류를 지키고 종류 고르개를 감춥니다.

**하지 않을 일(`NOT_TO_DO`)은 루틴 옆 탭에서 봅니다.** 기존 `content_items`에 제목과 선택 메모를
저장하고, 추가·수정·삭제는 같은 기록의 `syncId`와 삭제 표식을 유지합니다. 루틴 수행이나 글귀 읽음,
알림·위젯·지난해 글귀·AI 추천 원문으로 세거나 보내지 않습니다. 검색은 별도 `SearchKind.NOT_TO_DO`로
찾아 루틴 화면의 해당 탭을 엽니다. 앱·서버·웹의 종류 이름을 맞추고, 18→19 마이그레이션은 테이블을
유지합니다. 형식 19 서버에는 이를 아는 앱 1.2.0 이상으로 연결합니다.

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
걸음을 늘리면 **여섯 군데**를 함께 늘립니다 — `DailyLoopCalculator.daysByStep` · `DayScoreCalculator`의
`digest`와 `trend` · 홈의 탭 이동(`AppGoodWordsApp`의 `onOpenStep`) · 걸음마다 다른 색(`stepColors`) ·
설정 화면(`DailyStep.entries`를 그대로 훑으므로 대개 저절로 됩니다). 셈하는 자리를 빠뜨리면 그 걸음은
카드에서 영영 안 밟힌 것으로 남고, 고른 사람의 연속 날수가 매일 끊깁니다.

**독서 걸음(`READING`)은 날짜별 기록을 봅니다(`BookReadingLogEntity`).** 책에는 마지막 쪽만 남아서
그것만으로는 언제 읽었는지 되짚을 수 없습니다 — 연속 날수와 점수는 저장하지 않고 매번 기록에서
다시 세기 때문에, 날짜가 남는 표가 따로 있어야 합니다. **하루에 책 하나당 한 줄**이고, 같은 날 또
적으면 마지막 쪽만 올립니다(앱 `AppRepository.recordReading`, 서버 `recordReading`). 진도를 올리는
자리가 앱·웹 둘이라 양쪽에서 남겨야 같은 날을 셉니다. 덤 점수는 쪽수가 아니라 **권**으로 셉니다 —
쪽수로 세면 백 쪽 읽은 날 하나로 덤이 꽉 차 다른 걸음이 점수에서 사라집니다. **책을 지워도 독서
기록은 남깁니다** — 읽은 것은 책과 별개로 이미 한 일이고, 지우면 지난 연속 날수가 조용히 달라집니다.

**독후감(`BookEntity.review`)은 책 수정 폼에 실려 오지 않습니다.** 따로 쓰는 긴 글이라
`BookReviewScreen`에서만 고칩니다. 그래서 `AppRepository.saveBook`은 **기존 독후감을 지켜야**
합니다(`startedAt`과 같은 이유) — 안 지키면 책 제목만 고쳐도 써 둔 글이 통째로 사라집니다.
서버 `saveBook`도 `{...existing, ...payload}`라 같은 규칙입니다.
독후감은 짧은 메모(`note`)와 자리를 나눕니다. 한 칸에 합치면 책 카드가 글 전체를 싣고 길어져
목록이 문서 더미가 됩니다. **책을 합칠 때 독후감은 견주지 않습니다**(기준은 제목·저자) —
견주면 같은 책이 두 벌 남습니다. 글 안에서 고른 문장은 `TextFieldValue.selection`으로 읽어
`extractQuoteFromBook`에 그대로 넘깁니다. 고른 곳이 없으면 뽑기 버튼을 잠급니다 —
글 전체가 보관함에 들어가면 되돌리기 번거롭습니다.

**하루 점수(`DayScore`)에 기분을 넣지 않습니다.** 슬픈 날이 낮은 점수가 되면 앱이 감정을
잘못한 일로 세는 셈이고, 힘든 날일수록 열기 싫어집니다. 기분은 점수 **곁에** 둡니다.
점수의 뼈대는 사용자가 고른 걸음(`DailyStep`)입니다 — 앱이 따로 정한 잣대로 매기면 자기가
고른 축과 점수가 어긋납니다. 걸음을 넘겨 더 한 것은 덤으로 세되 상한을 둡니다. 끝없이 오르면
개수 채우기가 됩니다. **점수도 저장하지 않고 기록에서 다시 셉니다** — 연속 날수와 같은
이유입니다. 걸음을 바꾸면 지난날 점수도 곧바로 새 기준이 됩니다.

**돌아보기에는 날짜가 둘입니다(`GrowthCalendar`).** **돌린 날**(`createdAt`)과 **다뤄진 날**
(`periodStart`~`periodEnd`)입니다. 지난 한 주를 오늘 돌아봤다면 오늘은 돌린 날이고 지난 이레는
다뤄진 날입니다. 한 칸에 섞어 칠하면 "언제 돌아봤나"와 "어디까지 들여다봤나"를 가릴 수 없어,
달력은 칸 색(다뤄진 날)과 점(돌린 날)을 따로 씁니다. **빈 날을 세는 것이 이 달력의 값어치입니다** —
돌아본 날을 세는 것보다 빠뜨린 구간이 보이는 것이 다음에 무엇을 돌아볼지 정하게 해 줍니다.

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

**CLI 공급자(`CLAUDE_CLI`·`CODEX_CLI`)는 서버가 돌립니다(`askCli`).** 열쇠 대신 서버 PC에 로그인해
둔 `claude`·`codex`를 쓰므로 앱은 직접 부를 길이 없고(`AiProvider.serverCli`), 서버 주소가 없으면
막습니다. **물음은 명령줄 인자로 넘기지 않습니다** — 인자는 `ps`로 보이고 따옴표·`%`·줄바꿈이
셸에서 깨집니다. 사용자 글은 표준 입력, 일러 주는 말은 임시 파일로 건네고, 인자로 나가는 사용자
값은 글자를 검사한 모델 이름뿐입니다(`aiModelPattern`). CLI는 **도구 없이**(`--tools ""` ·
`--disable shell_tool`) **빈 임시 폴더에서** **대화를 남기지 않고**(`--no-session-persistence` ·
`--ephemeral`) 돌립니다. 하나라도 풀면 기록 속 문장이 서버 파일을 읽게 하거나 일기가 CLI 세션
기록으로 디스크에 쌓입니다. Claude에 `--bare`를 쓰지 마세요 — OAuth 로그인을 읽지 않아 열쇠가
도로 필요해집니다. 빈 모델은 "CLI 기본"이라는 뜻이라 CLI 공급자에만 둡니다(`CLI_DEFAULT_MODEL`).

**AI에 못 붙어도 막다른 길이 아니어야 합니다.** 물음을 복사해 채팅창에 붙여넣고 받아 온 답을
도로 넣는 길이 있습니다(`GrowthPrompt.chatPrompt` · `GrowthFeedbackCoordinator.saveManualAnswer`).
미리 보기와 복사되는 글은 **같아야 합니다.** 달라지면 무엇이 밖으로 나가는지 확인할 방법이
없어집니다. 붙여넣은 답은 형식이 어긋나도 버리지 않고 통째로 `guide`에 담습니다.
사람이 옮겨 온 글을 "못 읽었다"며 되돌려 주면 그 자리에서 사라집니다.

**동기화 JSON 포맷을 바꾸면 앱 `AppDataJson`과 서버를 함께 바꾸고 `schemaVersion`을 올립니다.**

**자판이 올라오면 창을 줄입니다(`windowSoftInputMode="adjustResize"`).** 미는 방식에서는 화면
위쪽이 통째로 밀려 나가, 긴 글을 쓰는 동안 제목 줄과 저장 버튼이 화면 밖으로 사라집니다.
창이 줄어드니 화면에서 `imePadding()`을 또 붙이지 마세요 — 글 칸 아래에 쓰지 못하는 빈자리가 생깁니다.
**긴 글을 쓰는 화면의 버튼은 글 위에 둡니다.** 아래에 두면 자판에 가려, 한창 쓰는 중에는
정작 저장도 누를 수 없습니다.

**색·글자·모서리·움직임·진동은 한 곳에서만 정합니다.** `ui/theme`의 `Color`·`Type`·`Theme`·`Motion`과
`ui/screen/Feedback.kt`입니다. 화면마다 따로 정하면 같은 동작이 어떤 화면에서는 울리고 어떤 화면에서는
조용해, 그 어긋남이 "만들다 만 앱" 같은 느낌을 만듭니다. 진동은 무게 셋뿐입니다 —
`tap`(되돌리기 쉬운 것) · `commit`(기록이 남는 것) · `celebrate`(드물고 기쁜 것). **한 동작에 한 번**
울립니다. 누를 때와 결과가 돌아올 때 두 번 울리면 고장 난 것처럼 느껴집니다.
숫자는 오르는 것이 보람인 자리(점수·걸음)에만 `CountUpNumber`로 굴립니다. 목록 개수까지 굴리면
화면이 쉴 새 없이 움직여 어지럽습니다.

서명 정보(`keystore.properties`, `*.jks`)는 커밋하지 않습니다. `keystore.properties.example`을 참고하세요.

동기화 규칙과 서버 운용은 `server/README.md`에 자세히 적혀 있습니다.

어디까지 왔고 무엇이 남았는지는 `BACKLOG.md`에 있습니다. 미룬 것과 그 이유도 거기 적습니다.
이 파일은 지켜야 할 규칙만 담습니다 — 어겼을 때 조용히 망가지는 것들입니다.
