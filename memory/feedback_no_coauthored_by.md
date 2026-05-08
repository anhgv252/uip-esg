---
name: No Co-Authored-By in commits
description: Do not add Co-Authored-By Claude signature to git commits
type: feedback
---

Không thêm dòng `Co-Authored-By: Claude ...` vào commit message.

**Why:** Team dùng nhiều AI tools (GitHub Copilot, Zai, Claude, ...) nên không muốn sign commit với thông tin tool cụ thể nào.

**How to apply:** Khi tạo git commit, bỏ hoàn toàn phần `Co-Authored-By` — chỉ viết commit message thuần.
