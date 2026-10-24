package io.vobs.consent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lưu trữ consent. {@code jdbc.PostgresConsentRepository} lưu vào PostgreSQL; {@link #inMemory()} dùng cho kiểm thử
 * và sandbox.
 */
public interface ConsentRepository {

    void save(Consent consent);

    Optional<Consent> find(String consentId);

    /** Mọi consent đã gắn với khách hàng này, mới nhất trước. */
    List<Consent> findByCustomer(String customerId);

    static ConsentRepository inMemory() {
        Map<String, Consent> store = new ConcurrentHashMap<>();
        return new ConsentRepository() {
            @Override
            public void save(Consent consent) {
                store.put(consent.consentId(), consent);
            }

            @Override
            public Optional<Consent> find(String consentId) {
                return Optional.ofNullable(store.get(consentId));
            }

            @Override
            public List<Consent> findByCustomer(String customerId) {
                return store.values().stream()
                        .filter(consent -> customerId.equals(consent.customerId()))
                        .sorted(Comparator.comparing(Consent::createdAt).reversed())
                        .toList();
            }
        };
    }
}
