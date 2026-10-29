# AppGoodWords Server

DB 파일을 서버 프로세스에서 관리하고, Android 앱과 웹 UI가 같은 REST API를 쓰도록 하는 서버입니다. Node.js 표준 라이브러리만 사용합니다.

이 문서는 **서버를 설치·운용하는 법과 동기화 규약**입니다. 앱을 쓰는 법은 [`../MANUAL.md`](../MANUAL.md),
코드를 고칠 때 지킬 것은 [`../AGENTS.md`](../AGENTS.md)에 있습니다.

## 빠른 실행

```sh
cd server
node app_good_words_server.mjs --seed
```

웹 UI:

```text
http://127.0.0.1:8765
```

탭은 홈 · 보관함 · 루틴 · 하지 않을 일 · 원칙 · 목표 · 할 일 · 일기 · 독서 · 기분 · 돌아보기 · 이력 · 서버입니다. 앱에서 하는 일은 웹에서도 됩니다.

Windows에서 간단히 실행하려면 `server\run_server.cmd`를 열면 됩니다.
macOS·리눅스에서는 `server/run_server.sh`입니다(아래 [macOS에서 실행](#macos에서-실행)).
서버 본체·웹 화면·게시 스크립트·시험은 모두 Node 표준 라이브러리만 쓰므로 양쪽에서 같이 돕니다.

## 윈도우에 설치한 서버

**지금 휴대폰이 쓰는 운영 서버는 맥미니입니다** — launchd가 저장소 폴더의 `server/`를 띄웁니다(아래
[macOS에서 실행](#macos에서-실행)). 아래는 윈도우에 따로 설치해 쓰던 방식이고, 그대로 쓸 수 있습니다.

윈도우 설치 위치는 `%LOCALAPPDATA%\AppGoodWordsServer`입니다. `app/`에 서버와 웹 화면,
`runtime/`에 Node, `data/`에 DB와 첨부, `logs/`에 실행 기록을 둡니다. 개발 저장소의 DB 원본은
유지하고 설치 직전 사본을 `data/backups/`에 남겼습니다.

Windows 로그인 시 시작프로그램의 `AppGoodWords Server` 바로가기가 서버를 숨김 실행합니다.
시작 메뉴의 **오늘의 글귀 서버**에서 서버 열기·시작·중지·접속 키 복사를 사용할 수 있습니다.
API 키는 설치 폴더의 `api-key.txt`에 있으며 저장소나 다운로드 페이지에는 포함하지 않습니다.

PC에서는 `http://127.0.0.1:8765/`로 접속할 수 있습니다. 휴대폰은 PC와 같은 Tailscale에 연결하고
`https://<PC의 Tailscale DNS 이름>/`으로 접속합니다. 앱의 서버 주소도 HTTPS 주소로 바꾸고 기존
접속 키를 사용합니다. **연결 테스트 → 서버와 병합**으로 확인합니다.
PC가 켜져 있고 Windows에 로그인되어 있어야 합니다.

Node 서버는 루프백에서만 HTTP를 받으며 휴대폰 연결은 Tailscale Serve의 HTTPS를 거칩니다.
LAN에 TCP 8765 포트를 열 필요가 없습니다. 설정 방법은 아래 HTTPS 연결 절을 참고하세요.

## 휴대폰에서 앱 업데이트

휴대폰 브라우저로 `https://<PC의 Tailscale DNS 이름>/update/`를 열면 최신 APK를 받을 수 있습니다.
서버 화면의 **앱 업데이트** 링크로도 이동합니다. 다운로드 뒤 Android 설치 화면에서 업데이트합니다.
설치 파일을 받는 페이지에는 접속 키가 필요하지 않으며, 개인 기록 API에는 기존 인증이 적용됩니다.

새 버전은 `app/build.gradle.kts`의 `versionCode`를 올리고 같은 서명으로 빌드한 다음 게시합니다.
프로젝트 루트에서:

```powershell
.\gradlew.bat assembleDebug
.\server\publish_update.ps1 -WebRoot "$env:LOCALAPPDATA\AppGoodWordsServer\app\web"
```

맥에서는 같은 일을 Node로 합니다. **게시 규칙은 `publish_update.mjs` 한 곳에 있고**,
위의 `publish_update.ps1`은 이 파일을 부르기만 합니다 — 두 벌로 적어 두면 한쪽만 고쳐졌을 때
운영체제에 따라 다른 `latest.json`이 나갑니다.

```sh
./gradlew assembleDebug
node server/publish_update.mjs --web-root ~/Library/Application\ Support/AppGoodWordsServer/web
```

저장소에서 직접 서버를 실행 중이면 `-WebRoot`/`--web-root`를 생략합니다. 게시 스크립트는 APK를 버전·해시가
포함된 이름으로 복사한 뒤 `latest.json`을 교체하므로 서버를 재시작할 필요가 없습니다.
페이지는 매번 최신 목록을 가져옵니다. 이전 파일은 이미 다운로드 중인 사용자를 위해 보존합니다.

현재 휴대폰 앱은 디버그 서명이므로 디버그 APK를 게시합니다. 서명이 다른 APK나 unsigned
릴리스를 게시하면 기존 앱에 업데이트할 수 없습니다. [Android 앱 서명 안내](https://developer.android.com/studio/publish/app-signing)
설치는 Android의 사용자 확인을 거칩니다. [웹사이트를 통한 배포 안내](https://developer.android.com/distribute/marketing-tools/alternative-distribution)

## macOS에서 실행

**서버는 Windows와 macOS 어느 쪽에서도 같은 일을 합니다.** 서버 본체는 Node 표준 라이브러리만
쓰고, 운영체제마다 다른 것은 띄우는 방법과 파일을 두는 자리뿐입니다. 맥에서도 Node는 루프백에
두고 휴대폰은 HTTPS 프록시(Tailscale Serve)로 연결합니다.

Node 18 이상이 필요합니다(`node:` 로 시작하는 표준 모듈과 `fetch`를 씁니다).

```sh
brew install node          # 이미 있으면 건너뜁니다
cd /path/to/AppGoodWords/server
chmod +x ./run_server.sh
APP_GOOD_WORDS_API_KEY="change-me" ./run_server.sh
```

**기록은 저장소 밖에 둡니다.** 체크아웃 안에 두면 `git clean` 한 번에 사라집니다.
맥에서 쓰는 자리는 `~/Library/Application Support/AppGoodWordsServer`입니다
(Windows 설치본의 `%LOCALAPPDATA%\AppGoodWordsServer\data`에 해당합니다).
첨부 파일은 DB 파일 옆 `attachments/`에 쌓입니다.

```sh
mkdir -p ~/Library/Application\ Support/AppGoodWordsServer
APP_GOOD_WORDS_DB=~/Library/Application\ Support/AppGoodWordsServer/app-good-words.db.json \
APP_GOOD_WORDS_API_KEY="$(cat ~/Library/Application\ Support/AppGoodWordsServer/api-key.txt)" \
  ./run_server.sh
```

맥과 휴대폰에 같은 Tailscale 계정을 연결한 뒤 아래 HTTPS 연결 절을 적용합니다.

로그인할 때 저절로 띄우려면 `launchd/com.appgoodwords.server.plist.example`의 경로·API 키·DB
자리를 고쳐 등록합니다.

```sh
cp launchd/com.appgoodwords.server.plist.example ~/Library/LaunchAgents/com.appgoodwords.server.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.appgoodwords.server.plist
launchctl enable gui/$(id -u)/com.appgoodwords.server
```

중지:

```sh
launchctl bootout gui/$(id -u) ~/Library/LaunchAgents/com.appgoodwords.server.plist
```

**launchd는 PATH를 거의 비워서 넘깁니다**(`/usr/bin:/bin:/usr/sbin:/sbin`). Homebrew나 nvm으로
깐 `node`·`claude`·`codex`가 그 안에 없어, 그냥 걸면 로그에 `node: command not found` 한 줄만
남습니다. plist 예시에 `PATH`를 넣어 두었고, `run_server.sh`도 흔히 깔리는 자리를 한 번 더
찾아봅니다. 그래도 못 찾으면 짚어 줍니다.

- `APP_GOOD_WORDS_NODE` — node 실행 파일 경로
- `APP_GOOD_WORDS_CLAUDE_CLI` · `APP_GOOD_WORDS_CODEX_CLI` — CLI 공급자를 쓸 때(아래 AI 돌아보기)
- `APP_GOOD_WORDS_OLLAMA_URL` · `APP_GOOD_WORDS_OLLAMA_MODEL` — Ollama가 기본 자리에 없거나 모델을
  정해 두고 싶을 때(아래 AI 돌아보기). Ollama는 HTTP로 부르므로 PATH와 상관없습니다

로그는 plist에 적은 `/tmp/appgoodwords-server.{out,err}.log`에 쌓입니다.
`node --test server/tests/*.test.mjs`로 돌리는 시험도 맥에서 그대로 돕니다.

## HTTPS 연결

PC·맥과 휴대폰을 같은 Tailscale에 연결합니다. 서버를 루프백에 실행한 뒤 Serve를 설정합니다.

```powershell
.\run_server.ps1 -BindHost 127.0.0.1 -Port 8765 -Seed
tailscale serve --bg --https=443 http://127.0.0.1:8765
```

맥에서도 같은 명령입니다. App Store 판 Tailscale에는 `tailscale` 명령이 PATH에 없어
앱 안의 것을 직접 부릅니다.

```sh
./run_server.sh
tailscale serve --bg --https=443 http://127.0.0.1:8765
# App Store 판이면:
/Applications/Tailscale.app/Contents/MacOS/Tailscale serve --bg --https=443 http://127.0.0.1:8765
```

API 키는 실행 전에 `APP_GOOD_WORDS_API_KEY`로 지정합니다. Serve가 계정의 HTTPS 활성화를
요구하면 안내한 Tailscale 페이지에서 활성화합니다. 성공 시 출력되는 HTTPS 주소를 앱과 웹에서
사용하며 기존 API 키 인증도 유지합니다. Tailscale Serve는 인증서 발급·갱신을 관리합니다.
[Tailscale Serve 문서](https://tailscale.com/docs/features/tailscale-serve)

Android 앱 설정 탭의 서버 주소 예시:

```text
https://<PC 또는 맥미니의 이름>.<tailnet>.ts.net
```

앱 1.1.1부터 사설 주소·루프백 주소도 HTTP 동기화는 차단합니다. HTTP 주소를 단순히 HTTPS로
고쳐 쓰는 것으로는 충분하지 않으며, 해당 주소에서 신뢰할 수 있는 인증서로 HTTPS를 제공해야 합니다.
인증서 검증을 끄거나 앱에 평문 예외를 추가하지 않습니다.

통합 테스트는 운영 DB와 분리한 서버를 HTTPS로 제공하고, 에뮬레이터 계측 실행에
`-e testServerUrl https://<테스트 서버> -e testServerApiKey <가짜 테스트 키>`를 전달합니다.
주소가 지정된 테스트 서버에 연결하지 못하면 테스트가 실패합니다. 운영 서버 주소를 넣지 마세요.

## 하지 않을 일

앱 1.2.0부터 **루틴 → 하지 않을 일**에서 반복하지 않기로 한 행동을 적고 볼 수 있습니다.
제목은 필수이고 메모는 선택입니다. 목록에서 수정·삭제할 수 있으며 검색·백업·서버 동기화에도
포함됩니다. 웹에서는 **루틴** 옆의 **하지 않을 일** 탭을 사용합니다.

기록은 `NOT_TO_DO` 종류로 저장하며 글귀 알림이나 읽음, 루틴 수행 횟수에 포함하지 않습니다.
하지 않을 일은 동기화 형식 19부터 지원합니다. 현재 서버가 요구하는 앱 버전과 형식은 아래 개념 설명을 따릅니다.

## 개념 설명

앱 1.3.0부터 임시저장소의 **AI에게 의미 묻기**로 원문을 설명받을 수 있습니다. 성공하면 원문 항목은
`CONCEPT`로 바뀌고 AI 답은 연결된 글귀 메모로 함께 저장됩니다. 실패하면 임시저장 원문은 그대로
남습니다. 개념은 읽음과 글귀 알림에 포함하지 않습니다.

AI 호출은 기존 돌아보기 설정과 같은 공급자·모델·키를 사용합니다. 서버 주소가 있으면
`/api/growth-feedback` 프록시를 사용하므로 서버에 `OPENAI_API_KEY` 또는 `ANTHROPIC_API_KEY`가 있어야
합니다. 서버에 키가 없고 휴대폰에 직접 호출용 키가 있으면 앱이 직접 호출합니다.
공급자를 Claude CLI·Codex CLI로 고르면 키 없이 서버 PC에 로그인해 둔 CLI가 답합니다(아래
[AI 돌아보기](#ai-돌아보기) 참고). 이 둘은 서버 주소가 없으면 부를 수 없습니다.

## 독서 기록

앱 1.5.0부터 **오늘의 걸음에 독서**를 고를 수 있습니다. 읽은 쪽을 남긴 날이 독서한 날입니다.
책에는 마지막 쪽만 남아 언제 읽었는지 되짚을 수 없어서, 날짜별 기록을 따로 둡니다(`bookReadingLogs`).

- **하루에 책 하나당 한 줄**입니다. 같은 날 또 적으면 마지막 쪽(`endPage`)만 오릅니다.
- 합치는 기준은 `bookSyncId|readDate`입니다(`readingLogFingerprint`). 쪽수는 견주지 않습니다 —
  두 기기가 같은 날 적은 쪽수는 서로 다르기 마련이라, 견주면 둘 다 남아 그날 읽은 쪽수가 겹쳐 세집니다.
- **웹에서 읽은 쪽을 올려도 남깁니다**(`saveBook` → `recordReading`). 제목만 고친 경우에는 남기지 않습니다.
- 책을 지워도 독서 기록은 남습니다. 읽은 것은 책과 별개로 이미 한 일이고, 지우면 지난 연속 날수가 달라집니다.
  그래서 `reindex`는 부모를 못 찾아도 버리지 않고 `bookId`만 0으로 끊습니다.

## 원칙

앱 1.6.0부터 **루틴 → 원칙**에서 지키기로 한 기준을 적을 수 있습니다. 하지 않을 일의 반대편이고,
`PRINCIPLE` 종류로 저장합니다. **수행 버튼이 없습니다** — 지킨 날을 세면 루틴이 하나 더 생길 뿐입니다.
읽음·알림·위젯·지난해 오늘·AI 추천 원문에서 모두 뺍니다(`personalContentTypes`).
웹에서는 **하지 않을 일** 옆의 **원칙** 탭을 씁니다.

## 목표 (이상향·목표·롤모델)

앱 1.13.0부터 **목표** 탭에서 되고 싶은 나(`VISION`)·이루고 싶은 것(`GOAL`)·닮고 싶은 사람(`ROLE_MODEL`)을 적습니다.
보관함 기록(`items`)의 종류로 저장하고, 내가 적은 것이라 읽음·알림·위젯에서 뺍니다(`personalContentTypes`).

- **되새긴 기록은 덧메모(`contentMemos`)입니다.** 지우면 그 기록의 메모도 한 편마다 삭제 표식을 남기고 지웁니다.
- **이어 둔 루틴은 `items[].linkedRoutineSyncIds`**(루틴의 `syncId` 목록)입니다. `normalizeContent`가 지키고, 같은 루틴
  둘이 하나로 합쳐지면 `deduplicate`가 남은 루틴으로 옮깁니다(앱 `SyncDeduplicator`와 같은 규칙). 웹의 수정은
  `{...existing, ...payload}`라 이 칸을 보내지 않아도 남습니다.
- 웹에서는 **목표** 탭에서 적고·고치고·지우고 되새길 수 있습니다. 루틴 잇기는 앱에서 합니다.

## 독후감

책마다 긴 글을 하나 둘 수 있습니다(`books[].review`). 짧은 메모(`note`)와 자리가 다릅니다.

- **독후감을 싣지 않은 수정은 써 둔 글을 지우지 않습니다.** `saveBook`이 기존 값을 먼저 깔고
  payload를 덮으므로, 앱의 책 수정 폼처럼 이 칸을 안 보내는 요청에도 글이 남습니다.
- 책을 합치는 기준은 제목·저자입니다. **독후감은 견주지 않습니다** — 견주면 같은 책이 두 벌 남습니다.
  나중에 고친 쪽(`updatedAt`)의 글이 남습니다.
- 웹에서는 책 수정 폼의 독후감 칸에서 문장을 끌어 고른 뒤 **고른 문장 글귀로**를 누르면
  `/api/books/{id}/quotes`로 들어갑니다. 앱은 독후감 화면에서 같은 일을 합니다.

동기화 형식은 24입니다(23에서 목표 종류와 `linkedRoutineSyncIds`가 들어왔고, 24에서 기분을 합치는 기준이 바뀌었습니다 —
아래 "오늘 기분"). 이 서버와 연결하는 기기는 모두 앱 1.15.0 이상으로 업데이트해야 합니다.

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
- 일기: 날짜·제목·본문·날씨·기분·종류·물음의 답·첨부
  (같은 날 쓴 감사 일기와 반성 일기는 서로 다른 기록이라, 종류와 답까지 봅니다.)
- 책: 제목·저자 (읽은 쪽수는 보지 않습니다. 기기마다 진도가 다른 것이 당연해서,
  쪽수까지 보면 같은 책이 두 벌 남습니다. 진도는 최신 `updatedAt`이 이깁니다.)
- 할 일: 날짜·제목·메모
- 기분: 날짜·기분·생성 시각·수정 시각이 모두 같은 사본만 (누를 때마다 한 기록이라서입니다 — 아래 "오늘 기분")

띄어쓰기와 대소문자만 다른 것도 같은 내용으로 봅니다. 사라진 쪽을 가리키던 이력·체크·메모와,
뽑아낸 것이 가리키는 출처(글귀의 `bookSyncId`, 루틴의 `sourceContentSyncId`)는 남은 쪽으로
옮겨 붙습니다. 옮기지 않으면 이력이 부모를 잃습니다.

**이력과 체크 자체는 합치지 않습니다.** 같은 글귀를 두 번 본 것은 진짜로 두 번 본 것입니다.

같은 시각에 손댔으면 `syncId`가 큰 쪽을 남깁니다. 앱과 서버가 같은 규칙을 써야 어느 쪽에서 돌려도
결과가 같습니다. 다르면 두 기기가 병합할 때마다 서로를 고쳐 끝나지 않습니다.

**대가:** 일부러 똑같이 적어 둔 기록도 하나로 합쳐집니다. 같은 날 같은 제목의 할 일을 두 개 만들면
하나만 남습니다. 첨부나 날씨·기분이 다르면 남습니다.

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

첨부는 [첨부 파일](#첨부-파일)을 보세요. 기기에서 붙인 파일은 다음 병합 때 서버로 올라갑니다.

### 독서

책도 수정 가능한 레코드라 최신 `updatedAt`이 남습니다. 읽은 쪽수가 그렇습니다.

책에서 뽑은 글귀는 보통 글귀와 같은 표에 들어가고, 어디서 왔는지를 `bookSyncId`(책)와 `bookPage`(쪽)로
들고 있습니다. **책은 숫자 id가 아니라 `syncId`로 가리킵니다.** 숫자 id는 기기마다 따로 증가해서
다른 기기로 넘어가면 엉뚱한 책을 가리킵니다.

**책을 지워도 그 책에서 뽑은 글귀는 남습니다.** 책장을 정리했다고 밑줄 그은 문장까지 사라지면 안 됩니다.
같은 내용 합치기로 책이 하나로 줄면, 사라진 책을 가리키던 글귀는 남은 책으로 옮겨 붙습니다.

### 글귀에 다는 메모와 글귀에서 뽑는 루틴

글귀 본문은 대개 남이 쓴 말이라 고칠 수 없습니다. 고치면 더는 그 사람의 말이 아닙니다. 그런데 같은
글귀라도 읽을 때마다 드는 생각이 다릅니다. 그래서 루틴처럼 옆에 붙여 쌓습니다(`contentMemos`).
부모는 `contentItemSyncId`로 가리킵니다. **붙을 글귀가 없는 메모는 버립니다.** 화면이 글귀 안에서만
그리므로 남겨도 볼 방법이 없습니다(이력은 예외로 남깁니다 — 제목만으로도 읽힙니다).

`POST /api/content/{id}/memos`로 답니다. **루틴 메모와 달리 체크를 남기지 않습니다.** 여기 적는 것은
실천이 아니라 생각이라, 체크를 남기면 하루의 실천 수가 한 일보다 부풀어 보입니다.
**글귀를 지우면 달아 둔 메모도 표식과 함께 지웁니다.** 표식을 빠뜨리면 주인 없는 메모가 다음 병합에
되살아납니다.

`POST /api/content/{id}/routines`는 그 글귀를 오늘부터 밟을 루틴으로 뽑아냅니다. 책에서 글귀를 뽑는
`POST /api/books/{id}/quotes`와 같은 자리입니다. 이름은 글귀의 첫 줄에서 뽑고(`title`로 직접 줄 수도
있습니다), **글귀 본문은 루틴 메모로 함께 옮깁니다.** 이름만 남기면 며칠 뒤에 왜 이걸 하기로 했는지
알 수 없습니다. 뽑아낸 글귀는 `sourceContentSyncId`로 가리키고, 카테고리는 `글귀에서`입니다.

**글귀를 지워도 뽑아낸 루틴은 남습니다.** 밟기로 한 것은 그 글귀와 별개로 이미 내 것입니다.

**할 일의 마감일(`dueDate`)은 비어 있을 수 있습니다.** 날짜를 정하지 않은 "언젠가" 할 일입니다.
빈 값이라고 버리면 안 됩니다. 기기에서 만든 것이 첫 업로드에 조용히 사라집니다.

할 일 알람은 `remindAt`(시각)만 동기화합니다. 예약 자체는 기기의 AlarmManager에 있어서, 다른 기기로
넘어온 할 일은 그 기기가 병합을 마칠 때 새로 예약됩니다.

일기의 날씨(`weather`)와 기분(`mood`)은 앱 `DiaryWeather`·`DiaryMood`의 이름을 그대로 담은 문자열입니다.
고르지 않았으면 빈 문자열입니다. **서버는 값을 검사하지 않습니다.** 앱이 선택지를 늘려도 서버를 함께
고칠 필요가 없고, 옛 앱이 모르는 값을 받아도 지우지 않고 그대로 넘겨줍니다. 여기서 모르는 값을 버리면
그 기기가 다음에 업로드할 때 다른 기기의 선택까지 함께 지워집니다.

일기의 종류(`kind`)도 같습니다. 앱 `DiaryKind`의 이름이고, 종류가 없던 시절의 일기는 `FREE`로 봅니다.
감사·반성 일기의 답은 `answers` 배열에 담고, **물음 순서에 자리를 맞춰 둡니다.**
그래서 가운데 빈칸을 버리면 안 됩니다. 버리면 뒤의 답이 다른 물음의 답이 되어 버립니다
(마지막 물음에만 답한 날 그 답이 첫 물음 자리로 올라옵니다). 뒤쪽 빈칸만 떼어 냅니다.
물음 자체는 서버에 저장하지 않습니다. 앱과 웹이 각자 `DiaryKind`·`diaryKinds`로 들고 있어서,
한쪽만 물음을 늘리거나 순서를 바꾸면 답이 엉뚱한 물음에 붙습니다.

### 바뀐 것만 주고받기

서버는 레코드가 바뀔 때마다 **리비전 번호**(`rev`)를 하나씩 올려 붙입니다. 기기는 마지막으로 본 번호를
적어 두었다가 다음에 `?since=`로 돌려주고, 서버는 그 뒤에 바뀐 것만 담아 보냅니다.

이력이 3,800건쯤 쌓인 상태에서 재 보면 **전체 805KB, 증분 2KB**입니다. 대부분은 지난번과 똑같은 내용이라
다시 실어 보낼 이유가 없습니다.

```text
POST /api/sync?since=120     # 120번 뒤에 바뀐 것만
GET  /api/snapshot?since=120
```

응답의 `partial`이 true면 **거기 없는 레코드는 지워진 것이 아니라 안 바뀐 것입니다.** 기기가 이때
DB를 통째로 갈아엎으면 담기지 않은 기록이 전부 사라집니다(앱은 `applyDelta`로 얹기만 합니다).

이 방식이 성립하려면 세 가지가 지켜져야 합니다.

- **안 바뀐 레코드는 번호가 오르면 안 됩니다.** 그래서 서버는 저장 직전에 내용을 비교해, 정말 달라진
  것에만 새 번호를 붙입니다(`stampRevisions`).
- **멀쩡한 숫자 id는 그대로 둡니다.** 예전에는 병합마다 1부터 다시 매겼는데, 그러면 한 건만 추가돼도
  뒤쪽 번호가 모두 밀려 전 레코드가 "바뀐 것"이 됩니다.
- **사라진 것은 삭제 표식으로 알립니다.** 부분 응답에는 남은 것만 오므로, 지웠거나 합쳐서 없어진
  레코드는 표식이 없으면 기기에 영영 남습니다. 같은 내용 합치기와 업로드 교체도 표식을 남깁니다.

기기가 들고 있는 번호가 서버의 현재 번호보다 크면(서버 DB를 새로 깐 경우) 전체를 보냅니다.
앱은 서버 주소를 바꾸거나 가져오기·복원으로 데이터를 통째로 바꾸면 이 번호를 지웁니다.

### 숫자 id 다시 매기기

병합 결과에는 서로 다른 기기에서 온 같은 숫자 id가 함께 들어옵니다. 그래서 저장 직전에 앱과 서버 양쪽이 id를 1부터 다시 부여합니다.

자식 레코드는 부모를 숫자 id가 아니라 `contentItemSyncId`(이벤트·글귀 메모), `routineSyncId`(체크·루틴 메모)로 가리킵니다. 다시 매긴 뒤 이 값으로 부모를 찾아 잇습니다.

- 부모를 못 찾은 이력은 참조만 끊고 남깁니다. 항목을 지워도 이력은 남아야 하기 때문입니다.
- 메모는 부모 화면 안에서만 보이므로, 붙을 루틴이나 글귀가 없으면 버립니다.
- 뽑아낸 출처(글귀의 `bookSyncId`, 루틴의 `sourceContentSyncId`)는 `syncId`라 번호를 다시 매겨도 그대로입니다.

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
POST /api/content/{id}/memos
POST /api/content/{id}/routines
DELETE /api/content-memos/{id}
GET  /api/routines
POST /api/routines
PUT  /api/routines/{id}
POST /api/routines/{id}/check
POST /api/routines/{id}/move
POST /api/routines/{id}/memos
GET  /api/diaries
POST /api/diaries
PUT  /api/diaries/{id}
DELETE /api/diaries/{id}
GET  /api/todos
POST /api/todos
PUT  /api/todos/{id}
DELETE /api/todos/{id}
POST /api/todos/{id}/toggle-done
GET  /api/books
POST /api/books
PUT  /api/books/{id}
DELETE /api/books/{id}
POST /api/books/{id}/quotes
GET  /api/events
DELETE /api/events?ids=1,2,3
```

### 루틴에는 하루에 밟는 차례가 있습니다

루틴은 할 일과 달리 순서가 있습니다(일어나서 물 → 스트레칭 → 책 한 쪽). 그 차례는 `orderIndex`에
담고, 작을수록 먼저입니다. 새로 만든 루틴은 맨 뒤에 붙고, 이름을 고쳐도 차례는 그대로입니다.

`POST /api/routines/{id}/move`로 옮깁니다.

```text
{"direction":"up"}      한 칸 앞으로
{"direction":"down"}    한 칸 뒤로
{"direction":"top"}     맨 위로
{"direction":"bottom"}  맨 아래로
{"position":3}          세 번째 자리로 (화면에 적힌 번호 그대로, 1부터)
```

`position`을 주면 `direction`은 보지 않습니다. 줄 밖을 가리키면 맨 위·맨 아래로 당겨 붙이므로,
개수를 세지 않고도 큰 수 하나로 "맨 아래"를 부를 수 있습니다.

늘어놓는 규칙과 옮기는 규칙은 앱 `RoutineOrder`, 서버 `sortedRoutines`·`moveRoutineTo`,
웹 `sortedRoutines` 세 곳이 같아야 합니다. 한쪽만 다르게 옮기면 두 기기가 병합할 때마다 서로의
차례를 고쳐 끝나지 않습니다. 옮긴 뒤에는 0부터 빈틈없이 번호를 다시 매기되, 차례가 실제로 달라진
루틴만 손댑니다. 안 바뀐 것까지 `updatedAt`을 올리면 증분 동기화가 매번 루틴 전부를 실어 나릅니다.

### AI 돌아보기

`POST /api/growth-feedback`은 기기 대신 AI에 물어봐 줍니다. 열쇠를 서버 한 곳에만 두려는
것입니다. 서버를 띄울 때 `OPENAI_API_KEY`나 `ANTHROPIC_API_KEY` 환경 변수를 주면 켜지고, 없으면
503과 함께 어느 열쇠가 없는지 돌려줍니다. 명령줄 인자로는 받지 않습니다. `ps`로 남에게 보이고
셸 기록에도 남기 때문입니다.

```text
POST /api/growth-feedback   {"system":"...","user":"...","model":"gpt-6-luna"}
           ->               {"text":"모델이 쓴 JSON 문자열"}
```

**어디에 물어볼지는 기기가 정합니다.** `provider`에 `openai`나 `anthropic`을 실어 보내고, 서버는
그에 맞는 열쇠를 골라 씁니다(`aiProviders`). 값이 없으면 OpenAI입니다. 이 값을 보내지 않던
옛 앱이 그대로 돌아가야 하기 때문입니다. 모르는 이름은 400으로 막습니다. 슬쩍 다른 곳으로
돌리면 사용자가 고르지 않은 데로 기록이 나갑니다.

```text
POST /api/growth-feedback   {"system":"...","user":"...","provider":"anthropic","model":"claude-opus-5-5"}
```

두 곳은 요청 모양이 다릅니다. OpenAI는 일러 주는 말을 `messages`에 함께 싣고 답을
`choices[0].message.content`에서 읽습니다. Claude는 `system`을 따로 싣고 `max_tokens`가 반드시
있어야 하며, 답은 `content` 배열에서 글 조각만 골라 이어야 합니다. 생각하는 시간은 따로 켜지
않습니다. 요즘 모델은 기본으로 켜져 있고 예전 방식(`budget_tokens`)을 보내면 400이 돌아옵니다.

#### 열쇠 대신 서버의 CLI로 답하기

`provider`에 `claude_cli`나 `codex_cli`를 보내면 열쇠 대신 **서버 PC에 깔려 로그인해 둔
Claude Code·Codex CLI**를 돌립니다(`askCli`). API 열쇠 없이 구독을 그대로 씁니다. 환경 변수는
필요 없습니다 — `claude`·`codex`가 PATH에 있으면 켜지고, 서버를 띄울 때 찾은 경로를 찍어 줍니다.
다른 곳에 깔았으면 `APP_GOOD_WORDS_CLAUDE_CLI`·`APP_GOOD_WORDS_CODEX_CLI`에 실행 파일 경로를
지정합니다. 못 찾으면 503과 함께 어디에 넣어야 하는지 돌려줍니다.

```text
POST /api/growth-feedback   {"system":"...","user":"...","provider":"claude_cli","model":""}
POST /api/growth-feedback   {"system":"...","user":"...","provider":"codex_cli","model":"gpt-6.1-sol"}
```

`model`을 비우면 `--model`을 붙이지 않아 CLI에 설정해 둔 기본 모델이 답합니다. 모델 이름은
CLI에 인자로 나가는 **유일한 사용자 값**이라 글자를 검사합니다(영숫자와 `._:-`만). 셸이 다르게
읽을 글자가 섞이면 400입니다.

CLI는 **도구 없이 글만 쓰게** 돌립니다. 기록 속 문장이 "파일을 읽어라"고 해도 읽을 도구가 없습니다.

- Claude: `claude -p --output-format json --safe-mode --tools "" --no-session-persistence
  --system-prompt-file <임시 파일>`. `--safe-mode`가 훅·플러그인·MCP·CLAUDE.md를 모두 끄고,
  로그인과 모델 선택은 그대로 둡니다. `--bare`는 쓰지 않습니다 — 그 모드는 OAuth 로그인을
  읽지 않아 열쇠가 다시 필요해집니다.
- Codex: `codex exec --ephemeral --skip-git-repo-check --ignore-user-config --disable shell_tool
  --disable unified_exec --sandbox read-only -o <임시 파일> -`. 일러 주는 말을 따로 싣는 자리가
  없어 물음 앞에 붙입니다. `--ignore-user-config`로 사용자 설정의 MCP 서버·훅을 읽지 않으므로
  그 안의 기본 모델도 무시됩니다. 대신 `model_reasoning_effort`를 medium으로 못 박습니다.

물음은 **명령줄 인자로 넘기지 않습니다.** 인자는 `ps`로 남에게 보이고, 따옴표·`%`·줄바꿈이
섞이면 셸이 다르게 읽습니다. 사용자 글은 표준 입력으로, 일러 주는 말은 빈 임시 폴더의 파일로
건넵니다. 작업 폴더도 그 임시 폴더라 서버 DB나 소스 옆에서 돌지 않고, 끝나면 지웁니다.
대화도 남기지 않습니다(`--no-session-persistence`·`--ephemeral`) — 일기가 CLI 세션 기록으로
서버 디스크에 쌓이면 안 됩니다. Windows에서는 `claude.cmd`처럼 확장자가 붙어 있어 서버가
PATH를 직접 뒤지고 `cmd.exe`를 거쳐 돌립니다(`resolveCli`·`runProcess`).

180초 안에 답이 없으면 프로세스를 나무째 끝내고 504를 돌려줍니다. 앱은 그보다 조금 더
기다려 그 사유를 받습니다. 시간은 `APP_GOOD_WORDS_AI_TIMEOUT_MS`로 바꿀 수 있는데, 늘리면
앱 `AiFeedbackClient`의 `readTimeout`(200초)도 함께 늘려야 사유가 앱까지 닿습니다.

CLI는 요청이 올 때마다 찾고 새로 돌리므로, `claude login`을 다시 하거나 CLI를 업데이트해도
서버를 다시 띄울 필요는 없습니다. 다만 서버는 뜰 때의 PATH를 쓰므로, CLI를 새 폴더에 깔아
PATH가 바뀌었으면 다시 띄워야 합니다. `claude`·`codex`는 서버가 도는 사용자 계정으로
로그인되어 있어야 합니다 — 이 PC의 설치된 서버는 시작프로그램으로 로그인한 사용자로 뜹니다.

#### 서버 PC의 Ollama로 답하기

`provider`에 `ollama`를 보내면 **서버 PC에서 도는 Ollama**에 묻습니다(`askOllama`). 열쇠도
구독도 없고, 기록이 그 PC 밖으로 나가지 않습니다. 환경 변수 없이도 Ollama의 기본 자리
(`http://127.0.0.1:11434`)를 봅니다. 서버를 띄울 때 Ollama가 닿으면 주소와 쓸 모델을 찍어 줍니다.

```text
POST /api/growth-feedback   {"system":"...","user":"...","provider":"ollama","model":""}
POST /api/growth-feedback   {"system":"...","user":"...","provider":"ollama","model":"qwen3.6:27b"}
```

- `APP_GOOD_WORDS_OLLAMA_URL` — Ollama가 다른 자리에 있을 때. **주소는 서버 설정에서만 읽습니다.**
  기기가 보낸 주소로 부르면 서버가 남이 시키는 아무 곳에나 요청을 대신 보내 주게 됩니다.
- `APP_GOOD_WORDS_OLLAMA_MODEL` — `model`을 비워 보냈을 때 쓸 모델. 없으면 받아 둔 모델 가운데
  **글을 쓸 수 있는 것 중 가장 최근에 받은 것**을 씁니다(임베딩 모델은 건너뜁니다). 앱은 서버 PC에
  무엇을 받아 두었는지 몰라 늘 비워 보냅니다. 요청마다 목록을 다시 보므로, 새 모델을 받거나
  Ollama를 나중에 띄워도 서버를 다시 띄울 필요가 없습니다. 이 값을 바꿨을 때만 다시 띄웁니다.

요청은 `/api/chat`에 `format: "json"`·`stream: false`·`think: false`로 보냅니다. 앱이 받는 답은 모두
JSON입니다. **생각 모드는 끕니다** — PC 한 대로 돌리면 생각하는 데만 몇 분을 씁니다. M4 맥미니
(32GB)에서 `qwen3.6:27b`로 한 주 돌아보기를 재어 보니 끄면 75초, 켜면 10분이 지나도 끝나지
않았습니다. 시간 제한은 CLI와 같은 180초(`APP_GOOD_WORDS_AI_TIMEOUT_MS`)이고, 넘기면 504와 함께
더 작은 모델을 쓰라고 알립니다. Ollama는 한 번에 한 요청만 받으므로, 같은 PC에서 `ollama run`으로
대화하는 중이면 그 뒤에 줄을 섭니다.

Ollama가 꺼져 있으면 503과 함께 어디를 봐야 하는지, 없는 모델이면 Ollama가 준 사유
(`model "..." not found`)를 502로 돌려줍니다.

**서버는 물음을 만들지 않습니다.** 무엇이 나갈지는 앱 `GrowthPrompt`가 정하고 서버는 전달만
합니다. 서버가 기록을 다시 읽어 물음을 짜면, 사용자가 앱에서 일기 본문은 빼라고 정해 둔 것이
서버 쪽에서 조용히 뒤집힙니다.

받아 둔 글은 `growthReports`로 동기화됩니다. 같은 기간을 두고 두 기기가 각각 받은 것은
`reportFingerprint`(단위·기간)로 한 편만 남깁니다. 본문까지 견주면 늘 다른 것이 되어 쌓이기만 합니다.

**오늘의 한마디는 서버에 오지 않습니다.** 같은 `/api/growth-feedback`을 쓰지만 받은 글은 기기에만
남고 자정이면 버립니다. 사용자가 집어 간 것(글귀·루틴·할 일)만 보통 레코드로 올라옵니다.
서버에서 찾지 마세요 — 없는 것이 맞습니다.

### 오늘 기분

`moodLogs`는 **한 번 누를 때마다 한 기록**입니다(사용자가 정함, 2026-10-29). 같은 날에도 다른 시각에 찍은 것은 모두 남습니다.
`moodLogFingerprint`는 날짜·기분·생성 시각·수정 시각이 모두 같은 사본만 합칩니다. 승자는 앱과 똑같이
`updatedAt`, 동률이면 `syncId`가 큰 쪽입니다. 합쳐진 사본에는 `MOOD_LOG` 삭제 표식을 남깁니다.
같은 내용 합치기는 `POST /api/sync`의 병합 안에서 일어납니다. `PUT /api/snapshot`은 입력한 기록을 그대로 교체합니다.

흐름의 시각은 `updatedAt`입니다. 예전 앱은 그날 기분을 고칠 때 이 값만 바꿨으므로 남아 있는 기분을 고른 때를 뜻합니다.
새 기록은 `createdAt`과 `updatedAt`을 같게 적습니다. 하루의 마지막 기록은 앱 `DayMood`가 고르고,
서로 다른 기분을 찍은 날은 기분별 실천 평균에서 뺍니다.

이 기준을 바꾸며 동기화 형식을 **24**로 올렸습니다. 날짜만으로 합치는 옛 앱이 흐름을 지우지 못하게 앱·서버를 함께 올립니다.
Room은 칸·인덱스가 그대로여서 **23**을 유지하며, 이번에는 마이그레이션이 없습니다.

기분 값은 검사하지 않습니다. 선택지는 앱 `DiaryMood`와 웹 `moodOptions`가 정하고 서버는
그대로 실어 나릅니다. 다만 날짜나 기분이 비면 버립니다. 어느 날의 무엇인지 알 수 없어
그래프에 놓을 수 없습니다.

웹에서는 날짜별로 묶고 하루 안에서는 시각순으로 읽기만 합니다. 기분은 그날 그때 찍는 것이라, 나중에 웹에서 고쳐 넣으면 그때
어땠는지가 아니라 지금 기억하는 것이 남습니다.

### 웹에서 고치고 지우는 것도 병합에 참여합니다

REST로 저장하면 `updatedAt`을 지금으로 올리고, 지우면 삭제 표식을 함께 남깁니다. 둘 중 하나라도
빠지면 기기가 다음 병합에서 옛 사본을 다시 올려 주어, 웹에서 한 일이 조용히 되돌아갑니다.

웹 UI에는 일기와 할 일 탭이 있고, 일기에는 사진·동영상·소리를 붙일 수 있습니다.

할 일 알람은 웹에서는 시각만 정합니다. 예약은 기기의 AlarmManager가 하므로, 그 기기가 병합을
마칠 때 실제로 걸립니다.

## 첨부 파일

파일은 DB JSON 밖, `attachments/` 폴더에 둡니다. DB 안에 넣으면 스냅샷을 주고받을 때마다 사진과
동영상이 통째로 오갑니다.

이름은 **내용 해시**입니다(`{sha256}.{확장자}`). 같은 파일을 여러 번 올려도 한 벌만 쌓이고, 한 번
올라간 주소는 내용이 바뀌지 않아 오래 캐시해도 됩니다. 기록이 가리키는 주소는
`appgoodwords://attachment/{sha256}.{확장자}`이고, 실제로 받을 때 `/api/attachments/{id}`로 바꿉니다.

```text
POST /api/attachments      본문에 파일 그대로, Content-Type에 형식
GET  /api/attachments/{id} 파일 내용
GET  /api/attachments      개수와 용량 (안 쓰는 첨부 수 포함)
```

**사진·동영상·소리만 받습니다.** 아무 파일이나 받으면 서버가 파일 창고가 되고, 브라우저에서 열리는
형식(SVG·HTML)은 스크립트를 품을 수 있습니다. 기본 상한은 100MB이고 `APP_GOOD_WORDS_MAX_UPLOAD`로
바꿉니다. 폴더 위치는 `--attachments`로 옮길 수 있습니다.

### 기기 첨부는 병합 직전에 올라갑니다

기기에서 붙인 첨부는 `content://...`이고 그 기기에서만 열립니다. 그래서 앱은 병합을 시작하기 전에
그 파일들을 서버로 올리고 주소를 서버 것으로 바꿉니다(`AttachmentUploader`). 이 단계가 없으면
스냅샷에 기기 주소만 담겨 다른 기기와 웹에서는 열 방법이 없습니다.

**한 파일이 실패해도 병합은 계속합니다.** 원본을 지웠거나 권한이 끊긴 첨부가 있을 수 있고,
사진 한 장 때문에 글까지 동기화되지 않으면 잃는 것이 더 큽니다. 실패한 주소는 기기 것으로 남습니다.

### 안 쓰는 첨부는 저절로 지워지지 않습니다

어느 기록도 가리키지 않는 파일이 생길 수 있습니다(붙였다가 저장하지 않은 경우 등). 서버는 그 개수와
용량만 알려 주고 지우지는 않습니다. **아직 서버에 붙지 않은 기기가 그 파일을 가리키는 기록을 들고
있을 수 있어서**, 안 쓰는 것처럼 보여도 실제로는 쓰이는 중일 수 있습니다. 용량이 문제가 되면 웹 "서버"
탭에서 숫자를 보고 직접 판단해 폴더에서 지우세요.
