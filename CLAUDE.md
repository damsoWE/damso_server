# CLAUDE.md

## Project Overview

This document is a router. It does not hold the project overview, planning
context, or rules themselves — follow the references below.

Backend for DAMSO (담소), a parent–teacher communication app. Spring Boot 4.1,
Java 21, Gradle wrapper. Root package `damso`. The frontend lives in a
separate repository and consumes `docs/api/openapi.yaml`.

## Important Note for AI Agent

As the AI Agent, you must not perform any actions or make any changes to the
codebase that have not been explicitly instructed by the user. Adhere strictly
to the user's commands and avoid proactive modifications or additions.

## References

| Target | File |
|---|---|
| Security principles (immutable, override the planning doc) | `.specify/memory/constitution.md` |
| Planning doc (reference only, may change during development) | `docs/아기하마_개발기획서.pdf` |
| IA / User Flow | `docs/소개딩_IA.pdf` |
| Design (draft) | [Figma](https://www.figma.com/design/LAQp2ZzMBavuZjjtH1bXFH/?node-id=11-2) — reference by frame link (node-id) |
| API contract (source of truth) | `docs/api/openapi.yaml` |
| Deviations from the planning doc | `docs/decisions.md` |
| Security weakness checklist | `docs/security-checklist.md` |
| Feature specs | `specs/` |

## Skill Routing

| Step | Skill |
|---|---|
| Project principles | `/speckit-constitution` |
| Feature spec (what/why only, no tech stack) | `/speckit-specify` |
| Resolve ambiguity | `/speckit-clarify` |
| Tech plan + API contracts | `/speckit-plan` |
| Task breakdown | `/speckit-tasks` |
| Cross-artifact check | `/speckit-analyze` |
| Implementation | `/speckit-implement` |
| Issue · branch · commit · PR | `git-conventions` (`.claude/skills/git-conventions/SKILL.md`) |

Skill definitions: `.claude/skills/*/SKILL.md`.

## General Rules

- Always respond in Korean.
- Do not modify files unrelated to the user's requested changes.
- Confirm before decisions with large impact, such as new permissions,
  external systems, or DB schema changes.
- Do not delete files or run destructive Git commands without user approval.
- Only run git push or git commit when explicitly requested by the user.
- Follow `.claude/skills/git-conventions/SKILL.md` for every issue, branch
  (`features/…`, `fix/…`), commit (`Feat : …`, `Fix : …`, `Docs : …`, `Chore : …`), and PR. Never
  push to `main` directly.

## Verification

Run tests with `.\gradlew.bat test` (Windows) / `./gradlew test`. Report
failures clearly — do not hide or bypass them.
