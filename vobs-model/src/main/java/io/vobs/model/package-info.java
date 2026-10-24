/**
 * Mô hình dữ liệu chuẩn của VOBS.
 *
 * <p>Các thực thể bám theo service domain của BIAN để ngân hàng dễ ánh xạ với kiến trúc nội bộ. Mọi module khác
 * (Integration Hub, consent, gateway) trao đổi dữ liệu qua các kiểu trong gói này, không dùng trực tiếp cấu trúc
 * bảng của core.
 */
package io.vobs.model;
