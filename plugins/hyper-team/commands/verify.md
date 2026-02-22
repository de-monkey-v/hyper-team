---
name: verify
description: Verify implementation against a todo spec. Runs tests, analyzes code quality, checks git status, and provides a comprehensive score. Specify NNN-subject or just NNN.
allowed-tools: Bash, Read, Write, Edit, Glob, Grep, WebSearch, AskUserQuestion, Task, Skill, TeamCreate, TeamDelete, SendMessage, TaskCreate, TaskUpdate, TaskList, TaskGet
---

Create a team. Verify the implementation specified in a hyper-team todo file.

**Arguments:** `$ARGUMENTS` (NNN-subject or just NNN)

---

## Phase 1: Locate Todo File

<instructions>
Parse `$ARGUMENTS` to find the matching todo file.

If `$ARGUMENTS` is a 3-digit number (e.g., `001`):
</instructions>

```
Glob: .hyper-team/todos/{$ARGUMENTS}*
```

<instructions>
If `$ARGUMENTS` is a full name (e.g., `001-user-auth`):
</instructions>

```
Read: .hyper-team/todos/$ARGUMENTS.md
```

<instructions>
If the file is not found, also check for the completed variant:
</instructions>

```
Glob: .hyper-team/todos/{$ARGUMENTS}*-complete*
```

<instructions>
If no arguments provided, list available todos:
</instructions>

```
Bash: ls -la .hyper-team/todos/ 2>/dev/null
```

Then ask the user which one to verify.

If no todo files exist, inform the user:
```
No todo files found. Use `/hyper-team:make-prompt` to create one first.
```

### Spawn Mode

arguments에서 `--gpt` 옵션 확인:
- `--gpt` 포함 → GPT_MODE = true (spawn-teammate에 `--agent-type` 없이 호출)
- 기본값 → GPT_MODE = false (spawn-teammate에 `--agent-type` 지정)

---

## Phase 2: Parse Requirements

<instructions>
Read the todo file and extract:

1. All items from `<requirements>` section
2. All items from `<acceptance_criteria>` section
3. Files listed in `<implementation_plan>` (CREATE and MODIFY)
4. Items from `<side_effects>` to watch for

Store these in your context for team task creation.
</instructions>

---

## Phase 3: Create Team and Verify

<instructions>
Create a team with 3 teammates to perform verification in parallel.

```
TeamCreate tool:
- team_name: "verify-{todo-id}"
- description: "Verify {todo-id}: 구현 검증"
```

Spawn teammates using spawn-teammate Skill, then send task instructions via SendMessage.
</instructions>

### Teammate 1: tester

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "tester --team verify-{todo-id} --agent-type claude-team:tester"
  (GPT_MODE일 때: "tester --team verify-{todo-id}")

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "tester"
- content: |
    **검증 대상:** {todo-file-path}

    다음을 수행해주세요:
    1. spec 파일을 읽고 acceptance criteria를 파악하세요.
    2. 패키지 매니저를 감지하세요 (pnpm-lock.yaml → pnpm, yarn.lock → yarn, 기본 → npm).
    3. 빌드 실행: `$PM run build 2>&1; echo "BUILD_EXIT_CODE=$?"`
       - BUILD_EXIT_CODE != 0이면 CRITICAL 실패로 표시, 테스트 건너뛰기.
    4. 테스트 실행: `$PM test 2>&1; echo "TEST_EXIT_CODE=$?"`
    5. 각 acceptance criterion을 확인하세요.
    6. 리더에게 다음 형식으로 보고해주세요:
       ## Tester Report
       ### Exit Codes
       - BUILD_EXIT_CODE: {값}
       - TEST_EXIT_CODE: {값}
       ### Test Output (last 20 lines)
       ```
       {테스트 출력}
       ```
       ### Acceptance Criteria
       | # | Criterion | Result | Evidence |
       |---|-----------|--------|----------|
       ### Judgment
       - Build: PASS/FAIL
       - Tests: PASS/FAIL

    **중요:** EXIT_CODE != 0이면 반드시 FAIL 판정. 출력 내용과 무관하게 exit code 우선.
- summary: "tester 검증 작업 지시"
```

### Teammate 2: code-reviewer

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "code-reviewer --team verify-{todo-id} --agent-type claude-team:reviewer"
  (GPT_MODE일 때: "code-reviewer --team verify-{todo-id}")

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "code-reviewer"
- content: |
    **검증 대상:** {todo-file-path}

    다음을 수행해주세요:
    1. spec의 implementation plan에 나열된 모든 파일을 읽으세요.
    2. 정적 분석 실행:
       - Type check (tsconfig.json 있으면): `npx tsc --noEmit 2>&1; echo "TYPECHECK_EXIT_CODE=$?"`
       - Lint (lint 스크립트 있으면): `$PM run lint 2>&1; echo "LINT_EXIT_CODE=$?"`
    3. 코드 품질 평가 (각 0-20점):
       - Readability, Maintainability, Error Handling, Security, Performance
    4. 코드 스멜 확인:
       - 중복 코드, 긴 함수(>50줄), 깊은 중첩(>3단계), 매직 넘버/스트링
    5. 리더에게 다음 형식으로 보고해주세요:
       ## Code Review Report
       ### Static Analysis Results
       | Tool | Exit Code | Errors | Warnings |
       |------|-----------|--------|----------|
       | Type Check (tsc) | {값} | {수} | {수} |
       | Lint | {값} | {수} | {수} |
       ### Static Analysis Output (if errors found)
       ```
       {에러 출력}
       ```
       ### Code Quality Score
       | Criteria | Score | Notes |
       |----------|-------|-------|
       | Readability | /20 | |
       | Maintainability | /20 | |
       | Error Handling | /20 | |
       | Security | /20 | |
       | Performance | /20 | |
       | **Total** | **/100** | |
       ### Code Smells
       {목록}
- summary: "code-reviewer 코드 리뷰 지시"
```

### Teammate 3: integration-checker

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "integration-checker --team verify-{todo-id} --agent-type claude-team:architect"
  (GPT_MODE일 때: "integration-checker --team verify-{todo-id}")

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "integration-checker"
- content: |
    **검증 대상:** {todo-file-path}

    다음을 수행해주세요:
    1. git status와 git diff --stat으로 변경 사항을 확인하세요.
    2. spec의 implementation plan과 실제 변경 파일을 대조하세요 (CREATE/MODIFY 파일 존재 확인).
    3. spec에 없는 의도하지 않은 변경이 있는지 확인하세요.
    4. <side_effects> 섹션의 잠재적 부작용을 점검하세요.
    5. 기존 테스트가 깨지지 않았는지 확인하세요.
    6. 리더에게 결과를 보고해주세요.
- summary: "integration-checker 통합 검증 지시"
```

<instructions>
Wait for all 3 teammates to report their findings. Then proceed to Phase 4.

**팀메이트 결과 대기:**
- tester, code-reviewer, integration-checker의 보고를 모두 수신할 때까지 대기
- 각 보고에서 exit code, 점수, 이슈를 추출하여 Phase 4에서 사용
</instructions>

---

## Phase 4: Compile Final Report + Extract Issues

<instructions>
After all teammates complete their work, compile a final verification report:
</instructions>

<output_format>

## Verification Report: NNN-subject

### Evidence (검증 증거)

**Exit code 기반 판정** — teammate의 자연어 해석과 exit code가 불일치하면 **exit code를 우선**합니다.

| 항목 | Exit Code | 상태 |
|------|-----------|------|
| Build | {BUILD_EXIT_CODE} | PASS/FAIL/N/A |
| Test | {TEST_EXIT_CODE} | PASS/FAIL/N/A |
| Type Check | {TYPECHECK_EXIT_CODE} | PASS/FAIL/N/A |
| Lint | {LINT_EXIT_CODE} | PASS/FAIL/N/A |

#### 테스트 출력 (마지막 20줄)
```
{raw test output from tester report}
```

#### 빌드/타입체크/린트 에러 (해당 시)
```
{raw error output from tester and code-reviewer reports}
```

### Test Results
- Tests Passed: X/Y
- Acceptance Criteria Met: X/Y
- Server Check: PASS/FAIL/N/A

### Code Quality Score
| Criteria | Score | Notes |
|----------|-------|-------|
| Readability | /20 | {notes} |
| Maintainability | /20 | {notes} |
| Error Handling | /20 | {notes} |
| Security | /20 | {notes} |
| Performance | /20 | {notes} |
| **Total** | **/100** | |

### Static Analysis
| Tool | Exit Code | Errors | Warnings |
|------|-----------|--------|----------|
| Type Check (tsc) | {0/N/N/A} | {count} | {count} |
| Lint | {0/N/N/A} | {count} | {count} |

### Grade
- 90-100: A (Excellent)
- 80-89: B (Good)
- 70-79: C (Acceptable)
- 60-69: D (Needs Improvement)
- Below 60: F (Major Issues)

### Integration Status
- Files Changed: {expected vs actual}
- Unintended Changes: {list or "None"}
- Regressions: {list or "None"}

### Recommendations
1. {Recommendation 1}
2. {Recommendation 2}

</output_format>

<instructions>
**Exit code 기반 자동 판정 규칙 (필수):**
- exit code가 0이 아닌 항목이 하나라도 있으면 해당 항목은 FAIL
- teammate 메시지에서 "통과"라고 했더라도 exit code가 0이 아니면 FAIL로 판정
- exit code가 0인데 출력에 `FAIL`, `Error`, `failed`가 포함되면 WARNING 표시

After presenting the report, extract structured issues from each teammate's findings.

Categorize issues by source and severity:

**Test Issues** (from tester):
- CRITICAL: `BUILD_EXIT_CODE != 0` or `TEST_EXIT_CODE != 0`
- MAJOR: Acceptance criteria not met (even if tests pass)

**Code Quality Issues** (from code-reviewer):
- CRITICAL: `TYPECHECK_EXIT_CODE != 0` or score below 10/20 in any category
- MAJOR: `LINT_EXIT_CODE != 0` or score below 15/20 in any category
- MINOR: Code smells, style issues (lint warnings)

**Integration Issues** (from integration-checker):
- CRITICAL: Missing files from implementation plan, regressions detected
- MAJOR: Unintended file changes, potential side effects

Build an issue list with this structure for each issue:
- ID: Sequential number (e.g., #1, #2, ...)
- Source: tester / code-reviewer / integration-checker
- Severity: CRITICAL / MAJOR / MINOR
- Description: Clear description of the problem
- File: Affected file path (if applicable)
- Suggested Fix: Brief description of how to fix it

Store the initial score, exit codes, and issue list in context. Then clean up the team:
</instructions>

```
TeamDelete
```

<instructions>
If there are **zero issues**, skip directly to Phase 8.

If there are issues, proceed to Phase 5.
</instructions>

---

## Phase 5: Fix Decision Loop

<instructions>
Present the extracted issues to the user, grouped by severity:
</instructions>

<output_format>

## Issues Found

### CRITICAL
| # | Source | Description | File |
|---|--------|-------------|------|
| {id} | {source} | {description} | {file} |

### MAJOR
| # | Source | Description | File |
|---|--------|-------------|------|
| {id} | {source} | {description} | {file} |

### MINOR
| # | Source | Description | File |
|---|--------|-------------|------|
| {id} | {source} | {description} | {file} |

</output_format>

<instructions>
Ask the user what to do using `AskUserQuestion`:

- **Option 1: "모든 이슈 수정 (Recommended)"** — Fix all issues, proceed to Phase 6 with full list
- **Option 2: "Critical/Major만 수정"** — Fix only CRITICAL and MAJOR issues, proceed to Phase 6
- **Option 3: "현재 점수 수용"** — Accept current score, skip to Phase 8

If the user chooses to fix, proceed to Phase 6 with the selected issue list.
If the user accepts the current score, proceed to Phase 8.
</instructions>

---

## Phase 6: Apply Fixes

<instructions>
Fix the selected issues directly (no team needed). Follow this order for dependencies:
1. **Integration issues** first (missing files, structural problems)
2. **Code quality issues** next (code improvements)
3. **Test issues** last (functional fixes that depend on correct structure)

For each issue:
1. Read the affected file(s)
2. Analyze the issue and determine the fix
3. Apply the fix using `Edit` or `Write`
4. For test issues: run the specific failing test to verify
   ```
   Bash: {test command for the specific test}
   ```
5. For code issues: re-read the file to confirm the change looks correct
6. Report progress:
   ```
   Fixed [N/total]: {description of what was fixed}
   ```

If a fix cannot be applied automatically (e.g., requires architectural changes, external dependencies, or user decisions), report it as:
```
SKIPPED [N/total]: {reason why it cannot be auto-fixed}
```

Important:
- Do NOT create git commits. All changes stay in the working tree.
- Keep fixes minimal and focused. Do not refactor beyond what is needed to resolve the issue.
- If fixing one issue might affect another, note the dependency.
</instructions>

---

## Phase 7: Re-verify

<instructions>
Perform a lightweight re-verification without creating a team. Use the same exit code capture approach:

1. **Detect package manager** (same as Phase 3):
   ```
   Bash: if [ -f "pnpm-lock.yaml" ]; then PM="pnpm"; elif [ -f "yarn.lock" ]; then PM="yarn"; else PM="npm"; fi
   ```

2. **Re-run build** (if build issues were fixed):
   ```
   Bash: $PM run build 2>&1; echo "BUILD_EXIT_CODE=$?"
   ```

3. **Re-run tests** (if test issues were fixed):
   ```
   Bash: $PM test 2>&1; echo "TEST_EXIT_CODE=$?"
   ```

4. **Re-run type check** (if type issues were fixed and tsconfig.json exists):
   ```
   Bash: npx tsc --noEmit 2>&1; echo "TYPECHECK_EXIT_CODE=$?"
   ```

5. **Re-run lint** (if lint issues were fixed and lint script exists):
   ```
   Bash: $PM run lint 2>&1; echo "LINT_EXIT_CODE=$?"
   ```

6. **Re-evaluate code quality** on modified files only:
   - Read each file that was modified in Phase 6
   - Re-score using the same 5 criteria (Readability, Maintainability, Error Handling, Security, Performance)

7. **Check integration status**:
   ```
   Bash: git status
   Bash: git diff --stat
   ```

8. Present a comparison table with exit codes:
</instructions>

<output_format>

## Re-verification Results (Iteration {N})

### Exit Code Comparison
| 항목 | Before | After |
|------|--------|-------|
| Build Exit Code | {N} | {N} |
| Test Exit Code | {N} | {N} |
| Type Check Exit Code | {N/N/A} | {N/N/A} |
| Lint Exit Code | {N/N/A} | {N/N/A} |

### Metrics Comparison
| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Tests Passed | X/Y | X/Y | {+/-} |
| Acceptance Criteria | X/Y | X/Y | {+/-} |
| Code Quality | /100 | /100 | {+/-} |
| Issues Remaining | N | N | {-N} |

### Resolved Issues
- ✅ #{id}: {description}

### Remaining Issues
- ❌ #{id}: {description}

</output_format>

<instructions>
Determine next step based on results:

- **All issues resolved OR score >= 90 (Grade A):** Proceed to Phase 8
- **Issues remain AND iteration < 3:** Return to Phase 5 with updated issue list
- **Iteration >= 3 with issues remaining:** Ask the user using `AskUserQuestion`:
  - **Option 1: "현재 결과 수용 (Recommended)"** — Accept current state, proceed to Phase 8
  - **Option 2: "한 번 더 시도"** — Allow one more fix iteration (return to Phase 5)
  - **Option 3: "수동 리뷰로 전환"** — End with remaining issues listed for manual review, proceed to Phase 8
</instructions>

---

## Phase 8: Final Summary

<instructions>
Present the complete verification-fix summary:
</instructions>

<output_format>

## Final Summary: NNN-subject

### Score Progression
| Iteration | Score | Grade | Issues |
|-----------|-------|-------|--------|
| Initial | /100 | {grade} | {count} |
| Fix #{N} | /100 | {grade} | {count} |
| ... | ... | ... | ... |
| **Final** | **/100** | **{grade}** | **{count}** |

### Files Modified During Fixes
{list of files changed during Phase 6, or "None" if no fixes were applied}

### Remaining Issues
{list of unresolved issues, or "None — all issues resolved!"}

</output_format>

<instructions>
If the final grade is A (>= 90) and no issues remain, suggest marking the todo as complete:
```
Grade A achieved! Consider marking this todo as complete by renaming:
  .hyper-team/todos/NNN-subject.md → .hyper-team/todos/NNN-subject-complete.md
```

If fixes were applied but not committed, remind the user:
```
Note: All fixes have been applied to the working tree but NOT committed.
Review the changes with `git diff` and commit when ready.
```
</instructions>
