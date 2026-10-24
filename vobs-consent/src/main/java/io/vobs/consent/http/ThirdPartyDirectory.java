package io.vobs.consent.http;

import io.vobs.consent.ThirdParty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Danh sách bên thứ ba đã ký hợp đồng kết nối Open API với ngân hàng, tra theo mã client. Loại tổ chức quyết định
 * nhóm API được phép dùng (Điều 5 Thông tư 64/2024/TT-NHNN).
 *
 * <p>Bản production đọc từ hệ thống quản lý đối tác của ngân hàng; bản này nạp từ cấu hình, cho sandbox và kiểm thử.
 */
public final class ThirdPartyDirectory {

    private final Map<String, ThirdParty> byClientId;

    public ThirdPartyDirectory(Iterable<ThirdParty> thirdParties) {
        Map<String, ThirdParty> map = new LinkedHashMap<>();
        for (ThirdParty thirdParty : thirdParties) {
            if (map.putIfAbsent(thirdParty.clientId(), thirdParty) != null) {
                throw new IllegalArgumentException("Trùng mã client " + thirdParty.clientId());
            }
        }
        this.byClientId = Map.copyOf(map);
    }

    /**
     * Đọc danh sách dạng {@code clientId:LOẠI}, cách nhau bởi dấu phẩy, ví dụ
     * {@code fintech-a:OTHER,wallet-b:PAYMENT_INTERMEDIARY,bank-c:BANK}.
     */
    public static ThirdPartyDirectory parse(String spec) {
        List<ThirdParty> thirdParties = new ArrayList<>();
        for (String item : spec.split(",")) {
            String trimmed = item.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.lastIndexOf(':');
            if (colon <= 0 || colon == trimmed.length() - 1) {
                throw new IllegalArgumentException("Cần dạng clientId:LOẠI, nhận được \"" + trimmed + "\"");
            }
            String clientId = trimmed.substring(0, colon).strip();
            ThirdParty.Type type = ThirdParty.Type.valueOf(trimmed.substring(colon + 1).strip());
            thirdParties.add(new ThirdParty(clientId, type));
        }
        return new ThirdPartyDirectory(thirdParties);
    }

    public Optional<ThirdParty> find(String clientId) {
        return Optional.ofNullable(byClientId.get(clientId));
    }
}
