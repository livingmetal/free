# 월급 타이머

Kotlin / Jetpack Compose 기반 개인용 오프라인 급여 타이머입니다.

## v1.3.0 설치 수정
- 이전 테스트 APK의 서명 인증서가 매번 달라 덮어쓰기 설치가 불가능했습니다.
- 수정판 패키지는 `com.livingmetal.salarytimer.personal`입니다. 기존 앱 삭제 없이 별도로 설치할 수 있으나 설정은 다시 입력해야 합니다.
- 실제 APK 버전: versionName 1.3.0, versionCode 13.
- 이전 목업의 3D 돈주머니 원본 부분을 추출한 비트맵과 adaptive launcher icon을 사용합니다.
- 공개 저장소에는 연봉 설정이나 개인 서명키가 포함되지 않습니다. 앱 INTERNET 권한 없음.

## 서명 키 보존
서명키를 바꾸면 같은 패키지 업데이트가 거부됩니다. 개인 서명키를 Git에 올리지 마세요.
최초 1회 bootstrap에서는 저장소의 공개 인증서로 서명키 백업을 CMS 암호화합니다. 소유자만 갖고 있는 복호화 개인키가 필요합니다.
최초 배포 후 `signing/bootstrap-recipient.pem`을 제거하여 다음 빌드에서 키가 자동 재생성되지 않게 합니다.
자동 서명하려면 기존 PKCS12 키와 암호를 Actions secrets `SALARY_KEYSTORE_BASE64`, `SALARY_KEYSTORE_PASSWORD`로 설정합니다(별칭 salarytimer).
키가 없을 경우 CI는 명시적으로 UNSIGNED 빌드만 생성하며 설치용 APK로 배포하지 않습니다. 기존 개인키로 로컬 서명할 수도 있습니다.

## 검증
CI는 APK 서명/정렬 검사 및 Android 16 에뮬레이터 설치, 실행, 월간 화면 전환, 동일 서명 재설치 검사를 수행합니다.
에뮬레이터 검증은 삼성 Auto Blocker, Play Protect 또는 회사 관리 정책의 설치 승인을 보장하지 않습니다.

## 계산 한계
현재 오늘 화면은 연 260근무일 기준, 월간 화면은 해당 월 평일 수와 연봉/12 기준으로 환산합니다. 두 화면의 환산 기준이 다릅니다.
공휴일/연차/상여/세금/실제 급여명세를 반영하지 않는 참고용입니다. 월간 누적 구간은 매월 1일부터 말일까지이며, 급여일은 D-Day 표시용입니다.
