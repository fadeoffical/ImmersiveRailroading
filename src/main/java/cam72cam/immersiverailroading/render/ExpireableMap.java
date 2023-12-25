package cam72cam.immersiverailroading.render;

import java.util.*;

public class ExpireableMap<K, V> {

    private final Map<K, V> map = new HashMap<>();
    private final Map<K, Long> mapUsage = new HashMap<>();
    private long lastTime = timeS();

    public V get(K key) {
        synchronized (this) {
            if (this.lastTime + this.lifespan() < timeS()) {
                // clear unused
                Set<K> ks = new HashSet<>(this.map.keySet());
                for (K dk : ks) {
                    if (dk != key && this.mapUsage.get(dk) + this.lifespan() < timeS()) {
                        this.onRemove(dk, this.map.get(dk));
                        this.map.remove(dk);
                        this.mapUsage.remove(dk);
                    }
                }
                this.lastTime = timeS();
            }


            if (this.map.containsKey(key)) {
                if (this.sliding()) {
                    this.mapUsage.put(key, timeS());
                }
                return this.map.get(key);
            }
            return null;
        }
    }

    public int lifespan() {
        return 10;
    }

    private static long timeS() {
        return System.currentTimeMillis() / 1000L;
    }

    public void onRemove(K key, V value) {

    }

    public boolean sliding() {
        return true;
    }

    public void put(K key, V displayList) {
        synchronized (this) {
            if (displayList == null) {
                this.remove(key);
            } else {
                this.mapUsage.put(key, timeS());
                this.map.put(key, displayList);
            }
        }
    }

    public void remove(K key) {
        synchronized (this) {
            if (this.map.containsKey(key)) {
                this.onRemove(key, this.map.get(key));
                this.map.remove(key);
                this.mapUsage.remove(key);
            }
        }
    }

    public Collection<V> values() {
        synchronized (this) {
            if (this.lastTime + this.lifespan() < timeS()) {
                // clear unused
                Set<K> ks = new HashSet<>(this.map.keySet());
                for (K dk : ks) {
                    if (this.mapUsage.get(dk) + this.lifespan() < timeS()) {
                        this.onRemove(dk, this.map.get(dk));
                        this.map.remove(dk);
                        this.mapUsage.remove(dk);
                    }
                }
                this.lastTime = timeS();
            }

            return this.map.values();
        }
    }
}
