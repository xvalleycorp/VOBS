package io.vobs.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Khách hàng của ngân hàng. Tương ứng service domain <em>Party Reference Data Directory</em> của BIAN.
 *
 * @param customerId mã khách hàng nội bộ (CIF) của ngân hàng
 * @param type cá nhân hay tổ chức
 * @param fullName họ tên đầy đủ, có dấu
 * @param citizenId số định danh cá nhân, {@code null} với tổ chức
 * @param taxCode mã số thuế, {@code null} nếu không có
 * @param dateOfBirth ngày sinh, {@code null} với tổ chức
 */
public record Customer(
        String customerId,
        PartyType type,
        String fullName,
        CitizenId citizenId,
        String taxCode,
        LocalDate dateOfBirth) {

    public enum PartyType { INDIVIDUAL, ORGANISATION }

    public Customer {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fullName, "fullName");
    }

    /** Tên dạng không dấu, viết hoa, dùng khi gửi sang chuyển mạch. */
    public String switchName() {
        return VietnameseText.toSwitchName(fullName);
    }
}
