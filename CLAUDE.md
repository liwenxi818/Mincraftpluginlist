# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This repository manages three PaperMC Minecraft servers running locally on Windows:

| Server | Directory | Port | Purpose |
|--------|-----------|------|---------|
| CS:GO 모드 서버 | `MinecraftServer_CSGO/` | 25565 | CS:GO 모드 플러그인 (`MinecraftCSGO-3.5.5.jar`) |
| OnlyUp 파쿠르 서버 | `MinecraftServer_OnlyUp/` | 25565 | 파쿠르 플러그인 (`ChainParkour-1.0.0.jar`) |
| 마피아 서버 | `MinecraftServer_Mafia/` | 25565 | 마피아 플러그인 (`MafiaPlugin.jar`) |

**세 서버가 같은 포트(25565)를 사용하므로 동시 실행 불가.** 동시 운영 시 한 쪽 포트 변경 필요.

## Starting Servers

```bat
# CS:GO 서버 시작
cd MinecraftServer_CSGO
start.bat

# OnlyUp 서버 시작
cd MinecraftServer_OnlyUp
start.bat

# 마피아 서버 시작
cd MinecraftServer_Mafia
start.bat
```

모든 서버는 Java로 실행되며 `-Xmx2G -Xms1G` 메모리 설정 사용.

## CS:GO Plugin Configuration

CS:GO 플러그인 설정 파일: `MinecraftServer_CSGO/plugins/MinecraftCSGO/config.yml`

주요 인게임 명령어로 설정하는 항목들:
- `/csgo setspawn` — 팀 스폰 위치 지정
- `/csgo setbombsite` — 폭탄 설치 구역 지정

설정 가능한 주요 값: 라운드 시간, 경제(시작금액/킬보상/최대소지금), 무기 가격/스탯, 폭탄 타이머, UI 스코어보드.

## Windows .bat 파일 작성 규칙

> **이 규칙을 반드시 지킬 것. bat 파일을 Write 툴로 직접 생성하면 LF + UTF-8로 저장되어 CMD가 오작동한다.**

### 문제
- `Write` 툴은 파일을 **LF 줄바꿈 + UTF-8** 인코딩으로 저장함
- Windows CMD는 **CRLF 줄바꿈 + ANSI(CP949)** 인코딩을 요구함
- LF bat 파일은 헤더 몇 줄 출력 후 **무음으로 멈추거나 명령어가 오파싱**됨
- UTF-8 한국어 주석이 있으면 CMD가 글자를 쪼개어 `'ho'`, `'cho'`, `'ally'` 등 엉뚱한 오류를 냄

### 해결책: PowerShell로 bat 파일 작성

bat 파일은 반드시 아래 패턴으로 PowerShell을 통해 CP949 + CRLF로 저장한다:

```powershell
powershell.exe -NoProfile -Command "
\$lines = @(
    '@echo off',
    'echo Hello',
    'pause'
)
\$crlf = \$lines -join \"\`r\`n\"
\$bytes = [System.Text.Encoding]::GetEncoding(949).GetBytes(\$crlf)
[System.IO.File]::WriteAllBytes('C:\path\to\file.bat', \$bytes)
"
```

### 추가 규칙
- bat 파일 본문에 **한국어 사용 금지** (주석 포함) — 순수 ASCII만 사용
- `>nul` 리다이렉션 사용 — `>/dev/null`은 Windows CMD에서 작동 안 함
- `set "VAR=값"` 구문에서 경로에 공백이 있으면 반드시 따옴표로 감쌈
- `%~dp0`으로 bat 파일 위치 기준 작업 디렉토리 고정: `cd /d "%~dp0"`

---

## GitHub

- 레포지토리: **https://github.com/liwenxi818/Mincraftpluginlist**
- GitHub CLI(`gh`)는 `liwenxi818` 계정으로 인증 완료 — 재로그인 불필요
- `gh` 실행 경로: `C:\Program Files\GitHub CLI\gh.exe` (bash에서는 `"/c/Program Files/GitHub CLI/gh.exe"`)

## Git Repository

`.gitignore` 정책:
- `.jar` 바이너리, `world/`, `logs/`, `cache/` 등은 제외
- `plugins/*.jar`은 제외하되 **`plugins/*/config.yml`은 추적**
- 런타임 생성 파일(`usercache.json`, `banned-*.json`, `ops.json` 등) 제외
- Maven 빌드 결과물 `target/` 제외
- Claude Code 내부 디렉토리 `.claude/` 제외

## Server Architecture

모든 서버는 **PaperMC** (Bukkit/Spigot 기반) 사용:
- `paper.jar` / `paper-1.20.4-499.jar` — 서버 엔진
- `plugins/` — 플러그인 `.jar` + 각 플러그인의 `config.yml`
- `world/`, `world_nether/`, `world_the_end/` — 월드 데이터

CS:GO 서버에는 `CommandBlockAsPlayer` 플러그인도 설치되어 있어 커맨드 블록이 플레이어 권한으로 명령 실행 가능.

---

## 마피아 플러그인

소스 파일은 `MafiaPlugin/` 에 작성 완료 — `mvn package` 로 빌드 후 `MinecraftServer_Mafia/plugins/` 에 jar 배치.

### 기본 정보
- 위치: `MafiaPlugin/` (레포 루트)
- 빌드: Maven, Java 17, Paper 1.20.4
- 명령어: `/mafia join|leave|start|end|status|vote`
- 테스트 명령어: `/mafia test` — 봇으로 4인 자동 채워 테스트 시작

### 게임 흐름
1. `/mafia start` → 플랫 월드(`mafia_world`) 자동 생성
2. 플레이어 수에 따라 역할 자동 배정 + 집 자동 건설 (9×9×6 오크 집) + 각자 집으로 스폰
3. **1일차 낮 전용**: 시장 선출 투표(30초) → GUI 최다득표자가 시장, 동점이면 무효 → **토론 없이 즉시 밤으로 전환**
4. **2일차 이후 낮 페이즈**: 2분 자유 토론 → 지목 GUI(30초) → 최후 변론(30초) → 처형 찬반 GUI(20초)
5. **밤 페이즈**: 전원 집으로 강제 소환 → 마피아(60초) → 의사(60초) → 경찰(60초) → 결과 처리
6. 게임 종료 시 월드 자동 삭제
- 각 페이즈마다 **action bar**에 남은 초 카운트다운 표시
- **스코어보드 사이드바**에 일차/페이즈/시장/생존자 수/사망자 수 실시간 표시

### 역할 구성 (인원별 자동 조정)
| 인원 | 마피아 | 경찰 | 의사 | 조커 | 시민 |
|------|--------|------|------|------|------|
| 4~5명 | 1 | 1 | - | - | 나머지 |
| 6~7명 | 2 | 1 | 1 | - | 나머지 |
| 8~10명 | 2 | 1 | 1 | 1 | 나머지 |
| 11명+ | 3 | 1 | 1 | 1 | 나머지 |

### 역할 능력
- **마피아**: 밤 GUI → 타겟 선택(또는 패스) → 처치. 마피아끼리 빨간 이름표 (scoreboard team)
- **경찰**: 밤 GUI → 타겟 선택(또는 패스) → 역할 귓속말로 알림 + 타겟 집 발자국 파티클
- **의사**: 밤 GUI → 타겟 선택(또는 패스) → 보호 (마피아 처치 무효)
- **조커**: 밤 행동 없음. 낮 투표로 처형당하면 단독 승리
- **시민**: 행동 없음

### 시장 (Mayor)
- 1일차 낮 시작 시 30초 GUI 투표로 선출
- 특별 능력 없음, 스코어보드에 `§6시장: [이름]` 표시
- 동점 또는 전원 기권 시 시장 없이 게임 진행
- `MafiaGame.mayorUUID` 에 저장, `getMayorUUID()` / `setMayorUUID()` 로 접근

### 투표 패스 기능
모든 GUI 투표에 **"패스 (기권)"** 회색 양털 버튼 존재:
- **지목 투표(NominationGUI)**: 패스 클릭 시 해당 플레이어 기권 (득표 집계 제외)
- **처형 찬반(ExecutionVoteGUI)**: 슬롯 4(중앙) 패스 클릭 시 기권
- **밤 행동(NightActionGUI)**: 마피아 패스 시 `PASS_UUID`로 투표 기록(자동 진행용), 의사/경찰 패스 시 즉시 다음 페이즈
- `MafiaGame.PASS_UUID = new UUID(0L, 0L)` 센티널 상수로 기권 표현

### 승리 조건
- 마피아: 생존 마피아 수 ≥ 생존 비마피아 수
- 시민팀: 마피아 전원 제거
- 조커: 낮 투표로 처형됨 (마피아 처치 X)

### 집 배치 방식 (HouseManager)
- 월드 스폰(0, 0) 중앙에 **캠프파이어** 1개 배치
- 4면(NORTH/SOUTH/EAST/WEST)에 인원 균등 분배: `base = count/4`, 나머지는 앞 면부터 +1
  - 예) 4명 → 각 면 1채, 5명 → [2,1,1,1], 6명 → [2,2,1,1], 8명 → 각 면 2채
- 각 면 내 여러 채는 좌우 간격 균등 분배 (`HOUSE_W + GAP = 13블록 단위`)
- 캠프파이어까지 거리 = `max(30, halfSpread + HOUSE_D + GAP)` — 인원이 많을수록 자동 증가
- 각 집의 **문·창문·침대·간판**이 캠프파이어 방향으로 자동 계산
  - NORTH 면 집 → 문 SOUTH, 창문 북벽, 침대 북벽 쪽
  - SOUTH 면 집 → 문 NORTH, 창문 남벽, 침대 남벽 쪽
  - EAST 면 집  → 문 WEST,  창문 서벽, 침대 서벽 쪽
  - WEST 면 집  → 문 EAST,  창문 동벽, 침대 동벽 쪽

### 기타
- `keepInventory = true`, 사망 시 관전 모드
- 밤 행동 후 타겟 집 입구에 발자국 파티클 (빨간 먼지 + FOOTSTEP)
- 집 스폰 높이는 `world.getHighestBlockYAt()`으로 지면 기준 자동 계산
- **배고픔 없음**: 게임 중 `FoodLevelChangeEvent` 취소 → 허기 감소 차단
- **자동 재생**: 게임 시작·매 낮 시작 시 `applyGameBuffs(p)` 호출
  - `setFoodLevel(20)` + `setSaturation(20)` + `REGENERATION` 포션 효과 (`Integer.MAX_VALUE/2` tick, 파티클 없음)

### HouseManager 블록 배치 규칙
BlockData 설정 시 반드시 `Material.XXX.createBlockData()` 패턴 사용 (`setType()` + `getBlockData()` 캐스팅 금지):
```java
// 올바른 패턴
Door data = (Door) Material.OAK_DOOR.createBlockData();
data.setFacing(BlockFace.SOUTH);
block.setBlockData(data);

// physics 트리거 방지가 필요한 경우 (Bed 등)
block.setBlockData(data, false);
```

### 파일 구조
```
MafiaPlugin/
├── pom.xml
└── src/main/
    ├── resources/plugin.yml
    └── java/com/mafia/
        ├── MafiaPlugin.java
        ├── command/MafiaCommand.java
        ├── game/GameState.java
        ├── game/MafiaGame.java
        ├── game/Role.java
        ├── gui/ExecutionVoteGUI.java
        ├── gui/MayorElectionGUI.java
        ├── gui/NightActionGUI.java
        ├── gui/NominationGUI.java
        ├── listener/MafiaListener.java
        ├── manager/GameManager.java
        ├── manager/HouseManager.java
        ├── manager/WorldManager.java
        └── util/ParticleUtil.java
```

### 알려진 미해결 사항 (Known Issues)

현재 미해결 이슈 없음.
