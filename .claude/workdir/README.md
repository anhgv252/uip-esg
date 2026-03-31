# .claude/workdir — Agent Shared Memory

Thư mục này là **shared memory** giữa các agents — thay thế việc pass context inline.

## Quy tắc
- Agents GHI output vào đây thay vì trả về inline trong conversation
- Agents ĐỌC input của agent trước từ đây thay vì nhận qua message
- File được đặt tên theo pattern: `{role}-{artifact}-{feature}.md`
- Dọn dẹp sau khi feature hoàn thành (archive vào `docs/`)

## Naming Convention
```
ba-spec-[feature].md          ← Business Analyst output
sa-output-[feature].md        ← Solution Architect output  
ux-spec-[feature].md          ← UI Designer output
qa-plan-[feature].md          ← QA Engineer test plan
test-report-[YYYYMMDD].md     ← Tester execution results
pm-sprint-[N].md              ← Project Manager sprint artifacts
pm-status-[YYYYMMDD].md       ← Weekly status reports
```

## Tại sao dùng file thay vì inline context?

| Approach | Token cost per agent | Total for 8-agent chain |
|----------|---------------------|------------------------|
| Inline context | 15,000 tokens | ~120,000 tokens |
| File-based | 500 tokens (compressed summary) | ~4,000 tokens |

**Tiết kiệm: ~97% token cost cho context passing**

Agents chỉ truyền nhau COMPRESSED SUMMARY (100–300 tokens).
Chi tiết đầy đủ nằm trong files ở thư mục này.

## Không commit vào git
Thêm `.claude/workdir/` vào `.gitignore`
(Đây là temporary working memory, không phải documentation)
