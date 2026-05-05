# FAQ — Tier 1 Customer Support

**Dành cho:** Support team Tier 1 (first-line customer support)  
**Scope:** UIP ESG POC — Production support  
**Escalation:** Tier 2 = Backend team Slack `#uip-support-escalation`

---

## Dashboard & Data

**Q: Dashboard không load, chỉ thấy spinner?**  
A: Kiểm tra network tab trong browser. Nếu thấy 401 → token hết hạn, yêu cầu user logout/login lại. Nếu thấy 503 → hệ thống đang maintenance, xem status page.

**Q: Dữ liệu ESG hiển thị 0 hoặc không cập nhật?**  
A: Dữ liệu ESG được tính theo batch (15 phút). Nếu sau 30 phút vẫn 0, escalate Tier 2 với `tenantId` và timestamp.

**Q: Biểu đồ cảm biến bị gián đoạn (gap)?**  
A: Gap thường do kết nối sensor bị mất tạm thời. Dữ liệu không được backfill. Ghi nhận timestamp gap và escalate nếu gap > 1 giờ.

---

## Authentication & Access

**Q: User không đăng nhập được — "Invalid credentials"?**  
A: Xác nhận email đúng chính tả, phân biệt HOA/thường. Nếu vẫn lỗi, dùng "Forgot password" flow. Nếu user chưa đặt mật khẩu (mới nhận invite), hướng dẫn kiểm tra email invitation.

**Q: User nhận được invite email nhưng link expired?**  
A: Invite token có hiệu lực 72 giờ. Tenant Admin cần gửi lại invite từ trang User Management.

**Q: "You don't have permission" khi truy cập feature?**  
A: Kiểm tra role của user với Tenant Admin. Có thể cần TENANT_ADMIN hoặc OPERATOR role. Không tự ý thay đổi role — yêu cầu Tenant Admin của khách hàng thực hiện.

---

## Reports

**Q: Generate ESG report thất bại?**  
A: Kiểm tra xem tenant có đủ dữ liệu trong period không. Nếu lỗi vẫn xảy ra, lấy `requestId` từ response và escalate Tier 2.

**Q: Download report bị lỗi hoặc file rỗng?**  
A: Thử lại sau 5 phút (report có thể đang generate). Nếu vẫn lỗi, escalate với `reportId`.

---

## Escalation Checklist (trước khi escalate Tier 2)

- [ ] `tenantId` của khách hàng
- [ ] Email user gặp vấn đề
- [ ] Timestamp chính xác lúc lỗi xảy ra (kèm timezone)
- [ ] Screenshot lỗi hoặc error message
- [ ] Các bước đã thử (logout/login, clear cache, etc.)
