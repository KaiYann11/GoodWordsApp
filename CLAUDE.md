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
사용자 DB가 오류 없이 통째로 지워집니다. 현재 버전은 11입니다.

**새 레코드 종류를 추가하면 여섯 군데를 함께 고칩니다.** 하나만 빠져도 조용히 어긋납니다.
`AppDataJson`(직렬화) · `SyncMerger`(병합) · `SyncDeduplicator`(같은 내용 합치기) ·
`SnapshotReindexer`(id 재부여) · `AppDataImporter`(저장) ·
서버의 `normalizeDb`/`mergeSnapshot`/`replaceSnapshot`/`reindex`/`deduplicate`.
특히 서버 `replaceSnapshot`을 빠뜨리면 업로드가 그 종류만 남겨 두어, 사용자가 지운 레코드가
다음 병합에 되살아납니다.

**`SyncDeduplicator`(앱)와 서버 `deduplicate()`는 규칙이 같아야 합니다.** 판정 기준과 승자 선택
(최신 `updatedAt`, 같으면 큰 `syncId`)이 어긋나면 두 기기가 병합할 때마다 서로를 고쳐 끝나지 않습니다.

**기기 간 식별자는 `syncId`뿐입니다.** Room의 숫자 id는 기기마다 따로 증가해서 A기기 id=5와 B기기 id=5가
서로 다른 레코드입니다. 자식은 부모를 `contentItemSyncId`(이벤트) 또는 `routineSyncId`(체크·메모)로
가리키고, 저장 직전에 `SnapshotReindexer`(앱)와 `reindex()`(서버)가 숫자 id를 다시 매깁니다.
병합 결과를 숫자 id 그대로 넣으면 서로를 덮어씁니다.

**삭제 표식 보관 기간은 양쪽이 같아야 합니다.** 앱 `SyncCoordinator.DELETION_RETENTION_DAYS`와
서버 `deletionRetentionDays`(현재 90일). 한쪽만 바꾸면 다른 쪽이 매번 되돌려 줍니다.

**동기화 JSON 포맷을 바꾸면 앱 `AppDataJson`과 서버를 함께 바꾸고 `schemaVersion`을 올립니다.**

서명 정보(`keystore.properties`, `*.jks`)는 커밋하지 않습니다. `keystore.properties.example`을 참고하세요.

동기화 규칙과 서버 운용은 `server/README.md`에 자세히 적혀 있습니다.
