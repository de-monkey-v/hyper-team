# Advanced Workflow Patterns

Complex team coordination patterns and best practices for the Agent Teams API.

## Pattern 1: Parallel Task Execution

Execute multiple independent tasks simultaneously using multiple teammates.

### Use Case
Analyze different data sources in parallel to reduce overall completion time.

### Implementation

```javascript
// 1. Create team
TeamCreate({
  team_name: "parallel-analysis",
  description: "Parallel data analysis team"
});

// 2. Spawn multiple analysts
const tasks = [
  { name: "sales-analyst", data: "/data/sales.csv", focus: "revenue trends" },
  { name: "marketing-analyst", data: "/data/marketing.csv", focus: "campaign ROI" },
  { name: "support-analyst", data: "/data/support.csv", focus: "ticket patterns" }
];

tasks.forEach(task => {
  Task({
    team_name: "parallel-analysis",
    name: task.name,
    model: "haiku",
    prompt: `You are a data analyst specializing in ${task.focus}. Analyze ${task.data} and provide key insights.`,
    subagent_type: "general-purpose",
    run_in_background: true
  });
});

// 3. Assign work to each analyst
tasks.forEach(task => {
  SendMessage({
    type: "message",
    team_name: "parallel-analysis",
    recipient: task.name,
    content: `Analyze ${task.data} focusing on ${task.focus}. Provide:
1. Top 3 key findings
2. Supporting data/metrics
3. Recommended actions`,
    summary: `${task.focus} analysis request`
  });
});

// 4. Collect results
// Wait for each analyst to send results back
// Then aggregate findings

// 5. Shutdown all analysts
tasks.forEach(task => {
  SendMessage({
    type: "shutdown_request",
    team_name: "parallel-analysis",
    recipient: task.name,
    content: "Analysis complete, thank you"
  });
});

// 6. Cleanup
TeamDelete({ team_name: "parallel-analysis" });
```

### Benefits
- Reduced total execution time (parallel vs. sequential)
- Independent failure isolation
- Resource utilization optimization

### Considerations
- Higher token cost (multiple active agents)
- Coordination overhead for result aggregation
- Potential resource contention

## Pattern 2: Hierarchical Delegation

Coordinator agent delegates tasks to worker agents and aggregates results.

### Use Case
Research project with one coordinator managing multiple specialized researchers.

### Implementation

```javascript
// 1. Create team
TeamCreate({
  team_name: "research-hierarchy",
  description: "Hierarchical research team"
});

// 2. Spawn coordinator
Task({
  team_name: "research-hierarchy",
  name: "coordinator",
  model: "sonnet",
  prompt: `You are a research coordinator. Your role:
1. Receive research requests
2. Break down into sub-tasks
3. Delegate to worker agents
4. Aggregate results
5. Provide comprehensive summary

You manage: researcher-1, researcher-2, researcher-3`,
  subagent_type: "general-purpose",
  run_in_background: true
});

// 3. Spawn workers
const workers = ["researcher-1", "researcher-2", "researcher-3"];
workers.forEach(name => {
  Task({
    team_name: "research-hierarchy",
    name: name,
    model: "haiku",
    prompt: `You are a research assistant. Follow instructions from coordinator precisely. Provide thorough, evidence-based responses.`,
    subagent_type: "general-purpose",
    run_in_background: true
  });
});

// 4. Send main task to coordinator
SendMessage({
  type: "message",
  team_name: "research-hierarchy",
  recipient: "coordinator",
  content: `Research project: Impact of AI on software development productivity.

Please coordinate research across multiple aspects and provide comprehensive report.`,
  summary: "AI productivity research project"
});

// Coordinator then delegates:
// - researcher-1: Literature review
// - researcher-2: Case studies
// - researcher-3: Statistical analysis
```

### Coordinator Logic

```javascript
// Coordinator receives task and delegates
SendMessage({
  type: "message",
  team_name: "research-hierarchy",
  recipient: "researcher-1",
  content: "Conduct literature review on AI coding assistants. Focus on peer-reviewed papers from 2023-2026.",
  summary: "Literature review task"
});

SendMessage({
  type: "message",
  team_name: "research-hierarchy",
  recipient: "researcher-2",
  content: "Gather case studies of companies using AI in development. Include metrics on productivity gains.",
  summary: "Case study research"
});

SendMessage({
  type: "message",
  team_name: "research-hierarchy",
  recipient: "researcher-3",
  content: "Analyze statistical data on developer productivity with/without AI tools.",
  summary: "Statistical analysis task"
});

// Wait for responses, then aggregate and respond to original requester
```

### Benefits
- Clear responsibility hierarchy
- Coordinator handles complexity
- Workers focus on specialized tasks
- Scalable pattern (add more workers)

### Considerations
- Coordinator must handle task decomposition
- Higher token cost (coordinator + workers)
- Potential bottleneck at coordinator

## Pattern 3: Review Pipeline

Multi-stage review process with different agents handling each stage.

### Use Case
Code review workflow with linting, security analysis, and human review.

### Implementation

```javascript
// 1. Create team
TeamCreate({
  team_name: "code-review",
  description: "Automated code review pipeline"
});

// 2. Spawn reviewers
const reviewers = [
  { name: "linter", focus: "code style and formatting" },
  { name: "security", focus: "security vulnerabilities" },
  { name: "performance", focus: "performance optimization" },
  { name: "reviewer", focus: "overall code quality" }
];

reviewers.forEach(r => {
  Task({
    team_name: "code-review",
    name: r.name,
    model: "sonnet",
    prompt: `You are a code reviewer specializing in ${r.focus}. Review code thoroughly and provide actionable feedback.`,
    subagent_type: "general-purpose",
    run_in_background: true
  });
});

// 3. Pipeline execution
const codeToReview = "/path/to/code.py";

// Stage 1: Linter
SendMessage({
  type: "message",
  team_name: "code-review",
  recipient: "linter",
  content: `Review ${codeToReview} for style issues. Check: PEP8, naming conventions, formatting.`,
  summary: "Linting review"
});

// Wait for linter response...

// Stage 2: Security (parallel with performance)
SendMessage({
  type: "message",
  team_name: "code-review",
  recipient: "security",
  content: `Security review of ${codeToReview}. Check for: SQL injection, XSS, insecure dependencies.`,
  summary: "Security review"
});

SendMessage({
  type: "message",
  team_name: "code-review",
  recipient: "performance",
  content: `Performance review of ${codeToReview}. Identify bottlenecks and optimization opportunities.`,
  summary: "Performance review"
});

// Wait for security and performance...

// Stage 3: Final review
SendMessage({
  type: "message",
  team_name: "code-review",
  recipient: "reviewer",
  content: `Final review of ${codeToReview}.

Linter feedback: [insert linter results]
Security feedback: [insert security results]
Performance feedback: [insert performance results]

Provide overall assessment and approval/rejection.`,
  summary: "Final code review"
});
```

### Benefits
- Specialized expertise at each stage
- Parallel execution where possible
- Comprehensive multi-perspective review

### Considerations
- Pipeline orchestration complexity
- Need to aggregate results between stages
- Higher latency (sequential stages)

## Pattern 4: Iterative Refinement

Agent produces work, receives feedback, and iterates until approved.

### Use Case
Document writing with revision cycles.

### Implementation

```javascript
// 1. Create team
TeamCreate({
  team_name: "document-writing",
  description: "Iterative document creation team"
});

// 2. Spawn writer and reviewer
Task({
  team_name: "document-writing",
  name: "writer",
  model: "sonnet",
  prompt: "You are a technical writer. Create clear, well-structured documentation. Incorporate feedback and iterate.",
  subagent_type: "general-purpose",
  run_in_background: true
});

Task({
  team_name: "document-writing",
  name: "reviewer",
  model: "sonnet",
  prompt: "You are a technical reviewer. Review documentation for clarity, accuracy, and completeness. Provide specific, actionable feedback.",
  subagent_type: "general-purpose",
  run_in_background: true
});

// 3. Initial writing request
SendMessage({
  type: "message",
  team_name: "document-writing",
  recipient: "writer",
  content: "Write API documentation for the TeamCreate tool. Include: purpose, parameters, examples, error handling.",
  summary: "API docs writing task"
});

// 4. Writer produces draft and sends to reviewer
// (Writer's action):
SendMessage({
  type: "message",
  team_name: "document-writing",
  recipient: "reviewer",
  content: `Please review this draft:

[Draft content...]

Provide feedback on clarity, completeness, and accuracy.`,
  summary: "Draft for review"
});

// 5. Reviewer provides feedback
// (Reviewer's action):
SendMessage({
  type: "message",
  team_name: "document-writing",
  recipient: "writer",
  content: `Feedback on draft:

1. Add more examples for error scenarios
2. Clarify parameter validation rules
3. Include migration notes for v1 to v2

Overall: Good structure, needs more detail.`,
  summary: "Review feedback"
});

// 6. Writer revises based on feedback
// Repeat steps 4-6 until approved

// 7. Final approval
SendMessage({
  type: "message",
  team_name: "document-writing",
  recipient: "writer",
  content: "Documentation approved. Excellent work!",
  summary: "Approval"
});
```

### Benefits
- Quality improvement through iteration
- Feedback loop built into workflow
- Human-in-the-loop compatible (reviewer can be human)

### Considerations
- Multiple iteration cycles increase cost
- Need clear termination criteria
- Potential infinite loop without approval logic

## Pattern 5: Event-Driven Coordination

Agents react to events and trigger downstream workflows.

### Use Case
Monitoring system where agents respond to specific events.

### Implementation

```javascript
// 1. Create team
TeamCreate({
  team_name: "event-monitor",
  description: "Event-driven monitoring team"
});

// 2. Spawn event handlers
Task({
  team_name: "event-monitor",
  name: "error-handler",
  model: "haiku",
  prompt: "You handle error events. Analyze errors, categorize severity, and escalate if needed.",
  subagent_type: "general-purpose",
  run_in_background: true
});

Task({
  team_name: "event-monitor",
  name: "performance-handler",
  model: "haiku",
  prompt: "You handle performance events. Monitor metrics and alert on anomalies.",
  subagent_type: "general-purpose",
  run_in_background: true
});

Task({
  team_name: "event-monitor",
  name: "escalation-handler",
  model: "sonnet",
  prompt: "You handle escalations. Assess critical issues and coordinate response.",
  subagent_type: "general-purpose",
  run_in_background: true
});

// 3. Event simulation
const errorEvent = {
  type: "error",
  severity: "high",
  message: "Database connection timeout",
  timestamp: "2026-02-16T12:00:00Z"
};

// 4. Route event to handler
SendMessage({
  type: "message",
  team_name: "event-monitor",
  recipient: "error-handler",
  content: `Error event detected:
${JSON.stringify(errorEvent, null, 2)}

Analyze and determine if escalation needed.`,
  summary: "Error event"
});

// 5. Error handler escalates
SendMessage({
  type: "message",
  team_name: "event-monitor",
  recipient: "escalation-handler",
  content: `Critical error requires escalation:

Error: Database connection timeout
Severity: High
Impact: Production database unavailable
Recommended action: Emergency failover to backup

Please coordinate response.`,
  summary: "Critical error escalation"
});
```

### Benefits
- Reactive, event-driven architecture
- Specialized handlers for event types
- Automatic escalation paths

### Considerations
- Event routing logic required
- Need event classification system
- Potential event flooding

## Pattern 6: MapReduce Pattern

Distribute work across workers (map) and aggregate results (reduce).

### Use Case
Process large dataset by splitting into chunks.

### Implementation

```javascript
// 1. Create team
TeamCreate({
  team_name: "mapreduce",
  description: "MapReduce processing team"
});

// 2. Spawn mapper and reducer
const mappers = ["mapper-1", "mapper-2", "mapper-3"];
mappers.forEach(name => {
  Task({
    team_name: "mapreduce",
    name: name,
    model: "haiku",
    prompt: "You are a data processor. Process assigned data chunk and return results.",
    subagent_type: "general-purpose",
    run_in_background: true
  });
});

Task({
  team_name: "mapreduce",
  name: "reducer",
  model: "sonnet",
  prompt: "You aggregate results from mappers. Combine, deduplicate, and summarize.",
  subagent_type: "general-purpose",
  run_in_background: true
});

// 3. Split data and assign to mappers
const dataChunks = [
  "/data/chunk-1.csv",
  "/data/chunk-2.csv",
  "/data/chunk-3.csv"
];

mappers.forEach((name, index) => {
  SendMessage({
    type: "message",
    team_name: "mapreduce",
    recipient: name,
    content: `Process ${dataChunks[index]}:
1. Count unique users
2. Calculate total revenue
3. Find top 10 products

Return results as JSON.`,
    summary: `Process ${dataChunks[index]}`
  });
});

// 4. Mappers send results to reducer
// (Mapper's action):
SendMessage({
  type: "message",
  team_name: "mapreduce",
  recipient: "reducer",
  content: `Results from mapper-1:
{
  "unique_users": 1250,
  "total_revenue": 45000,
  "top_products": [...]
}`,
  summary: "Mapper-1 results"
});

// 5. Reducer aggregates
// (After receiving all mapper results):
SendMessage({
  type: "message",
  team_name: "mapreduce",
  recipient: "coordinator",  // Or original requester
  content: `Final aggregated results:
{
  "unique_users": 3750,
  "total_revenue": 135000,
  "top_products": [...]
}`,
  summary: "Final MapReduce results"
});
```

### Benefits
- Scales to large datasets
- Parallel processing efficiency
- Fault tolerance (retry failed chunks)

### Considerations
- Data splitting complexity
- Reducer coordination overhead
- Network/storage for intermediate results

## Pattern 7: Consensus Building

Multiple agents analyze same problem and vote on solution.

### Use Case
Important decision requiring multiple perspectives.

### Implementation

```javascript
// 1. Create team
TeamCreate({
  team_name: "consensus",
  description: "Multi-agent consensus team"
});

// 2. Spawn analysts with different focuses
const analysts = [
  { name: "analyst-cost", focus: "cost optimization" },
  { name: "analyst-performance", focus: "performance" },
  { name: "analyst-security", focus: "security" }
];

analysts.forEach(a => {
  Task({
    team_name: "consensus",
    name: a.name,
    model: "sonnet",
    prompt: `You analyze problems from ${a.focus} perspective. Provide recommendations based on your expertise.`,
    subagent_type: "general-purpose",
    run_in_background: true
  });
});

Task({
  team_name: "consensus",
  name: "facilitator",
  model: "opus",
  prompt: "You facilitate consensus. Collect recommendations, identify conflicts, and synthesize final decision.",
  subagent_type: "general-purpose",
  run_in_background: true
});

// 3. Present problem to all analysts
const problem = "Should we migrate to serverless architecture?";

analysts.forEach(a => {
  SendMessage({
    type: "message",
    team_name: "consensus",
    recipient: a.name,
    content: `Analyze from ${a.focus} perspective: ${problem}

Provide:
1. Recommendation (yes/no/conditional)
2. Key considerations
3. Risks and benefits`,
    summary: "Architecture decision analysis"
  });
});

// 4. Analysts send recommendations to facilitator
// (Analyst's action):
SendMessage({
  type: "message",
  team_name: "consensus",
  recipient: "facilitator",
  content: `Cost analysis recommendation:

Recommendation: Yes, with conditions
Key considerations:
- 30% cost reduction for variable load
- Higher cost for consistent high load
- Migration cost: $50k

Recommendation: Proceed with pilot project first.`,
  summary: "Cost analysis complete"
});

// 5. Facilitator synthesizes consensus
SendMessage({
  type: "message",
  team_name: "consensus",
  recipient: "decision-maker",
  content: `Consensus analysis complete:

Cost perspective: Yes (with pilot)
Performance perspective: Yes (improves scalability)
Security perspective: Conditional (need security review)

Consensus: Proceed with pilot project
Include security review before full migration.`,
  summary: "Consensus decision"
});
```

### Benefits
- Multi-perspective analysis
- Reduces bias
- Higher confidence in decisions

### Considerations
- High token cost (multiple analyses)
- Conflict resolution needed
- Facilitator must synthesize effectively

## Pattern 8: Dynamic Team Scaling

Add or remove teammates based on workload.

### Use Case
Variable workload requiring elastic team size.

### Implementation

```javascript
// 1. Create base team
TeamCreate({
  team_name: "elastic-team",
  description: "Dynamically scaling team"
});

// 2. Spawn coordinator
Task({
  team_name: "elastic-team",
  name: "coordinator",
  model: "sonnet",
  prompt: "You coordinate work. Monitor queue depth and scale team up/down by spawning or shutting down workers.",
  subagent_type: "general-purpose",
  run_in_background: true
});

// 3. Coordinator monitors workload
function checkWorkload() {
  const queueDepth = getTaskQueue().length;
  const activeWorkers = getActiveWorkers().length;

  if (queueDepth > 10 && activeWorkers < 5) {
    // Scale up
    const newWorkerName = `worker-${Date.now()}`;
    Task({
      team_name: "elastic-team",
      name: newWorkerName,
      model: "haiku",
      prompt: "You process tasks from queue. Report completion and wait for next task.",
      subagent_type: "general-purpose",
      run_in_background: true
    });
  }

  if (queueDepth < 2 && activeWorkers > 1) {
    // Scale down
    const workerToRemove = getIdleWorker();
    SendMessage({
      type: "shutdown_request",
      team_name: "elastic-team",
      recipient: workerToRemove,
      content: "Low workload, shutting down excess capacity"
    });
  }
}

// 4. Monitor and scale continuously
setInterval(checkWorkload, 30000);  // Every 30 seconds
```

### Benefits
- Cost optimization (scale to demand)
- Performance optimization (scale for capacity)
- Automatic resource management

### Considerations
- Scaling decision logic complexity
- Worker startup/shutdown overhead
- State management during scaling

## Best Practices Summary

### Communication Patterns

**Direct vs. Broadcast:**
- Use direct messages for one-to-one communication
- Reserve broadcasts for urgent team-wide updates
- Broadcast cost = N × message cost (N = team size)

**Message Granularity:**
- Keep messages focused and actionable
- Include context in summary field
- Use metadata for additional information

### Error Handling

**Graceful Degradation:**
```javascript
try {
  SendMessage({...});
} catch (error) {
  if (error.type === 'RECIPIENT_INACTIVE') {
    // Try alternate worker or queue for retry
  }
}
```

**Timeout Handling:**
```javascript
const timeout = setTimeout(() => {
  // Agent didn't respond in time
  // Reassign task or escalate
}, 300000);  // 5 minutes
```

### Resource Management

**Token Budget:**
- Monitor token usage per agent
- Set budgets and shutdown when exceeded
- Use cheaper models for simple tasks

**Cleanup:**
```javascript
// Always cleanup on completion
async function cleanup(teamName) {
  const config = await readConfig(teamName);

  // Shutdown all active members
  for (const member of config.members) {
    if (member.isActive) {
      await shutdownMember(teamName, member.name);
    }
  }

  // Verify all shutdown
  await waitForAllShutdown(teamName);

  // Delete team
  await TeamDelete({ team_name: teamName });
}
```

### Monitoring and Observability

**Track Metrics:**
```javascript
// Log all team activities
function logActivity(teamName, event) {
  const log = {
    team: teamName,
    event: event.type,
    timestamp: new Date().toISOString(),
    details: event
  };

  appendToLog(`/logs/${teamName}.jsonl`, log);
}
```

**Health Checks:**
```javascript
// Periodic health verification
async function healthCheck(teamName) {
  const config = await readConfig(teamName);

  for (const member of config.members) {
    if (member.isActive) {
      // Verify tmux pane exists
      const paneExists = await tmuxPaneExists(member.tmuxPaneId);

      if (!paneExists) {
        // Pane died, mark as inactive
        await updateMemberStatus(teamName, member.name, false);
      }
    }
  }
}
```

## See Also

- **SKILL.md** - Core lifecycle workflows
- **api-specification.md** - Complete API reference
- **file-formats.md** - File format details
