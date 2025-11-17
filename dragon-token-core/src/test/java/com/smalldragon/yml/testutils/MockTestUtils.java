package com.smalldragon.yml.testutils;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 共享的Mock测试工具类，提供通用的Mock实现
 * 避免在多个测试类中重复相同的Mock代码
 */
public class MockTestUtils {

    /**
     * Mock Redis Connection Factory for testing
     */
    public static class MockRedisConnectionFactory implements RedisConnectionFactory {
        private final Map<String, Object> dataStore = new ConcurrentHashMap<>();

        @Override
        public RedisConnection getConnection() {
            return new MockRedisConnection(dataStore);
        }

        @Override
        public RedisClusterConnection getClusterConnection() {
            return null;
        }

        @Override
        public boolean getConvertPipelineAndTxResults() {
            return false;
        }

        @Override
        public RedisSentinelConnection getSentinelConnection() {
            return null;
        }

        @Override
        public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
            return null;
        }
    }

    /**
     * Mock Redis Connection with basic operations
     */
    public static class MockRedisConnection implements RedisConnection {
        private final Map<String, Object> dataStore;
        
        @Override
        public RedisSentinelConnection getSentinelConnection() {
            return null;
        }

        public MockRedisConnection(Map<String, Object> dataStore) {
            this.dataStore = dataStore;
        }

        @Override
        public Boolean exists(byte[] key) {
            return dataStore.containsKey(new String(key));
        }

        @Override
        public Long exists(byte[]... keys) {
            return Arrays.stream(keys)
                    .mapToLong(key -> dataStore.containsKey(new String(key)) ? 1L : 0L)
                    .sum();
        }

        @Override
        public Long del(byte[]... keys) {
            return Arrays.stream(keys)
                    .mapToLong(key -> dataStore.remove(new String(key)) != null ? 1L : 0L)
                    .sum();
        }

        @Override
        public Boolean expire(byte[] key, long seconds) {
            return dataStore.containsKey(new String(key));
        }

        @Override
        public Boolean pExpire(byte[] key, long millis) {
            return dataStore.containsKey(new String(key));
        }

        @Override
        public Long ttl(byte[] key) {
            return dataStore.containsKey(new String(key)) ? 3600L : -1L;
        }

        @Override
        public Long pTtl(byte[] key) {
            return dataStore.containsKey(new String(key)) ? 3600000L : -1L;
        }

        @Override
        public Duration idletime(byte[] key) {
            // Mock implementation - return mock idle time
            return dataStore.containsKey(new String(key)) ? Duration.ofSeconds(1800) : null;
        }

        @Override
        public ValueEncoding encodingOf(byte[] key) {
            // Mock implementation - return default encoding
            return new ValueEncoding() {
                @Override
                public String type() {
                    return "raw";
                }

                @Override
                public int memory() {
                    return 0;
                }

                @Override
                public int length() {
                    return 0;
                }
            };
        }

        @Override
        public void restore(byte[] key, long ttl, byte[] serializedValue, boolean replace) {
            // Mock implementation - store the value
            dataStore.put(new String(key), new String(serializedValue));
        }

        @Override
        public Boolean move(byte[] key, int dbIndex) {
            return false;
        }

        @Override
        public void flushDb() {
            dataStore.clear();
        }

        @Override
        public void flushAll() {
            dataStore.clear();
        }

        @Override
        public Long dbSize() {
            return (long) dataStore.size();
        }

        @Override
        public String ping() {
            return "PONG";
        }

        @Override
        public void select(int dbIndex) {
            // Mock implementation
        }

        @Override
        public byte[] echo(byte[] message) {
            return message;
        }

        @Override
        public Boolean set(byte[] key, byte[] value) {
            dataStore.put(new String(key), new String(value));
            return true;
        }

        @Override
        public Boolean setNX(byte[] key, byte[] value) {
            String keyStr = new String(key);
            if (!dataStore.containsKey(keyStr)) {
                dataStore.put(keyStr, new String(value));
                return true;
            }
            return false;
        }

        @Override
        public Boolean setEx(byte[] key, long seconds, byte[] value) {
            dataStore.put(new String(key), new String(value));
            return true;
        }

        @Override
        public Boolean pSetEx(byte[] key, long milliseconds, byte[] value) {
            dataStore.put(new String(key), new String(value));
            return true;
        }

        @Override
        public byte[] get(byte[] key) {
            Object value = dataStore.get(new String(key));
            return value != null ? value.toString().getBytes() : null;
        }

        @Override
        public byte[] getSet(byte[] key, byte[] value) {
            String keyStr = new String(key);
            Object oldValue = dataStore.put(keyStr, new String(value));
            return oldValue != null ? oldValue.toString().getBytes() : null;
        }

        @Override
        public List<byte[]> mGet(byte[]... keys) {
            return Arrays.stream(keys)
                    .map(key -> {
                        Object value = dataStore.get(new String(key));
                        return value != null ? value.toString().getBytes() : null;
                    })
                    .collect(Collectors.toList());
        }

        @Override
        public Boolean mSet(Map<byte[], byte[]> tuple) {
            tuple.forEach((key, value) -> dataStore.put(new String(key), new String(value)));
            return true;
        }

        @Override
        public Boolean mSetNX(Map<byte[], byte[]> tuple) {
            boolean allNew = tuple.keySet().stream()
                    .noneMatch(key -> dataStore.containsKey(new String(key)));
            if (allNew) {
                tuple.forEach((key, value) -> dataStore.put(new String(key), new String(value)));
                return true;
            }
            return false;
        }

        // Hash operations
        @Override
        public Boolean hSet(byte[] key, byte[] field, byte[] value) {
            String keyStr = new String(key);
            @SuppressWarnings("unchecked")
            Map<String, String> hash = (Map<String, String>) dataStore.computeIfAbsent(keyStr, k -> new HashMap<>());
            String oldValue = hash.put(new String(field), new String(value));
            return oldValue == null;
        }

        @Override
        public byte[] hGet(byte[] key, byte[] field) {
            Object value = dataStore.get(new String(key));
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> hash = (Map<String, String>) value;
                String fieldValue = hash.get(new String(field));
                return fieldValue != null ? fieldValue.getBytes() : null;
            }
            return null;
        }

        @Override
        public Boolean hExists(byte[] key, byte[] field) {
            Object value = dataStore.get(new String(key));
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> hash = (Map<String, String>) value;
                return hash.containsKey(new String(field));
            }
            return false;
        }

        @Override
        public Long hDel(byte[] key, byte[]... fields) {
            Object value = dataStore.get(new String(key));
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> hash = (Map<String, String>) value;
                return Arrays.stream(fields)
                        .mapToLong(field -> hash.remove(new String(field)) != null ? 1L : 0L)
                        .sum();
            }
            return 0L;
        }

        @Override
        public Map<byte[], byte[]> hGetAll(byte[] key) {
            Object value = dataStore.get(new String(key));
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> hash = (Map<String, String>) value;
                return hash.entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> entry.getKey().getBytes(),
                                entry -> entry.getValue().getBytes()
                        ));
            }
            return Collections.emptyMap();
        }

        // 其他必需的方法使用默认实现
        @Override
        public void close() {
            // Mock implementation - 不抛出异常
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public RedisCommands getNativeConnection() {
            return this;
        }

        @Override
        public boolean isQueueing() {
            return false;
        }

        @Override
        public boolean isPipelined() {
            return false;
        }

        @Override
        public void openPipeline() {
            // Mock implementation
        }

        @Override
        public List<Object> closePipeline() {
            return Collections.emptyList();
        }

        // 简化的其他操作实现
        @Override public Long incr(byte[] key) { return 1L; }
        @Override public Long incrBy(byte[] key, long value) { return value; }
        @Override public Double incrBy(byte[] key, double value) { return value; }
        @Override public Long decr(byte[] key) { return -1L; }
        @Override public Long decrBy(byte[] key, long value) { return -value; }
        @Override public Long append(byte[] key, byte[] value) { return 0L; }
        @Override public byte[] getRange(byte[] key, long start, long end) { return new byte[0]; }
        @Override public void setRange(byte[] key, byte[] value, long offset) {}
        @Override public Boolean getBit(byte[] key, long offset) { return false; }
        @Override public Boolean setBit(byte[] key, long offset, boolean value) { return false; }
        @Override public Long bitCount(byte[] key) { return 0L; }
        @Override public Long bitCount(byte[] key, long start, long end) { return 0L; }
        @Override public Long bitOp(BitOperation op, byte[] destination, byte[]... keys) { return 0L; }
        @Override public Long strLen(byte[] key) { return 0L; }
        
        // List operations - 简化实现
        @Override public Long rPush(byte[] key, byte[]... values) { return 0L; }
        @Override public Long lPush(byte[] key, byte[]... values) { return 0L; }
        @Override public Long rPushX(byte[] key, byte[] value) { return 0L; }
        @Override public Long lPushX(byte[] key, byte[] value) { return 0L; }
        @Override public Long lLen(byte[] key) { return 0L; }
        @Override public List<byte[]> lRange(byte[] key, long start, long end) { return Collections.emptyList(); }
        @Override public void lTrim(byte[] key, long start, long end) {}
        @Override public byte[] lIndex(byte[] key, long index) { return null; }
        @Override public Long lInsert(byte[] key, Position where, byte[] pivot, byte[] value) { return 0L; }
        @Override public void lSet(byte[] key, long index, byte[] value) {}
        @Override public Long lRem(byte[] key, long count, byte[] value) { return 0L; }
        @Override public byte[] lPop(byte[] key) { return null; }
        @Override public byte[] rPop(byte[] key) { return null; }
        @Override public List<byte[]> bLPop(int timeout, byte[]... keys) { return Collections.emptyList(); }
        @Override public List<byte[]> bRPop(int timeout, byte[]... keys) { return Collections.emptyList(); }
        @Override public byte[] rPopLPush(byte[] srcKey, byte[] dstKey) { return null; }
        @Override public byte[] bRPopLPush(int timeout, byte[] srcKey, byte[] dstKey) { return null; }
        
        // Set operations - 简化实现
        @Override public Long sAdd(byte[] key, byte[]... values) { return 0L; }
        @Override public Long sRem(byte[] key, byte[]... values) { return 0L; }
        @Override public byte[] sPop(byte[] key) { return null; }
        @Override public List<byte[]> sPop(byte[] key, long count) { return Collections.emptyList(); }
        @Override public Boolean sMove(byte[] srcKey, byte[] destKey, byte[] value) { return false; }
        @Override public Long sCard(byte[] key) { return 0L; }
        @Override public Boolean sIsMember(byte[] key, byte[] value) { return false; }
        @Override public Set<byte[]> sInter(byte[]... keys) { return Collections.emptySet(); }
        @Override public Long sInterStore(byte[] destKey, byte[]... keys) { return 0L; }
        @Override public Set<byte[]> sUnion(byte[]... keys) { return Collections.emptySet(); }
        @Override public Long sUnionStore(byte[] destKey, byte[]... keys) { return 0L; }
        @Override public Set<byte[]> sDiff(byte[]... keys) { return Collections.emptySet(); }
        @Override public Long sDiffStore(byte[] destKey, byte[]... keys) { return 0L; }
        @Override public Set<byte[]> sMembers(byte[] key) { return Collections.emptySet(); }
        @Override public byte[] sRandMember(byte[] key) { return null; }
        @Override public List<byte[]> sRandMember(byte[] key, long count) { return Collections.emptyList(); }
        @Override public Cursor<byte[]> sScan(byte[] key, ScanOptions options) {
            return new ConvertingCursor<>(Collections.emptyIterator(), Function.identity());
        }
        
        // 其他未实现的方法使用默认值
        @Override public Long hLen(byte[] key) { return 0L; }
        @Override public Set<byte[]> hKeys(byte[] key) { return Collections.emptySet(); }
        @Override public List<byte[]> hVals(byte[] key) { return Collections.emptyList(); }
        @Override public void hMSet(byte[] key, Map<byte[], byte[]> hashes) {}
        @Override public List<byte[]> hMGet(byte[] key, byte[]... fields) { return Collections.emptyList(); }
        @Override public Long hIncrBy(byte[] key, byte[] field, long delta) { return delta; }
        @Override public Double hIncrBy(byte[] key, byte[] field, double delta) { return delta; }
        @Override public Boolean hSetNX(byte[] key, byte[] field, byte[] value) { return false; }
        @Override public Cursor<Map.Entry<byte[], byte[]>> hScan(byte[] key, ScanOptions options) {
            return new ConvertingCursor<>(Collections.emptyIterator(), Function.identity());
        }
        @Override public Long hStrLen(byte[] key, byte[] field) { return 0L; }
        
        @Override
        public Object execute(String command, byte[]... args) {
            // Mock implementation for execute command
            return null;
        }
        
        @Override
        public Long refcount(byte[] key) {
            return 0L;
        }
    }

    /**
     * Mock Session Repository for testing
     */
    public static class MockSessionRepository implements SessionRepository<MockSession> {
        private final Map<String, MockSession> sessions = new ConcurrentHashMap<>();

        @Override
        public MockSession createSession() {
            return new MockSession();
        }

        @Override
        public void save(MockSession session) {
            sessions.put(session.getId(), session);
        }

        @Override
        public MockSession findById(String id) {
            return sessions.get(id);
        }

        @Override
        public void deleteById(String id) {
            sessions.remove(id);
        }
    }

    /**
     * Mock Session implementation
     */
    public static class MockSession implements Session {
        private final String id = UUID.randomUUID().toString();
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private Instant creationTime = Instant.now();
        private Instant lastAccessedTime = Instant.now();
        private Duration maxInactiveInterval = Duration.ofMinutes(30);
        private boolean expired = false;

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String changeSessionId() {
            return getId();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getAttribute(String attributeName) {
            return (T) attributes.get(attributeName);
        }

        @Override
        public Set<String> getAttributeNames() {
            return attributes.keySet();
        }

        @Override
        public void setAttribute(String attributeName, Object attributeValue) {
            attributes.put(attributeName, attributeValue);
        }

        @Override
        public void removeAttribute(String attributeName) {
            attributes.remove(attributeName);
        }

        @Override
        public Instant getCreationTime() {
            return creationTime;
        }

        @Override
        public void setLastAccessedTime(Instant lastAccessedTime) {
            this.lastAccessedTime = lastAccessedTime;
        }

        @Override
        public Instant getLastAccessedTime() {
            return lastAccessedTime;
        }

        @Override
        public void setMaxInactiveInterval(Duration interval) {
            this.maxInactiveInterval = interval;
        }

        @Override
        public Duration getMaxInactiveInterval() {
            return maxInactiveInterval;
        }

        @Override
        public boolean isExpired() {
            return expired || Instant.now().isAfter(lastAccessedTime.plus(maxInactiveInterval));
        }
    }

    /**
     * 简化的Cursor实现
     */
    private static class ConvertingCursor<T> implements Cursor<T> {
        private final Iterator<T> iterator;
        private final Function<T, T> converter;

        public ConvertingCursor(Iterator<T> iterator, Function<T, T> converter) {
            this.iterator = iterator;
            this.converter = converter;
        }

        @Override
        public long getCursorId() {
            return 0;
        }
        
        @Override
        public long getPosition() {
            return 0;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public T next() {
            return converter.apply(iterator.next());
        }
        
        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public Cursor<T> open() {
            // Mock implementation - cursor is already open
            return this;
        }

        @Override
        public void close() {
            // Mock implementation
        }
    }
}