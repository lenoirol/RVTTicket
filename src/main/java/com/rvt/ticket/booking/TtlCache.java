package com.rvt.ticket.booking;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Bộ nhớ đệm nhỏ có thời hạn cho dữ liệu chỉ đọc, ít đổi (danh mục tỉnh/địa điểm, số liệu trang chủ).
 * Giảm số lần hỏi DB và công render trên máy chủ yếu (Render Free 0,1 CPU). Dữ liệu trả về không sửa được.
 * Không dùng cho tồn chỗ / giá / đơn vé: những phần đó luôn đọc trực tiếp từ DB.
 */
final class TtlCache {
    private record Entry(Object value, long expiresAt) {}
    private final Map<Object, Entry> entries = new ConcurrentHashMap<>();
    private final long ttlNanos;
    TtlCache(Duration ttl) { this.ttlNanos = ttl.toNanos(); }

    @SuppressWarnings("unchecked")
    <T> T get(Object key, Supplier<T> loader) {
        long now = System.nanoTime();
        Entry e = entries.get(key);
        if (e != null && now - e.expiresAt() < 0) return (T) e.value();
        T value = readOnly(loader.get());
        if (entries.size() > 500) entries.clear(); // chặn tăng không giới hạn với khóa do người dùng nhập
        entries.put(key, new Entry(value, now + ttlNanos));
        return value;
    }
    void clear() { entries.clear(); }

    @SuppressWarnings("unchecked")
    private static <T> T readOnly(T value) {
        if (value instanceof List<?> list) {
            var copy = new ArrayList<Object>(list.size());
            for (Object o : list) copy.add(o instanceof Map<?, ?> m ? Collections.unmodifiableMap(m) : o);
            return (T) Collections.unmodifiableList(copy);
        }
        if (value instanceof Map<?, ?> m) return (T) Collections.unmodifiableMap(m);
        return value;
    }
}
