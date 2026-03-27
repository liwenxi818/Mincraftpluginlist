# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This repository manages two PaperMC Minecraft servers running locally on Windows:

| Server | Directory | Port | Purpose |
|--------|-----------|------|---------|
| CS:GO 모드 서버 | `MinecraftServer_CSGO/` | 25565 | CS:GO 모드 플러그인 (`MinecraftCSGO-3.5.5.jar`) |
| OnlyUp 파쿠르 서버 | `MinecraftServer_OnlyUp/` | 25565 | 파쿠르 플러그인 (`ChainParkour-1.0.0.jar`) |

**두 서버가 같은 포트(25565)를 사용하므로 동시 실행 불가.** 동시 운영 시 한 쪽 포트 변경 필요.

## Starting Servers

```bat
# CS:GO 서버 시작
cd MinecraftServer_CSGO
start.bat

# OnlyUp 서버 시작
cd MinecraftServer_OnlyUp
start.bat
```

두 서버 모두 Java로 실행되며 `-Xmx2G -Xms1G` 메모리 설정 사용.

## CS:GO Plugin Configuration

CS:GO 플러그인 설정 파일: `MinecraftServer_CSGO/plugins/MinecraftCSGO/config.yml`

주요 인게임 명령어로 설정하는 항목들:
- `/csgo setspawn` — 팀 스폰 위치 지정
- `/csgo setbombsite` — 폭탄 설치 구역 지정

설정 가능한 주요 값: 라운드 시간, 경제(시작금액/킬보상/최대소지금), 무기 가격/스탯, 폭탄 타이머, UI 스코어보드.

## Git Repository (minecraft_plugin/)

`MinecraftServer_CSGO/minecraft_plugin/`은 별도의 Git 저장소로, CS:GO 서버 설정과 플러그인 config만 추적한다.

`.gitignore` 정책:
- `.jar` 바이너리, `world/`, `logs/`, `cache/` 등은 제외
- `plugins/*.jar`은 제외하되 **`plugins/*/config.yml`은 추적**
- 런타임 생성 파일(`usercache.json`, `banned-*.json` 등) 제외

## Server Architecture

두 서버 모두 **PaperMC** (Bukkit/Spigot 기반) 사용:
- `paper.jar` / `paper-1.20.4-499.jar` — 서버 엔진
- `plugins/` — 플러그인 `.jar` + 각 플러그인의 `config.yml`
- `world/`, `world_nether/`, `world_the_end/` — 월드 데이터

CS:GO 서버에는 `CommandBlockAsPlayer` 플러그인도 설치되어 있어 커맨드 블록이 플레이어 권한으로 명령 실행 가능.
