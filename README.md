# Đọc Chữ Việt (Android)

Ứng dụng chụp ảnh, nhận dạng chữ tiếng Việt và đọc to. Chạy hoàn toàn offline.

- Nhận dạng: Tesseract 5 (thư viện Tesseract4Android) với mô hình `vie` có sẵn trong app (`app/src/main/assets/tessdata/vie.traineddata`).
- Hai chế độ: **Văn bản tiếng Việt**, và **Chỉ chữ số** (chỉ nhận số và dấu `. , : ; - / + % ( )`).
- Tăng tương phản trước khi nhận dạng (chuyển xám + giãn sáng tối), giúp nhiều với ảnh mờ hoặc tối.
- Đọc to bằng bộ đọc văn bản của Android: chỉnh được tốc độ, dừng tạm rồi đọc tiếp đúng câu đang đọc.
- Sao chép, chia sẻ văn bản. Nhận ảnh được chia sẻ từ Thư viện hoặc Zalo, v.v.
- Android 5.0 trở lên. Không xin quyền camera (dùng ứng dụng camera có sẵn) và không cần mạng.

## Build bằng GitHub Actions

1. Tạo một repository mới trên GitHub và đẩy toàn bộ thư mục này lên (giữ nguyên thư mục `.github`).
2. Vào tab **Actions**. Workflow **Build APK** sẽ tự chạy mỗi lần push, hoặc bấm **Run workflow** để chạy tay.
3. Khi chạy xong (khoảng 5–8 phút), mở lượt chạy đó và tải **DocChuViet-apk** ở mục *Artifacts*. Giải nén ra file APK:
   - `app-release.apk`: bản nên cài.
   - `app-debug.apk`: bản để tìm lỗi.
4. Chép APK vào điện thoại, mở file và cho phép "Cài ứng dụng không rõ nguồn gốc".

Bản release được ký bằng khóa debug để cài được ngay. Muốn đưa lên CH Play thì phải tạo khóa ký riêng.

## Giọng đọc tiếng Việt

Điện thoại cần có giọng tiếng Việt. Nếu app báo thiếu, bấm **Cài giọng đọc tiếng Việt**, hoặc vào
*Cài đặt › Hỗ trợ tiếp cận › Chuyển văn bản thành giọng nói*, chọn *Dịch vụ Lời nói của Google* rồi tải gói Tiếng Việt.

## Giới hạn

- Đọc tốt chữ in rõ nét. Chữ viết tay sẽ kém chính xác.
- Nên chụp thẳng, đủ sáng, để chữ chiếm phần lớn khung hình.

## Mở bằng Android Studio

Mở thư mục này trong Android Studio (bản Koala trở lên). Nếu được hỏi tạo Gradle wrapper thì chọn Gradle 8.7.

