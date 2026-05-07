# 📋 Sprint Retrospective Checklist — Tái sử dụng mỗi sprint

Use this checklist **before** closing each sprint (Friday EOD).

---

## ✅ Pre-Demo Checklist (T-24h before sprint review)

**Owner: Dev Lead + QA**

### Code Quality Gate
- [ ] All tests pass: `./gradlew test`, `npm run test` (target: ≥85% pass rate)
- [ ] TypeScript strict: `tsc --noEmit` → 0 errors
- [ ] Build success: `mvn package -DskipTests=false`, `npm run build`
- [ ] SonarQube: no new CRITICAL/BLOCKER issues
- [ ] Code review: all PRs reviewed, approved, merged
- [ ] Git tag: version tag applied to main (e.g., `sprint2-completed`)

### Test Coverage
- [ ] Unit tests: X/Y pass (target: ≥85%)
- [ ] Integration tests: X/Y pass (target: 100%)
- [ ] E2E tests: X/Y pass (target: 100%)
- [ ] Bug HIGH/MEDIUM: 0 open
- [ ] Regression suite: run full suite from Sprints 1-N

### Documentation
- [ ] PRs have linked Jira/GitHub issues
- [ ] Commits have clear messages (no "WIP", "checkpoint")
- [ ] API changes documented (OpenAPI snapshot)
- [ ] Database migrations reviewed (Flyway scripts + rollback tested)

---

## 🎓 Retrospective Meeting Agenda (Sprint Review + Retro = 1.5h)

**Owner: PM | Participants: Dev team + QA**

### Part 1: Sprint Outcome (30 min)
1. **Scope** — Committed vs completed story points
   - [ ] Velocity = X SP / Y committed
   - [ ] Unfinished stories? → Record in carry-over
   - [ ] Scope creep? → How many new stories added mid-sprint?

2. **Quality** — Test coverage, bugs, code review
   - [ ] Test pass rate: X%
   - [ ] Bugs P0/P1: X open
   - [ ] TypeScript strict: passed?
   - [ ] Build time: X sec

3. **Timeline** — Adherence to dates
   - [ ] Sprint closed on-time?
   - [ ] Any blockers that delayed demo?
   - [ ] Days lost to sick leave / context switching?

4. **Risk** — What went wrong?
   - [ ] List blockers encountered
   - [ ] P0 risks? How mitigated?
   - [ ] Residual risk for next sprint?

### Part 2: Lessons Learned (30 min)
**Moderator asks: "What went well? What could improve? What surprised us?"**

For each lesson:
- [ ] **Issue title** (one line)
- [ ] **What happened** (context)
- [ ] **Why** (root cause)
- [ ] **Impact** (if not fixed, what breaks?)
- [ ] **Action** (specific, who, when)
- [ ] **Owner + Due date** (for next sprint)

**Target: Top 10 lessons (most impactful)**

### Part 3: Action Items (15 min)
- [ ] List all action items from lessons
- [ ] Sort by priority (P0 blocker → P2 nice-to-have)
- [ ] Assign owner + due date
- [ ] Track in sprint backlog or separate action log

### Part 4: Next Sprint Readiness (15 min)
- [ ] Sprint N+1 backlog prioritized?
- [ ] ADRs accepted?
- [ ] Team capacity confirmed?
- [ ] Blockers identified?
- [ ] **GO / NO-GO to next sprint?**

---

## 📝 Retrospective Document (Post-Meeting)

**Owner: PM | Format: Markdown**

```markdown
# SPRINT X Retrospective — [Date]

## Outcome Summary
| Aspect | Committed | Completed | Status |
|--------|-----------|-----------|--------|
| Story Points | Y | X | % |
| Unit Tests | — | X/Y | % pass |
| Bugs P0/P1 | 0 | Z | status |
| Timeline | — | [date] | on-time? |

## Top 10 Lessons Learned
1. [Issue] → Action: [specific step] | Owner: [name] | Due: [date]
2. ...

## Action Items
| # | Action | Owner | Due | Priority |
|---|--------|-------|-----|----------|

## Next Sprint Readiness
- [ ] Backlog prioritized
- [ ] Capacity confirmed
- [ ] GO / NO-GO

```

Save as: `.claude/workdir/pm-sprint[X]-retrospective-summary-[DATE].md`

---

## 🚀 Common Pitfalls & Fixes

| Pitfall | Fix |
|---------|-----|
| **Lessons too vague** ("We need better testing") | Make SPECIFIC: "Add E2E test for nav-route coupling" + owner + due date |
| **Action items never tracked** | Create action log, PM reviews bi-weekly, escalate if blocked |
| **Team forgets lessons from previous sprints** | Reference prev sprint retro at next sprint kickoff |
| **"Deploy on Friday" → bugs on Monday** | Add pre-release checklist (code review, tests, smoke test) |
| **Parallel work → migration conflicts** | Reserve version numbers BEFORE code starts |
| **Security review delayed** | Smoke test for security-critical features (enum+migration, RBAC, data isolation) |

---

## 📊 KPI Template

Use for each sprint outcome:

| Metric | Target | Actual | Trend | Status |
|--------|--------|--------|-------|--------|
| Scope (SP velocity) | 40–50 | X | ↗/→/↘ | ✅/⏳/❌ |
| Quality (test pass %) | ≥90 | X% | → | ✅/⏳/❌ |
| Bug count P0/P1 | 0 | X | ✅/❌ | — |
| Timeline adherence | 100% | X% | → | ✅/❌ |
| Risk residual | LOW | — | → | 🟢/🟡/🔴 |

---

## 🎯 Month Cadence (Monthly Sprint Health Review)

**Every 4th sprint (Sprint 4, 8, 12, ...): Full retrospective + roadmap adjustment**

- Review trends: velocity, quality, risk over last 4 sprints
- Identify systemic issues (team morale, process gaps, tech debt accumulation)
- Adjust sprint planning / capacity for next month
- Report to stakeholders (city authority) on project health

---

## 📌 Quick Reference

**Sprint closing formula:**
1. ✅ Tests PASS → code review DONE → PR merged → main stable
2. 📊 Metrics collected → KPI table filled
3. 🎓 Lessons extracted → top 10 + actions
4. 📋 Retrospective doc written → saved to `.claude/workdir/pm-sprint[X]-...md`
5. 🚀 Next sprint readiness → backlog prioritized, GO/NO-GO decided
6. 📧 Stakeholder summary → compressed summary for PO (1-page)

**Time budget: 2–3 hours post-sprint** (demo 1h + retro meeting 1h + doc 30-60 min)

---

**Version:** 1.0 | **Last updated:** 2026-05-07 | **Owner:** PM
