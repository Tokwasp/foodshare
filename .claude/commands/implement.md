---
description: 도메인 명세(docs.md)를 기반으로 테스트 작성 → 구현 → PR 보고를 순서대로 진행
argument-hint: [도메인]
---

`implement` 스킬(`.claude/skills/implement/SKILL.md`)을 사용해 아래 도메인 작업을 진행한다.

**도메인 파라미터:** `$1`

스킬에 정의된 3단계 흐름을 그대로 따른다.

1. **테스트** — `.claude/docs/$1/docs.md` 명세와 `.claude/skills/test/` 작성법을 보고
   각 레이어 테스트를 작성한 뒤, `.claude/docs/$1/test.md`로 보고하고 **멈춘다**(내 검토 대기).
2. **구현** — 내가 진행을 지시하면 `.claude/skills/3layer/` 가이드를 보고 레이어별로 구현하고,
   테스트가 모두 green이 될 때까지 반복한다.
3. **보고** — `.claude/skills/pr-template/SKILL.md` 형식으로 `.claude/docs/$1/result.md`를 작성한다.

> 시작 전 `.claude/docs/$1/docs.md`가 없으면, 먼저 명세부터 정리하자고 알려준다.