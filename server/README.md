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

`syncId` 없이 오는 구버전 앱 레코드는 서버가 식별자를 채워 이후 병합에 참여시킵니다.

업로드와 가져오기는 실행 전에 확인 대화상자를 띄우고, 교체 직전 상태를 기기에 자동으로 백업합니다.

- 업로드 직전에는 서버 데이터를, 가져오기 직전에는 기기 데이터를 백업합니다.
- 백업은 설정 탭 `동기화 백업` 목록에서 바로 복원할 수 있고, 최근 10개까지 보관합니다.
- 저장 위치: `Android/data/com.codex.appgoodwords/files/sync-backups` (설정 탭에 전체 경로가 표시됩니다)

`연결 테스트`는 `/api/health`가 API 키를 검사하지 않기 때문에 `/api/snapshot`까지 호출해 인증까지 함께 확인합니다.

## 주소 규칙

업로드는 개인 기록 전체를 한 번에 보내므로, 앱은 평문 `http://` 동기화를 같은 네트워크 주소로만 허용합니다.

- 허용: `10.x`, `172.16~31.x`, `192.168.x`, `127.x`, `169.254.x`, `localhost`, `*.local`
- 외부 주소로 보내려면 `https://`를 써야 합니다.

인터넷 너머로 동기화하려면 서버 앞에 리버스 프록시를 두고 TLS를 붙이는 방식을 권합니다.

한계로 남아 있는 것: 병합은 사용자가 버튼을 눌러야 실행됩니다(자동 동기화 없음). 삭제 표식은 계속 쌓이며, 아직 자동으로 정리하지 않습니다.

서버 DB 파일 기본 위치:

```text
server/app-good-words.db.json
```

## 테스트

서버 스모크 테스트는 Node 표준 테스트 러너로 돌립니다. 임시 디렉터리에 별도 DB를 만들어 실제 서버 프로세스를 띄우므로 `server/app-good-words.db.json`은 건드리지 않습니다.

```sh
node --test server/tests/server.test.mjs
```

Android 쪽 JSON 동기화 포맷 테스트:

```sh
./gradlew testDebugUnitTest
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
