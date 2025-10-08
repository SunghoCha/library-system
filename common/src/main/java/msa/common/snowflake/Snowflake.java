package msa.common.snowflake;

import java.util.random.RandomGenerator;

public class Snowflake {
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private final long nodeId;
    private final long epochMillis; // 예: 2024-01-01T00:00:00Z
    private long lastMillis;
    private long sequence;

    public Snowflake(long nodeId, long epochMillis) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId out of range: " + nodeId);
        }
        this.nodeId = nodeId;
        this.epochMillis = epochMillis;
        this.lastMillis = epochMillis;
        this.sequence = 0L;
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis();

        if (now < lastMillis) {
            long diff = lastMillis - now;
            if (diff <= 5000L) {
                now = waitUntil(lastMillis);
            } else {
                throw new IllegalStateException("Clock moved backwards by " + diff + " ms");
            }
        }

        if (now == lastMillis) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                now = waitUntil(lastMillis + 1);
            }
        } else {
            sequence = 0;
        }

        lastMillis = now;
        long timestampPart = (now - epochMillis) << (NODE_ID_BITS + SEQUENCE_BITS);
        long nodePart = nodeId << SEQUENCE_BITS;
        return timestampPart | nodePart | sequence;
    }

    private long waitUntil(long targetMillis) {
        long ts = System.currentTimeMillis();
        while (ts < targetMillis) {
            Thread.onSpinWait();
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
