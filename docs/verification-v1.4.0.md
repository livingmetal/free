# v1.4.0 검증 완료

검증일: 2026-09-18.

## 배포 APK

- 파일: SalaryTimer-v1.4.0.apk (6,557,951 bytes)
- 패키지: com.livingmetal.salarytimer.personal
- versionName 1.4.0 / versionCode 14
- SHA-256: 7f0672ef7111624724656f9e10bac595c26e942d870527271a277e6203f9a189
- 서명 인증서 SHA-256: db0f4ce9e54b59070c34d9aa81767b5e228bc2661db05b5fdd975af97b60496c
- 기존 v1.3.0과 같은 서명키 및 패키지. 최종 다운로드용 APK와 CI 설치 검증 APK는 바이트 단위로 동일함을 확인함.

## 기능 및 계산 테스트

기능 소스 커밋: b8a01b206c852ee3c90a079f113e785837e1d2a1
CI 실행: 35352160697
테스트 산출물: 10550001728

계산 단위 테스트 18개: 실패 0, 오류 0, 건너뜀 0.
Android 16 기능 테스트 6개: OK (6 tests).

검증 범위: 기존 SharedPreferences 읽기, 다크 테마 저장 및 재실행, 주말 4.5시간/1.5배 기록, 보너스 입력, 달력/연간/오늘 화면 전환, 재실행 후 데이터 유지, 잘못된 시수 거부, 캘린더 미연결 상태 사용, 실제 Calendar Provider에 만든 테스트 일정 읽기.

## 최종 서명 APK 업데이트 테스트

CI 실행: 35353680534
서명 APK 검증 산출물: 10550354189 (SalaryTimer-v1.4.0-verified-APK)

Android 16 에뮬레이터에서 최종 배포용 release APK를 직접 검증함:

1. APK v2/v3 서명 검증: PASS.
2. zipalign -c -P 16 -v 4: Verification successful.
3. 기존 v1.3.0 release 설치 및 UI에서 설정 저장.
4. v1.4.0으로 adb install -r: Success.
5. MainActivity 실행: Status ok, COLD launch.
6. 새 앱 설정에서 이전 연봉 값 100000000 유지 확인. 이 금액은 테스트 기본값이며 실제 사용자 데이터를 읽은 것이 아님.
7. 다크 화면 전환 및 실제 release 실행 화면 저장.
8. 동일 최종 APK 재설치: Success.
9. 재실행 프로세스 확인, crash buffer 0 bytes.

## 지원 범위 및 한계

다크/라이트/시스템 테마, 근무 달력, 선택적 휴대폰 일정 읽기, 주말 추가 시수와 수당 배율, 보너스 지급/예정 구분, 연간 및 월별 내역을 구현함. 기존 3D 돈주머니 아이콘 유지.

READ_CALENDAR는 선택적으로 요청함. INTERNET 및 WRITE_CALENDAR 권한 없음. 외부 캘린더 일정 추가는 사용자가 캘린더 앱에서 확인 후 저장하며 급여 금액을 전달하지 않음. 읽어온 일정은 급여에 자동 반영하지 않음.

공휴일/연차는 직접 휴무로 지정해야 함. 연봉에는 별도로 입력한 보너스를 제외하여 중복 계산을 피해야 함. 주말 가산율은 자동 법정 계산이 아니라 사용자가 입력한 배율임. 현재 설정에 따른 참고용 환산액이며 실제 급여명세 데이터베이스가 아님.

삼성 실물 휴대폰, Play Protect, 자동 차단, 회사 관리 정책의 승인 여부를 검증한 것은 아님.
