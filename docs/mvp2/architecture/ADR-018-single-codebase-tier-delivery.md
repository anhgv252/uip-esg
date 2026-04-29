# ADR-018: ~~Single Codebase, Configuration-Driven Tier Delivery~~

**Status**: ~~Superseded~~ — **hợp nhất vào ADR-011**
**Date**: 2026-04-28
**Superseded by**: [ADR-011: Monorepo Architecture & Module Extraction Strategy](ADR-011-monorepo-module-extraction.md)

---

> Nội dung ADR này (monorepo structure, capability flags, Helm values per tier, trunk-based git strategy)
> đã được hợp nhất hoàn toàn vào **ADR-011** vì hai quyết định không thể tách rời:
>
> - ADR-011 (module extraction order) và ADR-018 (single codebase delivery) cùng giải quyết
>   một vấn đề: "làm thế nào quản lý source code khi platform scale từ monolith sang microservices
>   qua nhiều tier mà không bị branch diverge?"
>
> - Capability flag "extraction flags" (`iot-ingestion-external`, `alert-external`) là cầu nối
>   trực tiếp giữa hai ADR — không thể hiểu ADR-018 mà không đọc ADR-011 và ngược lại.
>
> **Đọc ADR-011 để có toàn bộ nội dung.**
