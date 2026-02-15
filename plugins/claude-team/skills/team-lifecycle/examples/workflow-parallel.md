# Parallel Workflow Example

This example demonstrates creating a team that processes multiple independent tasks in parallel, then aggregates results.

## Scenario

Research team analyzing three different data sources simultaneously:
- Sales trends analysis
- Customer behavior analysis
- Market intelligence research

Each analyst works independently, then a coordinator aggregates findings into a comprehensive report.

## Step-by-Step Workflow

### 1. Create Team

```javascript
TeamCreate({
  team_name: "parallel-research",
  description: "Parallel data analysis team for Q4 comprehensive report"
});
```

**Result:**
```
Created: ~/.claude/teams/parallel-research/
Created: ~/.claude/teams/parallel-research/config.json
Created: ~/.claude/teams/parallel-research/inboxes/
```

### 2. Spawn Coordinator

```javascript
Task({
  team_name: "parallel-research",
  name: "coordinator",
  model: "sonnet",
  prompt: `You are a research coordinator managing parallel analysis tasks.

Your responsibilities:
1. Receive analysis requests
2. Delegate to appropriate analysts
3. Monitor progress
4. Aggregate results into comprehensive reports
5. Ensure quality and completeness

You manage three analysts:
- sales-analyst: Sales trends and revenue analysis
- customer-analyst: Customer behavior and segmentation
- market-analyst: Market trends and competitive intelligence

When aggregating results:
- Identify cross-cutting insights
- Resolve contradictions
- Synthesize recommendations
- Present cohesive narrative`,
  subagent_type: "general-purpose",
  run_in_background: true
});
```

**Result:**
```json
{
  "agentId": "coordinator@parallel-research",
  "name": "coordinator",
  "model": "sonnet",
  "tmuxPaneId": "%88",
  "isActive": true
}
```

### 3. Spawn Three Analysts in Parallel

```javascript
// Sales Analyst
Task({
  team_name: "parallel-research",
  name: "sales-analyst",
  model: "haiku",
  prompt: `You are a data analyst specializing in sales trends.

Analyze assigned data and provide:
1. Key trends and patterns
2. Quantitative metrics
3. Supporting evidence
4. Actionable recommendations

Report findings to coordinator when complete.`,
  subagent_type: "general-purpose",
  run_in_background: true
});

// Customer Analyst
Task({
  team_name: "parallel-research",
  name: "customer-analyst",
  model: "haiku",
  prompt: `You are a data analyst specializing in customer behavior.

Analyze assigned data and provide:
1. Customer segment identification
2. Behavioral patterns
3. Qualitative insights
4. Retention/growth strategies

Report findings to coordinator when complete.`,
  subagent_type: "general-purpose",
  run_in_background: true
});

// Market Analyst
Task({
  team_name: "parallel-research",
  name: "market-analyst",
  model: "haiku",
  prompt: `You are a market analyst specializing in competitive intelligence.

Analyze assigned data and provide:
1. Competitive landscape
2. Market trends
3. Industry benchmarks
4. Strategic positioning recommendations

Report findings to coordinator when complete.`,
  subagent_type: "general-purpose",
  run_in_background: true
});
```

**Result:**
Three analysts spawned in parallel tmux panes, all ready to receive tasks.

### 4. Coordinator Delegates Tasks

```javascript
// Main request to coordinator
SendMessage({
  type: "message",
  team_name: "parallel-research",
  recipient: "coordinator",
  content: `Please conduct comprehensive Q4 analysis covering:
1. Sales performance and trends
2. Customer behavior patterns
3. Market position and competition

Data available:
- /data/q4-sales.csv
- /data/q4-customers.csv
- /data/market-intel.json

Deadline: End of day
Priority: High

Provide comprehensive report with integrated insights.`,
  summary: "Q4 comprehensive analysis request"
});
```

**Coordinator's Actions:**

```javascript
// Coordinator delegates to sales analyst
SendMessage({
  type: "message",
  team_name: "parallel-research",
  recipient: "sales-analyst",
  content: `Analyze Q4 sales data: /data/q4-sales.csv

Focus on:
1. Overall revenue trends (MoM, QoQ)
2. Top performing products/categories
3. Regional performance
4. Channel effectiveness (online vs retail)

Provide:
- Key metrics and trends
- Anomalies or concerns
- Recommendations for sales team

Report back when complete.`,
  summary: "Q4 sales analysis task"
});

// Coordinator delegates to customer analyst
SendMessage({
  type: "message",
  team_name: "parallel-research",
  recipient: "customer-analyst",
  content: `Analyze Q4 customer data: /data/q4-customers.csv

Focus on:
1. Customer segmentation (demographics, value)
2. Behavioral patterns (purchase frequency, basket size)
3. Churn indicators
4. High-value customer characteristics

Provide:
- Customer segment profiles
- Behavioral insights
- Retention strategies
- Growth opportunities

Report back when complete.`,
  summary: "Q4 customer analysis task"
});

// Coordinator delegates to market analyst
SendMessage({
  type: "message",
  team_name: "parallel-research",
  recipient: "market-analyst",
  content: `Analyze Q4 market intelligence: /data/market-intel.json

Focus on:
1. Competitive landscape changes
2. Market trends and shifts
3. Our position vs competitors
4. Industry benchmarks

Provide:
- Competitive analysis
- Market trend insights
- Strategic recommendations
- Opportunities and threats

Report back when complete.`,
  summary: "Q4 market analysis task"
});
```

### 5. Analysts Work in Parallel

Each analyst independently processes their assigned data:

**Timeline:**
```
10:30 - Tasks assigned to all three analysts
10:31 - All three begin analysis (parallel execution)
11:15 - sales-analyst completes (45 min)
11:20 - customer-analyst completes (50 min)
11:25 - market-analyst completes (55 min)
```

**Total elapsed time: 55 minutes** (vs 150 minutes if sequential)

### 6. Analysts Report Results to Coordinator

**Sales Analyst Response:**

```javascript
SendMessage({
  type: "message",
  team_name: "parallel-research",
  recipient: "coordinator",
  content: `Sales Analysis Complete

## Key Findings:
1. Revenue up 23% QoQ ($4.2M → $5.16M)
2. Top 3 products = 53% of revenue
3. West Coast strongest region (+28%)
4. Online channel accelerating (+31%)

## Concerns:
- South region lagging (+15% vs +23% avg)
- Retail channel growth slowing (+12%)

## Recommendations:
1. Investigate South region performance
2. Invest in online channel expansion
3. Review retail strategy

Full analysis: /data/analysis/q4-sales-report.md`,
  summary: "Sales analysis complete - 23% growth"
});
```

**Customer Analyst Response:**

```javascript
SendMessage({
  type: "message",
  team_name: "parallel-research",
  recipient: "coordinator",
  content: `Customer Analysis Complete

## Segments Identified:
1. High-Value (15%): $500+ avg order, 5+ purchases/qtr
2. Growing (35%): $200-500 avg, increasing frequency
3. At-Risk (25%): Declining purchase frequency
4. New (25%): First purchase in Q4

## Key Behaviors:
- High-value customers prefer Product A (73%)
- Growing segment responds to promotions
- At-risk segment shows price sensitivity
- New customers primarily online (82%)

## Retention Strategies:
1. VIP program for high-value segment
2. Targeted promotions for growing segment
3. Win-back campaign for at-risk segment
4. Onboarding optimization for new customers

Full analysis: /data/analysis/q4-customer-report.md`,
  summary: "Customer analysis complete - 4 segments"
});
```

**Market Analyst Response:**

```javascript
SendMessage({
  type: "message",
  team_name: "parallel-research",
  recipient: "coordinator",
  content: `Market Analysis Complete

## Competitive Landscape:
- We are #3 in market (12% share)
- Competitor A leads (28%), Competitor B (18%)
- Gap narrowing: We grew 23% vs market 15%

## Market Trends:
1. Shift to online accelerating (industry-wide)
2. Subscription models gaining traction
3. Sustainability becoming differentiator
4. Regional expansion opportunities

## Strategic Position:
- Strength: Product quality and customer service
- Weakness: Brand awareness vs top 2
- Opportunity: Underserved South region
- Threat: New entrant with VC funding

## Recommendations:
1. Accelerate online/subscription offerings
2. Invest in brand marketing
3. Expand in South region (first-mover advantage)
4. Develop sustainability messaging

Full analysis: /data/analysis/q4-market-report.md`,
  summary: "Market analysis complete - #3 position"
});
```

### 7. Coordinator Aggregates Results

```javascript
SendMessage({
  type: "message",
  team_name: "parallel-research",
  recipient: "requester",  // Original requester
  content: `Q4 Comprehensive Analysis - Executive Summary

## Overall Performance: Strong Growth with Strategic Opportunities

### Headline Metrics:
- Revenue: $5.16M (+23% QoQ, +8% vs market)
- Market Position: #3 (12% share, gaining)
- Customer Base: Growing across all segments

### Integrated Insights:

**1. Online Channel Momentum**
- Sales: +31% growth in online channel
- Customer: 82% of new customers online
- Market: Industry trend toward online accelerating
→ **Recommendation:** Accelerate online investment, target 75% of revenue by Q2

**2. South Region Opportunity**
- Sales: Lagging growth (+15% vs +23% avg)
- Market: Underserved region, competitors weak
→ **Recommendation:** Launch South expansion initiative, first-mover advantage

**3. Customer Segmentation Strategy**
- Sales: Top 3 products = 53% revenue
- Customer: High-value segment (15%) prefers Product A
- Market: Subscription models gaining traction
→ **Recommendation:** Develop VIP subscription program around Product A

**4. Brand Awareness Gap**
- Sales: Strong growth but limited to existing markets
- Customer: Low brand recognition in new customer research
- Market: #3 position limited by awareness vs quality
→ **Recommendation:** Increase brand marketing investment

### Strategic Priorities (Ranked):

1. **Online/Subscription Expansion** (High impact, ready to execute)
   - Leverage existing momentum
   - Customer demand validated
   - Industry trend aligned

2. **South Region Launch** (High impact, moderate complexity)
   - Underserved market
   - Weak competition
   - Revenue growth potential

3. **VIP Program Development** (Medium impact, low complexity)
   - Retain high-value customers
   - Increase customer lifetime value
   - Differentiation opportunity

4. **Brand Marketing Campaign** (Medium impact, high investment)
   - Close awareness gap
   - Support regional expansion
   - Long-term growth driver

### Risk Factors:
- New VC-funded competitor entering market
- Retail channel declining (mitigation: accelerate online)
- At-risk customer segment (25%) needs retention focus

### Next Steps:
1. Approve strategic priorities and budget
2. Develop detailed execution plans
3. Establish success metrics and monitoring

Full reports available:
- Sales: /data/analysis/q4-sales-report.md
- Customer: /data/analysis/q4-customer-report.md
- Market: /data/analysis/q4-market-report.md
- Integrated: /data/analysis/q4-comprehensive-report.md

Questions or need deeper analysis on any area?`,
  summary: "Q4 comprehensive analysis complete"
});
```

### 8. Shutdown Workflow

```javascript
// Coordinator shuts down analysts
SendMessage({
  type: "shutdown_request",
  team_name: "parallel-research",
  recipient: "sales-analyst",
  content: "Analysis complete. Thank you for excellent work. Shutting down."
});

SendMessage({
  type: "shutdown_request",
  team_name: "parallel-research",
  recipient: "customer-analyst",
  content: "Analysis complete. Thank you for excellent work. Shutting down."
});

SendMessage({
  type: "shutdown_request",
  team_name: "parallel-research",
  recipient: "market-analyst",
  content: "Analysis complete. Thank you for excellent work. Shutting down."
});

// Wait for shutdown approvals...

// Shutdown coordinator
SendMessage({
  type: "shutdown_request",
  team_name: "parallel-research",
  recipient: "coordinator",
  content: "Project complete. Shutting down team."
});

// Wait for all shutdowns to complete...

// Delete team
TeamDelete({ team_name: "parallel-research" });
```

## Performance Analysis

### Time Comparison

**Parallel Execution (this workflow):**
- Task assignment: 1 min
- Parallel analysis: 55 min (longest analyst)
- Aggregation: 15 min
- **Total: 71 minutes**

**Sequential Execution (traditional):**
- Task assignment: 1 min
- Sales analysis: 45 min
- Customer analysis: 50 min
- Market analysis: 55 min
- Aggregation: 15 min
- **Total: 166 minutes**

**Time saved: 95 minutes (57% reduction)**

### Token Cost

**Parallel Execution:**
- Coordinator spawn: ~500 tokens
- 3 analyst spawns: ~1,500 tokens
- Task delegation: ~1,000 tokens
- Analysis work: ~30,000 tokens (3 × 10,000)
- Aggregation: ~5,000 tokens
- **Total: ~38,000 tokens**

**Sequential Execution:**
- Single analyst spawn: ~500 tokens
- Analysis work: ~30,000 tokens (same total work)
- Context switching overhead: ~2,000 tokens
- **Total: ~32,500 tokens**

**Extra cost: ~5,500 tokens (17% increase for 57% time reduction)**

## Key Benefits

1. **Faster Results**: 57% time reduction through parallelization
2. **Specialized Focus**: Each analyst concentrates on their expertise
3. **Independent Analysis**: Reduces bias from sequential exposure
4. **Quality Aggregation**: Coordinator synthesizes cross-cutting insights
5. **Scalability**: Easy to add more parallel analysts

## Considerations

1. **Cost vs Speed Trade-off**: Higher token cost for faster results
2. **Coordination Overhead**: Coordinator must effectively aggregate
3. **Data Dependencies**: Works best for independent data sources
4. **Resource Usage**: Multiple simultaneous processes

## When to Use This Pattern

**✅ Good fit:**
- Independent data sources
- Time-sensitive deliverables
- Complex multi-faceted analysis
- Need for specialized expertise
- Sufficient computational resources

**❌ Poor fit:**
- Sequential dependencies between tasks
- Limited resources
- Simple single-perspective analysis
- Cost-sensitive projects

## See Also

- **SKILL.md** - Core lifecycle concepts
- **advanced-patterns.md** - Other workflow patterns (MapReduce, Hierarchical, etc.)
- **api-specification.md** - Tool parameter details
