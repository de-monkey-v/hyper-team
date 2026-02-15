# Inbox Protocol Reference

Complete specification for the message delivery system used by Agent Teams.

## Overview

The inbox system enables asynchronous message passing between team coordinator and members. Each member has a dedicated inbox file that acts as a message queue.

## Inbox File Location

```
~/.claude/teams/{team-name}/members/{member-name}/inbox
```

Example:
```
~/.claude/teams/team-alpha/members/backend-dev/inbox
```

## Message Format

Each message is a single line of JSON followed by a newline:

```
{"type":"message_type","from":"sender","to":"recipient","timestamp":"ISO-8601","content":{...}}\n
```

### Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Message type identifier |
| `from` | string | Sender identifier (usually "coordinator") |
| `to` | string | Recipient member name |
| `timestamp` | string | ISO 8601 datetime when message was sent |
| `content` | object | Message-specific payload |

### Standard Message Types

#### 1. task_assignment

Assign a task to a member:

```json
{
  "type": "task_assignment",
  "from": "coordinator",
  "to": "backend-dev",
  "timestamp": "2026-02-15T10:30:00Z",
  "content": {
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "subject": "Implement user authentication API",
    "priority": 4,
    "estimatedHours": 8
  }
}
```

#### 2. task_status_request

Request task progress update:

```json
{
  "type": "task_status_request",
  "from": "coordinator",
  "to": "backend-dev",
  "timestamp": "2026-02-15T11:00:00Z",
  "content": {
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }
}
```

#### 3. message

General communication message:

```json
{
  "type": "message",
  "from": "coordinator",
  "to": "backend-dev",
  "timestamp": "2026-02-15T11:15:00Z",
  "content": {
    "text": "Please prioritize the authentication task, frontend is blocked."
  }
}
```

#### 4. team_broadcast

Message to all team members:

```json
{
  "type": "team_broadcast",
  "from": "coordinator",
  "to": "all",
  "timestamp": "2026-02-15T09:00:00Z",
  "content": {
    "text": "Daily standup in 30 minutes",
    "recipients": ["backend-dev", "frontend-dev", "tester"]
  }
}
```

#### 5. idle_notification

Member reports being idle (sent from member to coordinator):

```json
{
  "type": "idle_notification",
  "from": "backend-dev",
  "to": "coordinator",
  "timestamp": "2026-02-15T15:30:00Z",
  "content": {
    "reason": "no_assigned_tasks",
    "lastTaskCompleted": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }
}
```

#### 6. task_completion

Member reports task completion:

```json
{
  "type": "task_completion",
  "from": "backend-dev",
  "to": "coordinator",
  "timestamp": "2026-02-15T16:00:00Z",
  "content": {
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "actualHours": 7.5,
    "notes": "Implemented with JWT, added rate limiting"
  }
}
```

#### 7. blocking_notification

Member reports being blocked:

```json
{
  "type": "blocking_notification",
  "from": "tester",
  "to": "coordinator",
  "timestamp": "2026-02-15T14:00:00Z",
  "content": {
    "taskId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "blockedBy": ["a1b2c3d4-e5f6-7890-abcd-ef1234567890"],
    "reason": "Cannot test authentication until implementation is complete"
  }
}
```

## Delivery Mechanism

### Writing Messages

**Atomic append operation:**

```bash
MESSAGE='{"type":"message","from":"coordinator","to":"backend-dev","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","content":{"text":"Hello"}}'

echo "$MESSAGE" >> ~/.claude/teams/team-alpha/members/backend-dev/inbox
```

**Why append-only:**
- Prevents race conditions
- Maintains message order
- Allows concurrent writes (filesystem handles serialization)
- Supports simple file-based monitoring

### Reading Messages

Members read the inbox file sequentially:

```bash
# Read all unprocessed messages
while IFS= read -r line; do
  echo "$line" | jq '.'
done < ~/.claude/teams/team-alpha/members/backend-dev/inbox
```

**Processing pattern:**

1. Read entire inbox file
2. Process each message line-by-line
3. Track last processed message (via separate state file or timestamp)
4. Ignore already-processed messages
5. Respond to new messages

### Inbox State Tracking

Members maintain a cursor to track processed messages:

```bash
# State file location
~/.claude/teams/{team}/members/{member}/state.json

# Example state
{
  "lastProcessedLine": 42,
  "lastProcessedTimestamp": "2026-02-15T16:00:00Z"
}
```

**Read only new messages:**

```bash
LAST_LINE=$(jq -r '.lastProcessedLine' state.json)
CURRENT_LINE=0

while IFS= read -r line; do
  CURRENT_LINE=$((CURRENT_LINE + 1))
  if [ $CURRENT_LINE -gt $LAST_LINE ]; then
    # Process new message
    echo "$line" | jq '.'
  fi
done < inbox

# Update state
jq --arg line "$CURRENT_LINE" '.lastProcessedLine = ($line | tonumber)' \
  state.json > state.json.tmp
mv state.json.tmp state.json
```

## Monitoring Inbox Activity

### Check for New Messages

**File modification time:**

```bash
stat -c '%Y' ~/.claude/teams/{team}/members/{member}/inbox
```

Compare with previous check - if changed, new messages arrived.

**File size:**

```bash
stat -c '%s' ~/.claude/teams/{team}/members/{member}/inbox
```

Growing size indicates active message delivery.

### Message Rate Analysis

**Count messages in time window:**

```bash
# Messages in last 5 minutes
CUTOFF=$(date -u -d '5 minutes ago' +%Y-%m-%dT%H:%M:%S)

jq -r --arg cutoff "$CUTOFF" \
  'select(.timestamp > $cutoff)' \
  ~/.claude/teams/{team}/members/{member}/inbox | \
  wc -l
```

### Extract Recent Messages

**Last N messages:**

```bash
tail -n 10 ~/.claude/teams/{team}/members/{member}/inbox | jq '.'
```

**Messages by type:**

```bash
jq -r 'select(.type == "task_assignment")' \
  ~/.claude/teams/{team}/members/{member}/inbox
```

**Messages in time range:**

```bash
START="2026-02-15T10:00:00Z"
END="2026-02-15T12:00:00Z"

jq -r --arg start "$START" --arg end "$END" \
  'select(.timestamp >= $start and .timestamp <= $end)' \
  ~/.claude/teams/{team}/members/{member}/inbox
```

## Inbox Maintenance

### Size Management

Large inbox files can slow down reading. Implement rotation:

**Check size:**

```bash
du -h ~/.claude/teams/{team}/members/{member}/inbox
```

**Rotate if over threshold (e.g., 10MB):**

```bash
INBOX=~/.claude/teams/{team}/members/{member}/inbox
SIZE=$(stat -c '%s' "$INBOX")

if [ $SIZE -gt 10485760 ]; then  # 10MB
  mv "$INBOX" "${INBOX}.$(date +%Y%m%d-%H%M%S)"
  touch "$INBOX"
fi
```

**Archive old messages:**

```bash
# Keep last 1000 messages, archive rest
INBOX=~/.claude/teams/{team}/members/{member}/inbox
ARCHIVE=~/.claude/teams/{team}/members/{member}/inbox-archive

head -n -1000 "$INBOX" >> "$ARCHIVE"
tail -n 1000 "$INBOX" > "${INBOX}.tmp"
mv "${INBOX}.tmp" "$INBOX"
```

### Clean Up Processed Messages

For very active teams, consider periodic cleanup:

```bash
# Delete messages older than 7 days
CUTOFF=$(date -u -d '7 days ago' +%Y-%m-%dT%H:%M:%S)

jq -r --arg cutoff "$CUTOFF" \
  'select(.timestamp > $cutoff)' \
  ~/.claude/teams/{team}/members/{member}/inbox \
  > inbox.tmp
mv inbox.tmp ~/.claude/teams/{team}/members/{member}/inbox
```

**Important:** Only clean up if you're certain messages have been processed. Consider archiving instead of deleting.

## Performance Considerations

### Read Optimization

**For large inboxes, use state tracking:**

```bash
# Don't re-read entire file every time
# Use lastProcessedLine to skip already-seen messages
```

**Index by timestamp:**

If frequent time-range queries are needed, create an index:

```bash
jq -r '{timestamp, line: input_line_number}' inbox > inbox-index.json
```

### Write Optimization

**Batch writes when possible:**

```bash
# Instead of multiple echo commands
{
  echo "$MESSAGE1"
  echo "$MESSAGE2"
  echo "$MESSAGE3"
} >> inbox
```

**Avoid frequent file stat calls:**

Cache inbox metadata and refresh periodically instead of checking every second.

## Error Handling

### Malformed JSON

Skip and log invalid messages:

```bash
while IFS= read -r line; do
  if ! echo "$line" | jq empty 2>/dev/null; then
    echo "Invalid JSON at line $(input_line_number): $line" >> errors.log
    continue
  fi

  # Process valid message
  echo "$line" | jq '.'
done < inbox
```

### Missing Inbox File

Create if not exists:

```bash
INBOX=~/.claude/teams/{team}/members/{member}/inbox

if [ ! -f "$INBOX" ]; then
  mkdir -p "$(dirname "$INBOX")"
  touch "$INBOX"
fi
```

### File Lock Contention

Use flock for exclusive access if needed:

```bash
{
  flock -x 200
  echo "$MESSAGE" >> inbox
} 200>inbox.lock
```

**Note:** Usually unnecessary due to append-only pattern and filesystem serialization.

## Security Considerations

### Message Validation

Validate message structure before processing:

```bash
# Check required fields
jq -e '.type and .from and .to and .timestamp and .content' message.json

# Validate sender authorization
EXPECTED_SENDER="coordinator"
ACTUAL_SENDER=$(jq -r '.from' message.json)

if [ "$ACTUAL_SENDER" != "$EXPECTED_SENDER" ]; then
  echo "Unauthorized sender: $ACTUAL_SENDER"
  exit 1
fi
```

### Content Sanitization

For messages containing user input or file paths:

```bash
# Escape special characters
CONTENT=$(jq -r '.content.text' message.json | sed 's/[";$`\\]/\\&/g')

# Validate file paths
FILE=$(jq -r '.content.filePath' message.json)
if [[ ! "$FILE" =~ ^/home/.*$ ]]; then
  echo "Invalid file path: $FILE"
  exit 1
fi
```

### File Permissions

Ensure inbox files are readable only by authorized users:

```bash
chmod 600 ~/.claude/teams/{team}/members/{member}/inbox
```

For team-wide access:

```bash
chgrp claude-team ~/.claude/teams/{team}/members/{member}/inbox
chmod 640 ~/.claude/teams/{team}/members/{member}/inbox
```

## Advanced Patterns

### Priority Messages

Use separate priority queue files:

```
inbox          # Normal priority
inbox-high     # High priority (read first)
inbox-low      # Low priority (read last)
```

**Read order:**

```bash
for queue in inbox-high inbox inbox-low; do
  process_messages "$queue"
done
```

### Message Acknowledgment

Require members to acknowledge receipt:

**Sender includes ackId:**

```json
{
  "type": "task_assignment",
  "ackId": "msg-12345",
  ...
}
```

**Recipient sends ack to coordinator's inbox:**

```json
{
  "type": "acknowledgment",
  "from": "backend-dev",
  "to": "coordinator",
  "timestamp": "2026-02-15T10:31:00Z",
  "content": {
    "ackId": "msg-12345"
  }
}
```

### Request-Response Pattern

**Request (coordinator → member):**

```json
{
  "type": "task_status_request",
  "from": "coordinator",
  "to": "backend-dev",
  "timestamp": "2026-02-15T11:00:00Z",
  "requestId": "req-98765",
  "content": {
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }
}
```

**Response (member → coordinator):**

```json
{
  "type": "task_status_response",
  "from": "backend-dev",
  "to": "coordinator",
  "timestamp": "2026-02-15T11:01:00Z",
  "requestId": "req-98765",
  "content": {
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "status": "in_progress",
    "progress": 60,
    "eta": "2026-02-15T14:00:00Z"
  }
}
```

### Broadcast with Targeting

Send message to multiple specific members:

```bash
for member in backend-dev frontend-dev tester; do
  MESSAGE='{"type":"message","from":"coordinator","to":"'$member'","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","content":{"text":"Meeting in 10 min"}}'
  echo "$MESSAGE" >> ~/.claude/teams/team-alpha/members/$member/inbox
done
```

## Debugging

### Trace Message Flow

**Log all sent messages:**

```bash
MESSAGE='...'
echo "$MESSAGE" >> inbox
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) SENT: $MESSAGE" >> message-log.txt
```

**Log all received messages:**

```bash
while IFS= read -r line; do
  echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) RECV: $line" >> message-log.txt
  process_message "$line"
done < inbox
```

### Verify Delivery

**Check message arrived:**

```bash
EXPECTED_CONTENT="Implement user authentication"
grep "$EXPECTED_CONTENT" ~/.claude/teams/{team}/members/{member}/inbox
```

**Check message was processed:**

```bash
LAST_PROCESSED=$(jq -r '.lastProcessedTimestamp' state.json)
MESSAGE_TIMESTAMP="2026-02-15T10:30:00Z"

if [[ "$MESSAGE_TIMESTAMP" < "$LAST_PROCESSED" ]]; then
  echo "Message was processed"
else
  echo "Message not yet processed"
fi
```

## Best Practices

1. **Keep messages small**: Large content should reference external files
2. **Use timestamps consistently**: Always UTC, always ISO 8601
3. **Validate on both sides**: Sender and recipient should validate messages
4. **Handle missing fields gracefully**: Provide defaults when possible
5. **Log errors, don't fail silently**: Track malformed or unexpected messages
6. **Rotate large inboxes**: Prevent performance degradation
7. **Use atomic operations**: Append is atomic, avoid read-modify-write
8. **Include context in messages**: Enough info to process without external lookups
9. **Version your protocol**: Plan for message format evolution
10. **Test error conditions**: Simulate file corruption, missing files, etc.
