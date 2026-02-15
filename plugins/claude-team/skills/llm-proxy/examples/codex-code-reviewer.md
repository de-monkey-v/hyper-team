# Using Codex CLI for Code Review - Getting a Second Opinion

This example demonstrates how to use a proxy agent with the Codex CLI to get code reviews from OpenAI models, providing a "second opinion" alongside Claude's analysis.

## Use Case

When you want a different AI perspective on:
- Code quality and potential bugs
- Architecture decisions
- Security vulnerabilities
- Performance improvements
- Best practices compliance

Running code through both Claude and an OpenAI model (via Codex) can catch issues that one model might miss, providing more comprehensive code review coverage.

## Setup

### Prerequisites

1. **Codex CLI installed**
   ```bash
   # Install from: https://github.com/yourusername/codex
   pip install codex-cli
   # or
   npm install -g codex-cli
   ```

2. **OpenAI API Key configured**
   ```bash
   export OPENAI_API_KEY="sk-..."
   ```

3. **Verify Codex is working**
   ```bash
   codex --help
   ```

### Model Recommendations

| Model | Use Case | Speed | Depth |
|-------|----------|-------|-------|
| `o4-mini` | Fast reviews, syntax checks | ⚡ Fast | Basic |
| `o3` | Deep analysis, architecture review | 🐢 Slow | Comprehensive |
| `gpt-4-turbo` | Balanced speed and depth | ⚖️ Medium | Good |

## Proxy System Prompt

Here's a complete system prompt for a code review proxy agent:

```markdown
You are a Code Review Proxy that uses the Codex CLI to get second opinions from OpenAI models.

## Your Role

1. Receive code snippets or file paths from team messages
2. Construct detailed code review prompts
3. Execute Codex CLI with the code
4. Parse and format the review results
5. Send structured feedback via SendMessage

## Code Review Process

### Step 1: Extract Code from Message

When you receive a message with code:
- If it contains inline code, extract it directly
- If it contains a file path, read the file first using the Read tool
- If it's a request without code, ask for the code or file path

### Step 2: Construct Review Prompt

Create a comprehensive review prompt:

```
Review this code for:
1. Bugs and logical errors
2. Security vulnerabilities
3. Performance issues
4. Code style and best practices
5. Potential edge cases

Code:
```language
{code_content}
```

Provide specific, actionable feedback.
```

### Step 3: Execute Codex CLI

Use heredoc syntax for multiline code:

```bash
codex --dangerously-bypass-approvals-and-sandbox << 'EOF'
Review this code for bugs, security issues, and improvements:

{code}

Provide:
- Critical issues (bugs, security)
- Performance concerns
- Best practice violations
- Suggested improvements
EOF
```

For single-line invocation:

```bash
codex --dangerously-bypass-approvals-and-sandbox "Review this code:\n\n{code}\n\nFocus on bugs and security."
```

### Step 4: Format Review Output

Structure the Codex output as:

```markdown
## Code Review Results (via OpenAI {model})

### Critical Issues
- **Issue 1**: Description and location
- **Issue 2**: Description and location

### Performance Concerns
- Concern 1: Explanation
- Concern 2: Explanation

### Best Practice Violations
- Violation 1: Recommendation
- Violation 2: Recommendation

### Suggested Improvements
1. Improvement 1
2. Improvement 2

### Overall Assessment
Summary of code quality
```

### Step 5: Send Response

Use SendMessage with the formatted review to the requesting agent.

## Important Notes

- For files >1000 lines, suggest reviewing specific sections
- Handle API errors gracefully (rate limits, timeouts)
- Always specify which model was used (o4-mini, o3, etc.)
- Preserve code context (function names, line numbers)

## Token Limit Considerations

- **o4-mini**: ~8K tokens input
- **o3**: ~32K tokens input
- **gpt-4-turbo**: ~128K tokens input

For large files, chunk the review or focus on specific functions.
```

## Step-by-Step Workflow

### 1. Create the Team

```bash
cc claude-team:create reviewer-team
```

### 2. Spawn Code Review Proxy

```json
{
  "command": "TeamCreate",
  "teamId": "reviewer-team",
  "agents": [
    {
      "agentId": "codex-reviewer",
      "model": "haiku",
      "role": "general-purpose",
      "systemPrompt": "[Use the proxy system prompt above]"
    }
  ]
}
```

### 3. Send Code for Review

```json
{
  "command": "SendMessage",
  "teamId": "reviewer-team",
  "fromAgentId": "coordinator",
  "toAgentId": "codex-reviewer",
  "content": "Please review this authentication function:\n\n```python\ndef authenticate_user(username, password):\n    user = db.query(f\"SELECT * FROM users WHERE username = '{username}'\")\n    if user and user.password == password:\n        return generate_token(user)\n    return None\n```"
}
```

### 4. Proxy Executes Codex Review

The proxy agent internally runs:

```bash
codex --dangerously-bypass-approvals-and-sandbox << 'EOF'
Review this authentication function for bugs, security issues, and improvements:

```python
def authenticate_user(username, password):
    user = db.query(f"SELECT * FROM users WHERE username = '{username}'")
    if user and user.password == password:
        return generate_token(user)
    return None
```

Focus on:
1. Security vulnerabilities
2. Authentication best practices
3. Error handling
4. Code quality
EOF
```

### 5. Receive Structured Review

```json
{
  "command": "SendMessage",
  "teamId": "reviewer-team",
  "fromAgentId": "codex-reviewer",
  "toAgentId": "coordinator",
  "content": "## Code Review Results (via OpenAI o3)\n\n### Critical Security Issues\n\n- **SQL Injection Vulnerability**: Line 2 uses f-string interpolation directly in SQL query. This allows attackers to inject malicious SQL.\n  - **Risk**: Critical - Complete database compromise\n  - **Fix**: Use parameterized queries: `db.query(\"SELECT * FROM users WHERE username = ?\", [username])`\n\n- **Plain Text Password Comparison**: Line 3 compares passwords directly without hashing.\n  - **Risk**: High - Passwords stored in plain text\n  - **Fix**: Use bcrypt or argon2: `bcrypt.checkpw(password.encode(), user.password_hash)`\n\n### Best Practice Violations\n\n- **No Input Validation**: Username and password are not validated or sanitized\n- **Missing Error Handling**: Database errors are not caught\n- **No Rate Limiting**: Function can be brute-forced\n- **Timing Attack Vulnerable**: Different execution times for valid/invalid users\n\n### Suggested Improvements\n\n1. **Secure Implementation**:\n```python\nimport bcrypt\nfrom sqlalchemy import text\n\ndef authenticate_user(username: str, password: str) -> Optional[str]:\n    if not username or not password:\n        return None\n    \n    try:\n        stmt = text(\"SELECT id, password_hash FROM users WHERE username = :username\")\n        user = db.execute(stmt, {\"username\": username}).fetchone()\n        \n        if user and bcrypt.checkpw(password.encode(), user.password_hash):\n            return generate_token(user.id)\n    except Exception as e:\n        log.error(f\"Authentication error: {e}\")\n    \n    return None\n```\n\n2. **Add Rate Limiting**: Implement exponential backoff after failed attempts\n3. **Add Logging**: Log failed authentication attempts for security monitoring\n4. **Add Type Hints**: Improve code clarity and catch type errors\n\n### Overall Assessment\n\n⚠️ **CRITICAL - Do Not Deploy**: This code has severe security vulnerabilities that would allow complete system compromise. Requires immediate rewrite following security best practices.\n\n**Severity**: 🔴 Critical\n**Recommendation**: Rewrite using the suggested secure implementation"
}
```

### 6. Cleanup

```bash
cc claude-team:destroy reviewer-team
```

## Sample Message Flow

### Scenario: Reviewing a Suspicious API Endpoint

**Step 1: Coordinator sends code**

```json
{
  "command": "SendMessage",
  "teamId": "security-review",
  "fromAgentId": "lead-architect",
  "toAgentId": "codex-reviewer",
  "content": "I'm concerned about this payment processing endpoint. Can you get a second opinion?\n\n```javascript\napp.post('/api/process-payment', (req, res) => {\n  const { amount, cardNumber, cvv } = req.body;\n  \n  // Process payment\n  const charge = stripe.charges.create({\n    amount: amount * 100,\n    currency: 'usd',\n    source: cardNumber,\n    description: 'Order payment'\n  });\n  \n  res.json({ success: true, chargeId: charge.id });\n});\n```"
}
```

**Step 2: Proxy processes and calls Codex**

Internal execution:

```bash
codex --model o3 --dangerously-bypass-approvals-and-sandbox << 'EOF'
Review this payment processing endpoint for security vulnerabilities and best practices:

```javascript
app.post('/api/process-payment', (req, res) => {
  const { amount, cardNumber, cvv } = req.body;

  // Process payment
  const charge = stripe.charges.create({
    amount: amount * 100,
    currency: 'usd',
    source: cardNumber,
    description: 'Order payment'
  });

  res.json({ success: true, chargeId: charge.id });
});
```

Focus on:
1. PCI compliance issues
2. Input validation
3. Error handling
4. Security best practices
5. Async/await usage
EOF
```

**Step 3: Proxy sends formatted review**

```json
{
  "command": "SendMessage",
  "teamId": "security-review",
  "fromAgentId": "codex-reviewer",
  "toAgentId": "lead-architect",
  "content": "## Code Review Results (via OpenAI o3)\n\n### Critical Security Issues\n\n- **🚨 PCI Compliance Violation**: Handling raw card numbers in application code\n  - **Risk**: Catastrophic - PCI-DSS violation, potential fines, legal liability\n  - **Fix**: NEVER handle raw card data. Use Stripe Elements or Payment Intents API\n\n- **Missing Authentication**: No auth middleware on payment endpoint\n  - **Risk**: Critical - Anyone can charge any amount\n  - **Fix**: Add authentication: `app.post('/api/process-payment', authenticate, ...)`\n\n- **No Input Validation**: Amount, cardNumber, CVV not validated\n  - **Risk**: High - Negative amounts, invalid cards accepted\n  - **Fix**: Validate all inputs with joi or express-validator\n\n### Functional Issues\n\n- **Missing Async/Await**: `stripe.charges.create()` returns a Promise but not awaited\n  - **Impact**: Response sent before charge completes\n  - **Fix**: Use `const charge = await stripe.charges.create(...)`\n\n- **No Error Handling**: Stripe API failures crash the endpoint\n  - **Impact**: Server errors, no user feedback\n  - **Fix**: Wrap in try/catch block\n\n- **CVV Not Used**: CVV extracted but never utilized\n  - **Impact**: Reduced fraud protection\n\n### Best Practice Violations\n\n- No rate limiting (brute force vulnerable)\n- No idempotency keys (duplicate charges possible)\n- No logging for audit trail\n- Hardcoded currency\n- No webhook verification for charge status\n\n### Recommended Secure Implementation\n\n```javascript\nconst stripe = require('stripe')(process.env.STRIPE_SECRET_KEY);\nconst { body, validationResult } = require('express-validator');\n\napp.post('/api/process-payment',\n  authenticate,\n  rateLimit({ windowMs: 15 * 60 * 1000, max: 5 }),\n  [\n    body('amount').isFloat({ min: 0.01 }).toFloat(),\n    body('paymentMethodId').notEmpty()\n  ],\n  async (req, res) => {\n    try {\n      const errors = validationResult(req);\n      if (!errors.isEmpty()) {\n        return res.status(400).json({ errors: errors.array() });\n      }\n\n      const { amount, paymentMethodId } = req.body;\n      const idempotencyKey = req.headers['idempotency-key'];\n\n      // Use Payment Intents API (PCI compliant)\n      const paymentIntent = await stripe.paymentIntents.create({\n        amount: Math.round(amount * 100),\n        currency: 'usd',\n        payment_method: paymentMethodId,\n        confirm: true,\n        metadata: {\n          userId: req.user.id,\n          orderId: req.body.orderId\n        }\n      }, {\n        idempotencyKey\n      });\n\n      // Log for audit\n      await auditLog.create({\n        action: 'payment_processed',\n        userId: req.user.id,\n        amount,\n        paymentIntentId: paymentIntent.id\n      });\n\n      res.json({\n        success: true,\n        paymentIntentId: paymentIntent.id,\n        status: paymentIntent.status\n      });\n\n    } catch (error) {\n      logger.error('Payment processing failed', {\n        error: error.message,\n        userId: req.user?.id\n      });\n\n      if (error.type === 'StripeCardError') {\n        return res.status(402).json({\n          error: 'Payment failed',\n          message: error.message\n        });\n      }\n\n      res.status(500).json({ error: 'Payment processing failed' });\n    }\n  }\n);\n```\n\n### Overall Assessment\n\n⛔ **BLOCK DEPLOYMENT - Critical Security Issues**\n\nThis code violates PCI-DSS compliance and has severe security vulnerabilities. **Do not deploy to production** under any circumstances.\n\n**Required Actions**:\n1. ✅ Implement Stripe Payment Intents API (no raw card data)\n2. ✅ Add authentication and authorization\n3. ✅ Implement proper async/await and error handling\n4. ✅ Add input validation and rate limiting\n5. ✅ Set up webhook handlers for charge confirmations\n\n**Severity**: 🔴 Critical  \n**Timeline**: Rewrite required before any production deployment  \n**Compliance**: PCI-DSS violation - legal risk"
}
```

## Notes and Best Practices

### For Large Files

When reviewing files >1000 lines:

```json
{
  "command": "SendMessage",
  "content": "This file is 2500 lines. Please review the following sections:\n\n1. Lines 100-200: Authentication logic\n2. Lines 500-600: Database queries\n3. Lines 1200-1300: Payment processing\n\nFile path: /src/app/main.py"
}
```

The proxy should:
1. Read the file using the Read tool
2. Extract relevant line ranges
3. Review each section separately
4. Combine results into one comprehensive report

### Token Limit Strategy

```markdown
**Proxy logic for large files:**

if file_lines < 500:
    review_entire_file()
elif file_lines < 2000:
    chunk_by_functions()
    review_each_chunk()
    combine_results()
else:
    ask_user_for_specific_sections()
```

### Error Handling

The proxy should handle:

```bash
# Rate limit errors
if "rate_limit_exceeded" in error:
    send_message("OpenAI rate limit hit. Retrying in 60s...")
    sleep(60)
    retry()

# Model unavailable
if "model_not_available" in error:
    send_message(f"Model {model} unavailable. Falling back to gpt-4-turbo...")
    retry_with_fallback_model()

# Timeout
if "timeout" in error:
    send_message("Review timed out. The code may be too complex. Try smaller sections.")
```

### Formatting Tips

For better readability in team messages:

- Use emoji indicators: 🔴 Critical, 🟡 Warning, 🟢 Good
- Include line numbers when possible
- Provide code snippets for fixes
- Separate issues by severity
- Always include an "Overall Assessment" summary

### Model Selection Guide

**Use o4-mini for:**
- Quick syntax checks
- Style guide compliance
- Simple bug detection
- High-volume reviews

**Use o3 for:**
- Security audits
- Architecture reviews
- Complex logic validation
- Production-critical code

**Use gpt-4-turbo for:**
- Balanced reviews
- Medium complexity
- Good speed/depth trade-off

### Integration with Claude's Review

To get the best of both perspectives:

1. **Claude reviews first** (fast, integrated)
2. **Codex proxy reviews second** (different model perspective)
3. **Compare findings** (what did each catch?)
4. **Synthesize results** (combined recommendation)

Example workflow:

```markdown
## Combined Code Review

### Claude's Findings
- Issue A (both found)
- Issue B (Claude only)

### OpenAI's Findings
- Issue A (both found)
- Issue C (OpenAI only)

### Consensus Recommendation
Fix Issue A immediately (both models agree it's critical)
Investigate Issue B and C (flagged by one model each)
```

## Conclusion

Using the Codex CLI proxy provides a powerful second opinion mechanism, catching issues that might be missed by a single model. This multi-model approach significantly improves code review quality and reduces the risk of shipping bugs or security vulnerabilities.
