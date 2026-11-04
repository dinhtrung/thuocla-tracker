Các file trong thư mục này được **sinh tự động** từ bản web ở repo root — **cấm sửa tay**.
Sửa file gốc ở root (`index.html`, `app.js`, `styles.css`, `manifest.json`, `icon-192.png`, `icon-512.png`),
rồi đồng bộ lại bằng: `scripts/verify-web-assets.sh --copy` (chạy từ repo root).
Script `scripts/verify-web-assets.sh` so sha256 từng cặp và fail khi lệch; CI chạy nó trước bước build APK.
