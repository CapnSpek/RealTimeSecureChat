package server.utils;

import java.util.concurrent.ConcurrentHashMap;

public class BiDirectionalMap<K, V> {
    private final ConcurrentHashMap<K, V> forwardMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<V, K> reverseMap = new ConcurrentHashMap<>();
    private final Object lock = new Object();  // Lock object for synchronized updates

    public void put(K key, V value) {
        synchronized (lock) {
            // Remove existing entries to maintain one-to-one mapping
            if (forwardMap.containsKey(key)) {
                V oldValue = forwardMap.get(key);
                reverseMap.remove(oldValue);
            }
            if (reverseMap.containsKey(value)) {
                K oldKey = reverseMap.get(value);
                forwardMap.remove(oldKey);
            }
            // Add new entries to both maps
            forwardMap.put(key, value);
            reverseMap.put(value, key);
        }
    }

    public V getValue(K key) {
        return forwardMap.get(key);
    }

    public K getKey(V value) {
        return reverseMap.get(value);
    }

    public void removeByKey(K key) {
        synchronized (lock) {
            V value = forwardMap.remove(key);
            if (value != null) {
                reverseMap.remove(value);
            }
        }
    }

    public void removeByValue(V value) {
        synchronized (lock) {
            K key = reverseMap.remove(value);
            if (key != null) {
                forwardMap.remove(key);
            }
        }
    }

    public boolean containsKey(K key) {
        return forwardMap.containsKey(key);
    }

    public boolean containsValue(V value) {
        return reverseMap.containsKey(value);
    }

    public int size() {
        return forwardMap.size();
    }

    public void clear() {
        synchronized (lock) {
            forwardMap.clear();
            reverseMap.clear();
        }
    }
}
