package io.vobs.switching;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Danh bạ các adapter chuyển mạch đang dùng, tra theo {@link SwitchAdapter#id()}. */
public final class SwitchRegistry {

    private final Map<String, SwitchAdapter> adapters = new ConcurrentHashMap<>();

    /** Nạp mọi adapter khai báo qua {@link ServiceLoader} trên classpath. */
    public static SwitchRegistry loadInstalled() {
        SwitchRegistry registry = new SwitchRegistry();
        ServiceLoader.load(SwitchAdapter.class).forEach(registry::register);
        return registry;
    }

    public SwitchRegistry register(SwitchAdapter adapter) {
        SwitchAdapter existing = adapters.putIfAbsent(adapter.id(), adapter);
        if (existing != null) {
            throw new IllegalStateException("Đã có adapter với id " + adapter.id());
        }
        return this;
    }

    public SwitchAdapter get(String id) {
        SwitchAdapter adapter = adapters.get(id);
        if (adapter == null) {
            throw new NoSuchElementException("Chưa đăng ký chuyển mạch " + id + "; đang có: " + adapters.keySet());
        }
        return adapter;
    }

    public Set<String> ids() {
        return Set.copyOf(adapters.keySet());
    }
}
