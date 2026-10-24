package io.vobs.consent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lưu trữ consent. Bản production dùng PostgreSQL sao chép đồng bộ (RPO gần 0); {@link #inMemory()} dùng cho
 * kiểm thử và sandbox.
 */
public interface ConsentRepository {

    void save(Consent consent);

    Optional<Consent> find(String consentId);

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
        };
    }
}
