# AppGoodWords Server

DB 파일을 서버 프로세스에서 관리하고, Android 앱과 웹 UI가 같은 REST API를 쓰도록 하는 서버입니다. Node.js 표준 라이브러리만 사용합니다.

## 빠른 실행

```sh
cd server
node app_good_words_server.mjs --seed
```

웹 UI:

```text
http://127.0.0.1:8765
```

Windows에서 간단히 실행하려면 `server\run_server.cmd`를 열면 됩니다.

## 맥미니에서 실행

맥미니를 같은 Wi-Fi/LAN의 서버로 쓸 때는 모든 네트워크 인터페이스에서 받도록 실행합니다.

```sh
cd /path/to/AppGoodWords/server
chmod +x ./run_server.sh
APP_GOOD_WORDS_API_KEY="change-me" ./run_server.sh
```

맥미니의 LAN IP 확인:

```sh
ipconfig getifaddr en0
```

다른 기기에서 접속:

```text
http://<Mac mini LAN IP>:8765
```

Android 앱 설정 탭의 서버 주소도 같은 값으로 넣습니다.

```text
http://<Mac mini LAN IP>:8765
```

맥미니 부팅 시 자동 실행하려면 `launchd/com.appgoodwords.server.plist.example`의 경로와 API 키를 수정한 뒤 아래처럼 등록합니다.

```sh
cp launchd/com.appgoodwords.server.plist.example ~/Library/LaunchAgents/com.appgoodwords.server.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.appgoodwords.server.plist
launchctl enable gui/$(id -u)/com.appgoodwords.server
```

중지:

```sh
launchctl bootout gui/$(id -u) ~/Library/LaunchAgents/com.appgoodwords.server.plist
```

## LAN/Android 공유

Windows PC나 서버의 같은 포트를 다른 기기에서 접근하게 하려면 바인드 주소를 열어 실행합니다.

```powershell
.\run_server.ps1 -BindHost 0.0.0.0 -Port 8765 -Seed -ApiKey "change-me"
```

Android 앱 설정 탭의 서버 주소:

```text
Android Emulator: http://10.0.2.2:8765
Physical device:  http://<Mac mini 또는 PC LAN IP>:8765
```

`-ApiKey`를 사용한 경우 Android 앱과 웹 UI의 API 키 입력란에 같은 값을 넣습니다.

## 동기화 동작

Android 앱 설정 탭에 네 개의 버튼이 있습니다.

- `연결 테스트`: 주소와 API 키가 맞는지, 서버에 데이터가 얼마나 있는지 먼저 확인합니다.
- `서버와 병합`: 양쪽 변경을 항목 단위로 합칩니다. **여러 기기를 쓴다면 이 방식을 쓰세요.**
- `서버로 업로드`: 현재 Android 로컬 DB 전체를 서버 DB 파일로 교체합니다.
- `서버에서 가져오기`: 서버 DB 파일 전체를 Android 로컬 DB로 교체합니다.

### 병합 규칙

각 레코드에 기기와 무관한 `syncId`가 붙습니다. Room의 기본 키는 기기마다 따로 증가해서 A기기 id=5와 B기기 id=5가 서로 다른 항목이기 때문입니다.

- 같은 `syncId`가 양쪽에 있으면 `updatedAt`이 최신인 쪽이 남습니다. 같으면 서버 쪽을 유지합니다.
- 노출 이력과 루틴 체크는 바뀌지 않는 기록이라 합집합입니다.
- 삭제는 `deletions` 표식으로 전달됩니다. 표식이 레코드의 `updatedAt`보다 나중이면 삭제가 유지되고, 지운 뒤 다시 고쳤다면 그 수정이 이깁니다.
- 설정은 레코드가 아니라 한 덩어리라 `settingsUpdatedAt`이 최신인 쪽을 통째로 씁니다.

병합은 서버에서 수행합니다. 서버가 쓰기를 직렬화하므로 두 기기가 동시에 보내도 서로를 덮어쓰지 않습니다.

### 같은 내용 합치기

`syncId`로만 짝지으면 처음 서버를 붙일 때 같은 글귀가 두 벌이 됩니다. 앱과 서버가 각자 기본 글귀를
심는데 그 둘은 `syncId`가 다르기 때문입니다. 기기를 새로 깔고 붙여도 같습니다.

그래서 짝짓기가 끝난 뒤 **내용이 같은 것끼리 하나로 합칩니다.** 판단 기준은 종류마다 다릅니다.

- 글귀: 종류·제목·본문·저자·주소·첨부
- 루틴: 제목
- 일기: 날짜·제목·본문·첨부
- 할 일: 날짜·제목·메모

띄어쓰기와 대소문자만 다른 것도 같은 내용으로 봅니다. 사라진 쪽을 가리키던 이력·체크·메모는 남은 쪽으로
옮겨 붙습니다. 옮기지 않으면 이력이 부모를 잃습니다.

**이력과 체크 자체는 합치지 않습니다.** 같은 글귀를 두 번 본 것은 진짜로 두 번 본 것입니다.

같은 시각에 손댔으면 `syncId`가 큰 쪽을 남깁니다. 앱과 서버가 같은 규칙을 써야 어느 쪽에서 돌려도
결과가 같습니다. 다르면 두 기기가 병합할 때마다 서로를 고쳐 끝나지 않습니다.

**대가:** 일부러 똑같이 적어 둔 기록도 하나로 합쳐집니다. 같은 날 같은 제목의 할 일을 두 개 만들면
하나만 남습니다. 첨부가 다르면 남습니다.

### 서버 없이 두 기기 맞추기

서버 없이도 앱은 그대로 동작합니다. 서버 주소가 비어 있으면 네트워크를 아예 쓰지 않습니다.

두 기기를 맞추려면 설정 탭에서 `데이터 내보내기`로 만든 JSON을 다른 기기에서 `파일과 병합`으로 넣습니다.
서버의 `/api/sync`와 같은 규칙(앱 `SyncMerger`)으로 합칩니다. `데이터 가져오기 (교체)`와 다릅니다 —
교체는 기기 데이터를 파일로 통째로 바꿉니다.

서버가 없으므로 쓰기를 직렬화해 주는 것이 없습니다. 두 기기가 서로의 파일을 동시에 넣으면 각자
자기 쪽이 최신인 결과를 갖게 되고, 다음에 한 번 더 주고받아야 같아집니다.

`syncId` 없이 오는 구버전 앱 레코드는 서버가 식별자를 채워 이후 병합에 참여시킵니다.

### 형식이 다르면 주고받지 않습니다

병합·업로드·가져오기는 시작하기 전에 `/api/health`로 서버의 `schemaVersion`을 먼저 확인하고, 앱과 다르면
아무것도 보내지 않고 멈춥니다. 서버가 앞서 있어도 마찬가지입니다.

한쪽만 올려 두면 상대가 모르는 항목을 조용히 떨어뜨립니다. 예를 들어 9 이전 서버는 자식이 부모를 가리키는
`syncId`를 몰라서, 그 결과를 받아 저장하면 이력이 부모를 잃고 메모는 버려집니다. 오류가 나지 않으므로
사용자는 한참 뒤에야 알아차립니다.

**앱을 올렸으면 서버도 함께 올려야 합니다.** 확인이 요청보다 앞에 있어서 서버 데이터가 상하지는 않지만,
그때까지 동기화는 실패로 남습니다.

### 일기와 할 일

둘 다 수정 가능한 레코드라 `updatedAt`이 최신인 쪽이 남습니다. 할 일의 완료 표시도 같은 규칙이라,
한 기기에서 체크하면 다음 병합에서 다른 기기에도 반영됩니다.

**일기 첨부 파일은 동기화되지 않습니다.** 오가는 것은 URI 문자열뿐이고 사진·동영상·음성 파일 자체는
기기에 남습니다. 다른 기기에서는 그 첨부가 열리지 않고, 원본을 지우면 첨부한 기기에서도 열리지 않습니다.
글귀의 `imageUris`/`videoUris`도 예전부터 같은 방식입니다.

할 일 알람은 `remindAt`(시각)만 동기화합니다. 예약 자체는 기기의 AlarmManager에 있어서, 다른 기기로
넘어온 할 일은 그 기기가 병합을 마칠 때 새로 예약됩니다.

### 숫자 id 다시 매기기

병합 결과에는 서로 다른 기기에서 온 같은 숫자 id가 함께 들어옵니다. 그래서 저장 직전에 앱과 서버 양쪽이 id를 1부터 다시 부여합니다.

자식 레코드는 부모를 숫자 id가 아니라 `contentItemSyncId`(이벤트), `routineSyncId`(체크·메모)로 가리킵니다. 다시 매긴 뒤 이 값으로 부모를 찾아 잇습니다.

- 부모를 못 찾은 이력은 참조만 끊고 남깁니다. 항목을 지워도 이력은 남아야 하기 때문입니다.
- 메모는 루틴 화면 안에서만 보이므로, 붙을 루틴이 없으면 버립니다.

### 삭제 표식 정리

표식을 그냥 두면 끝없이 쌓입니다. 앱과 서버 모두 병합할 때 90일이 지난 표식을 지웁니다. 표식은 먼저 적용하고 나서 정리하므로, 늦게 도착한 표식도 한 번은 반영됩니다.

**90일보다 오래 꺼져 있던 기기를 다시 붙이면, 그 사이 지운 항목이 되살아날 수 있습니다.** 기간을 바꾸려면 앱의 `SyncCoordinator.DELETION_RETENTION_DAYS`와 서버의 `deletionRetentionDays`를 함께 바꿔야 합니다. 한쪽만 바꾸면 다른 쪽이 매번 되돌려 줍니다.

### 자동 동기화

설정 탭 `자동 동기화`를 켜면 배경에서 주기적으로 병합합니다. 1·6·24시간 중에 고를 수 있고 기본은 6시간입니다.

- 병합만 실행합니다. 업로드·가져오기처럼 한쪽을 통째로 지우는 동작은 배경에서 돌지 않습니다.
- 서버 주소를 넣어야 켤 수 있습니다.
- 네트워크가 연결된 동안에만 돕니다. 안드로이드가 배터리 상태에 따라 늦출 수 있어 정확히 그 시각에 돌지는 않습니다.
- 배경 동기화는 실패해도 화면에 뜨지 않으므로, 마지막 결과를 설정 탭에 남깁니다. 실패하면 사유가 함께 보입니다.

기기에서 확인하려면 예약이 실제로 걸렸는지부터 봅니다.

```sh
adb shell am broadcast -a "androidx.work.diagnostics.REQUEST_DIAGNOSTICS" -p com.codex.appgoodwords
adb logcat -d -s WM-DiagnosticsWrkr:*     # SyncWorker의 job id 확인
adb shell dumpsys jobscheduler | grep -A 14 "JOB #u0aNNN/<job id>:"
```

`Minimum latency`가 주기와 같고 `Required constraints`에 `CONNECTIVITY`가 있어야 합니다.

`cmd jobscheduler run -f`로는 앞당겨 돌릴 수 없습니다. WorkManager가 예정 시각 전에 깨어난 주기 작업을
실행하지 않고 다시 예약만 합니다(job id가 바뀌고 아무 일도 일어나지 않습니다). 실제로 돌려 보려면
기기 시계를 주기만큼 앞으로 돌려야 합니다.

```sh
adb root && adb shell settings put global auto_time 0
adb shell date -u -s "YYYY-MM-DD HH:MM:SS"   # 주기보다 뒤로
```

업로드와 가져오기는 실행 전에 확인 대화상자를 띄우고, 교체 직전 상태를 기기에 자동으로 백업합니다.

- 업로드 직전에는 서버 데이터를, 가져오기 직전에는 기기 데이터를 백업합니다.
- 백업은 설정 탭 `동기화 백업` 목록에서 바로 복원할 수 있습니다.
- 종류별로 최근 5개까지 보관합니다. 한 묶음으로 세면 자주 도는 자동 동기화가 직접 만든 백업을 밀어냅니다.
- 저장 위치: `Android/data/com.codex.appgoodwords/files/sync-backups` (설정 탭에 전체 경로가 표시됩니다)

`연결 테스트`는 `/api/health`가 API 키를 검사하지 않기 때문에 `/api/snapshot`까지 호출해 인증까지 함께 확인합니다.

## 주소 규칙

업로드는 개인 기록 전체를 한 번에 보내므로, 앱은 평문 `http://` 동기화를 같은 네트워크 주소로만 허용합니다.

- 허용: `10.x`, `172.16~31.x`, `192.168.x`, `127.x`, `169.254.x`, `localhost`, `*.local`
- 외부 주소로 보내려면 `https://`를 써야 합니다.

인터넷 너머로 동기화하려면 서버 앞에 리버스 프록시를 두고 TLS를 붙이는 방식을 권합니다.

한계로 남아 있는 것:

- 90일보다 오래 꺼져 있던 기기를 다시 붙이면 그 사이 지운 항목이 되살아날 수 있습니다.
- 자동 동기화는 안드로이드가 배터리 상태에 따라 늦출 수 있어 정확한 주기를 보장하지 않습니다.
  에뮬레이터에서 6시간 주기가 예정대로 도는 것까지는 확인했지만, 도즈 모드에 오래 들어간 실제 기기에서
  얼마나 밀리는지는 재 보지 않았습니다.
- 서버는 삭제 표식을 병합할 때만 정리합니다. 아무도 동기화하지 않으면 정리되지 않습니다.
- 노출 이력은 자동으로 줄지 않습니다. 동기화는 매번 스냅샷 전체를 보내므로 오래 쓸수록 payload가 커집니다.
  이력 화면에서 직접 지울 수는 있습니다.
- 일기 첨부 파일은 동기화되지 않습니다. 다른 기기에서는 첨부가 열리지 않습니다.
- 할 일 알람은 정확한 시각을 쓰지만, Android 12+에서는 사용자가 시스템 설정에서 '알람 및 리마인더'를
  켜 줘야 합니다. 켜지 않으면 앱이 대략적인 알람으로 대신 걸고 화면에 그 사실을 알립니다.

서버 DB 파일 기본 위치:

```text
server/app-good-words.db.json
```

## 테스트

서버 스모크 테스트는 Node 표준 테스트 러너로 돌립니다. 임시 디렉터리에 별도 DB를 만들어 실제 서버 프로세스를 띄우므로 `server/app-good-words.db.json`은 건드리지 않습니다.

```sh
node --test server/tests/server.test.mjs
```

Android 쪽 JSON 동기화 포맷과 병합 규칙 테스트:

```sh
./gradlew testDebugUnitTest
```

마이그레이션·저장·배경 동기화는 실제 기기가 필요합니다. 서버를 띄워 두면 앱↔서버 통신까지 함께 확인합니다(없으면 그 테스트만 건너뜁니다).

```sh
node server/app_good_words_server.mjs --host 0.0.0.0 --port 8765
./gradlew connectedDebugAndroidTest
```

## 주요 API

```text
GET  /api/health
GET  /api/snapshot
PUT  /api/snapshot
POST /api/sync
GET  /api/content
POST /api/content
PUT  /api/content/{id}
POST /api/content/{id}/toggle-confirm
POST /api/content/{id}/favorite
GET  /api/routines
POST /api/routines
PUT  /api/routines/{id}
POST /api/routines/{id}/check
POST /api/routines/{id}/memos
GET  /api/events
DELETE /api/events?ids=1,2,3
```
