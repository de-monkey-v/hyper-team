---
namespace: team
description: Send message to teammate or broadcast to all
argument-hint: <member-name|all> <message>
allowed-tools: Bash, Read, Glob, SendMessage
---

Parse the arguments to extract the target (first word) and message content (remaining text).

**Target Parsing:**
- If first argument is "all" → broadcast to entire team
- Otherwise → direct message to specific teammate

**Execution:**

1. Extract target from $ARGUMENTS (first word)
2. Extract message content (everything after first word)

3. If target is "all":
   - Call SendMessage tool with:
     - type: "broadcast"
     - content: {message content}
     - summary: First 50 characters of message

4. If target is a specific member name:
   - Call SendMessage tool with:
     - type: "message"
     - recipient: {target member name}
     - content: {message content}
     - summary: First 50 characters of message

5. Display confirmation of message sent

**Examples:**
- `/team:message implementer Phase 1 시작해주세요` → Direct message to implementer
- `/team:message all 모든 작업 중단하세요` → Broadcast to all members
