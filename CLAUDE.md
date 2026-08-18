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

## 규칙

**PowerShell로 소스 파일을 일괄 치환하지 마세요.** Windows PowerShell 5.1은 UTF-8 파일을 ANSI로 읽어서
`Get-Content ... | Set-Content`를 거치면 한글 주석과 UI 문구가 전부 깨집니다. 파일 수정은 Edit 도구로 합니다.

주석과 사용자에게 보이는 문구는 한국어로 씁니다.

**Room 스키마를 바꾸면 `Migration`과 `Migration{N}To{M}Test`를 함께 추가합니다.**
`AppContainer`에 `fallbackToDestructiveMigration()`이 걸려 있어서, 마이그레이션이 없거나 틀리면
사용자 DB가 오류 없이 통째로 지워집니다. 현재 버전은 13입니다.

**새 레코드 종류를 추가하면 여섯 군데를 함께 고칩니다.** 하나만 빠져도 조용히 어긋납니다.
`AppDataJson`(직렬화) · `SyncMerger`(병합) · `SyncDeduplicator`(같은 내용 합치기) ·
`SnapshotReindexer`(id 재부여) · `AppDataImporter`(저장) ·
서버의 `normalizeDb`/`mergeSnapshot`/`replaceSnapshot`/`reindex`/`deduplicate` ·
웹 `server/web`(탭·화면·`emptySnapshot`).
특히 서버 `replaceSnapshot`을 빠뜨리면 업로드가 그 종류만 남겨 두어, 사용자가 지운 레코드가
다음 병합에 되살아납니다.

**첨부 주소 형식은 앱·서버·웹 세 곳이 같아야 합니다.** `appgoodwords://attachment/{sha256}.{확장자}`이고,
앱 `AttachmentUris.SCHEME` · 서버 `attachmentScheme` · 웹 `app.js`의 `attachmentScheme`에 각각 있습니다.
파일은 DB JSON 밖 `attachments/` 폴더에 둡니다. DB에 넣으면 스냅샷마다 사진이 통째로 오갑니다.

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

**`SyncDeduplicator`(앱)와 서버 `deduplicate()`는 규칙이 같아야 합니다.** 판정 기준과 승자 선택
(최신 `updatedAt`, 같으면 큰 `syncId`)이 어긋나면 두 기기가 병합할 때마다 서로를 고쳐 끝나지 않습니다.

**글귀는 뽑아낸 책을 `bookSyncId`로 가리킵니다.** 숫자 id로 가리키면 다른 기기에서 엉뚱한 책이 됩니다.
같은 내용 합치기로 책이 하나로 줄면 사라진 책을 가리키던 글귀를 남은 책으로 옮겨 붙여야 합니다
(앱 `SyncDeduplicator`, 서버 `deduplicate`). 책을 지워도 뽑아 둔 글귀는 남깁니다.

**기기 간 식별자는 `syncId`뿐입니다.** Room의 숫자 id는 기기마다 따로 증가해서 A기기 id=5와 B기기 id=5가
서로 다른 레코드입니다. 자식은 부모를 `contentItemSyncId`(이벤트) 또는 `routineSyncId`(체크·메모)로
가리키고, 저장 직전에 `SnapshotReindexer`(앱)와 `reindex()`(서버)가 숫자 id를 다시 매깁니다.
병합 결과를 숫자 id 그대로 넣으면 서로를 덮어씁니다.

**삭제 표식 보관 기간은 양쪽이 같아야 합니다.** 앱 `SyncCoordinator.DELETION_RETENTION_DAYS`와
서버 `deletionRetentionDays`(현재 90일). 한쪽만 바꾸면 다른 쪽이 매번 되돌려 줍니다.

**서버는 바뀐 레코드에만 새 리비전 번호를 붙입니다.** 안 바뀐 것까지 번호가 오르면 증분 동기화가
매번 전부를 보냅니다. 그래서 (1) 저장 직전에 내용을 비교하고(`stampRevisions`), (2) 멀쩡한 숫자 id는
그대로 두고(`withStableIds`), (3) 지웠거나 합쳐서 사라진 레코드는 삭제 표식을 남깁니다.
부분 응답(`partial`)에 없는 레코드는 지워진 것이 아니라 안 바뀐 것이라, 앱은 `applyDelta`로 얹기만 합니다.

**동기화 JSON 포맷을 바꾸면 앱 `AppDataJson`과 서버를 함께 바꾸고 `schemaVersion`을 올립니다.**

서명 정보(`keystore.properties`, `*.jks`)는 커밋하지 않습니다. `keystore.properties.example`을 참고하세요.

동기화 규칙과 서버 운용은 `server/README.md`에 자세히 적혀 있습니다.
