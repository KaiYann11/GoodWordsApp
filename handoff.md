# AppGoodWords 작업 인수인계

최종 갱신: 2026-09-20 (Asia/Seoul)

## 현재 상태

- 작업 트리는 아직 커밋하지 않았다. `main`과 `origin/main`은 기존 커밋을 가리키며, 여러 세션에서 만든 UI/UX, 임시저장소·메모, 보안, 서버 설치, 하지 않을 일, 개념 기능 변경이 함께 들어 있다.
- `git reset`, `git clean`, 다른 체크아웃으로 현재 변경을 없애지 않는다.
- 앱 버전은 `1.5.0`, `versionCode = 8`, Room·동기화·서버 스키마는 모두 `21`이다(2026-09-23, 독서 기록 추가). (2026-09-20: 돌아보기를 서버의
  Claude CLI·Codex CLI로 돌리는 공급자를 더해 게시했다. 기록은 `captures/ai-cli/verification.md`.)
- PC 운영 서버는 `C:\Users\User\AppData\Local\AppGoodWordsServer`에 설치돼 있다.
- 서버 데이터는 사용자의 요청으로 비운 테스트 데이터이며 현재 모든 레코드가 0건이다. 기존 서버 API 키는 그대로 유지했다. 키 값은 저장소, 문서, 로그, 채팅에 기록하지 않는다.
- 실제 Galaxy S24는 현재 ADB에 연결돼 있지 않다. 기기의 데이터나 앱은 이번 작업에서 건드리지 않았다.

## 이번에 구현한 개념 기능

- 임시저장소 항목에 `AI에게 의미 묻기` 동작을 추가했다.
- AI에는 원문의 제목과 본문만 보내며, 의미·개념·문맥·예시를 설명하도록 요청한다. 원문 안의 명령은 데이터로 취급하고 따르지 않도록 시스템 프롬프트에 명시했다.
- AI 답을 먼저 정상 수신한 뒤 Room 트랜잭션에서 원본 항목의 종류를 `INBOX`에서 `CONCEPT`로 변경하고, 답변을 연결된 `content_memos` 레코드로 저장한다.
- AI 호출이나 응답 해석이 실패하면 원본은 임시저장소에 그대로 남는다.
- 신규 `개념` 탭에서 원문과 AI 답변 요약을 함께 볼 수 있고, 상세 화면에서는 답변 전체를 볼 수 있다.
- 검색은 원문과 연결된 AI 답변을 모두 찾는다.
- 개념 항목은 읽음·오늘 확인·알림/위젯 후보·오늘의 재노출·AI 글귀 추천 원문에서 제외된다.
- 웹 보관함도 `CONCEPT` 종류와 AI 답변 검색을 지원하며, 개념에는 오늘 확인과 루틴 만들기 동작을 표시하지 않는다.

주요 파일:

- `app/src/main/java/com/codex/appgoodwords/data/ConceptExplanation.kt`
- `app/src/main/java/com/codex/appgoodwords/data/AppRepository.kt`
- `app/src/main/java/com/codex/appgoodwords/ui/MainViewModel.kt`
- `app/src/main/java/com/codex/appgoodwords/ui/screen/TextCollectionScreen.kt`
- `app/src/main/java/com/codex/appgoodwords/ui/screen/LibraryTabsScreen.kt`
- `server/app_good_words_server.mjs`
- `server/web/app.js`

## AI 제공자 설정 제한

- 2026-09-20 확인 결과 PC의 프로세스/사용자/시스템 환경 변수에 `OPENAI_API_KEY`와 `ANTHROPIC_API_KEY`가 없고, 설치 서버의 `Start-Server.ps1`도 해당 키를 설정하지 않는다.
- 따라서 현재 폰 앱 설정에 직접 AI 제공자 키가 있으면 직접 호출할 수 있지만, `서버 경유` 방식은 서버에 제공자 키를 설정하기 전까지 답변을 만들 수 없다.
- 이 제한은 저장 안전성에 영향을 주지 않는다. 호출 실패 시 항목은 개념으로 이동하지 않는다.
- 2026-09-20부터 열쇠 없이도 된다: 앱에서 공급자를 `Claude CLI`·`Codex CLI`로 고르면 설치 서버가 PC에
  로그인해 둔 `claude`·`codex`를 돌린다(`askCli`). 설치 서버 로그에 두 CLI 경로가 찍히는 것을 확인했다.

## 빌드와 테스트

- JVM 단위 테스트: 417개 통과, 실패·오류·건너뜀 0.
- Node 서버/전송 테스트: 145개 통과, 실패·건너뜀 0.
- `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` 성공.
- 에뮬레이터에서 개념 저장, 스키마 17→18→19→20, 콘텐츠 회전, 임시저장소 화면 관련 계측 테스트 20개 통과.
- `node --check server/app_good_words_server.mjs`, `node --check server/web/app.js` 통과.
- `git -c core.safecrlf=false diff --check` 통과.
- 로그는 `captures/concept-ai/`에 있다.

## PC 서버와 업데이트 배포

- 로컬 주소: `http://127.0.0.1:8765`
- 휴대폰용 HTTPS 주소: `https://desktop-7k69krq.tail6ce501.ts.net`
- 업데이트 페이지: `https://desktop-7k69krq.tail6ce501.ts.net/update/`
- Tailscale Serve는 tailnet 전용 HTTPS에서 로컬 서버로 프록시한다.
- 2026-09-20 검증 결과:
  - 업데이트 페이지 HTTP 200, 응답 132ms
  - `/api/health` 스키마 20
  - `/api/snapshot` 기존 키 포함 200, 키 없음 401
  - 서버 레코드 모두 0건
  - 게시 APK와 로컬 빌드의 크기와 SHA-256 일치
- 게시 APK: `app-good-words-1.3.0-5-4e86a3b0.apk`
- 크기: `24,945,326` 바이트
- SHA-256: `4e86a3b0d3a62b54d232f37c691ccfd2414c8e112b74ec7f8f01e5cd9ed4ebb1`
- 배포 전 스냅샷과 서버 파일 백업은 설치 루트의 `backups\concept-ai-20260915-091513`에 있다.

2026-09-20에는 Tailscale가 `NoState`에 멈춰 HTTPS가 잠시 끊겼다. 관리자 권한 없이 `tailscale down` 후 `tailscale up`으로 정상 연결했고, 다음 명령으로 Serve를 복구했다.

```powershell
tailscale serve --bg --yes http://127.0.0.1:8765
```

서버는 시작 메뉴의 `AppGoodWords Server.lnk`로 로그인 시 자동 시작한다. 수동 시작 시 현재 셸 종료에 Node가 딸려 내려가지 않도록 시작 프로그램 바로가기를 실행하거나 WMI `Win32_Process.Create`로 `Start-Server.ps1`을 독립 실행한다.

## 휴대폰 설치와 사용

1. PC와 휴대폰에서 Tailscale을 켠다.
2. 휴대폰 브라우저에서 업데이트 페이지를 열어 1.3.0을 설치한다.
3. 기존 앱 위에 업데이트하며 앱 데이터 삭제나 제거를 하지 않는다.
4. 앱 설정의 서버 주소에 HTTPS 주소를 입력한다.
5. 서버 API 키는 PC 시작 메뉴의 서버 키 복사 항목 또는 설치 폴더의 `Copy-ApiKey.ps1`로 복사해 개인 채널로 휴대폰에 옮긴다.
6. 앱에서 연결 테스트를 실행한다.
7. 서버는 비어 있으므로, 휴대폰에 실제 데이터가 있으면 `서버에서 가져오기`를 누르지 않는다. 먼저 휴대폰 DB를 백업하고 `서버로 업로드`를 사용한다.

## 다음 세션 주의사항

- 실제 기기가 연결된 상태에서 대상 serial을 확인하지 않고 전체 `connectedDebugAndroidTest`를 실행하지 않는다. 테스트 APK 설치와 데이터 삭제가 실제 폰에 적용될 수 있다.
- 기기 앱 업데이트 전에 DB, `-wal`, `-shm`을 함께 백업한다.
- `fallbackToDestructiveMigration()`이 남아 있으므로 누락된 마이그레이션은 앱 데이터를 지울 수 있다.
- 서버와 폰 데이터가 다를 때 자동 병합을 가정하지 않는다. 먼저 양쪽 백업과 개수를 확인한다.
- API 키나 AI 제공자 키를 출력하거나 커밋하지 않는다.
- 운영 서버 데이터 초기화 스크립트는 사용자의 새롭고 명시적인 요청 없이 다시 실행하지 않는다.

## 빠른 확인 명령

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
.\gradlew.bat assembleRelease
node --test server/tests/server.test.mjs server/tests/transport.test.mjs
Invoke-RestMethod https://desktop-7k69krq.tail6ce501.ts.net/api/health
tailscale serve status
```
