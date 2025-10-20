package msa.common.snowflake;

import org.springframework.beans.factory.annotation.Value;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;

public class DefaultInstanceIdentity implements InstanceIdentity {

    private final long nodeId;
    private final String workerId;

    public DefaultInstanceIdentity(@Value("${snowflake.node-id}")long nodeId) {
        this.nodeId = nodeId;
        this.workerId = buildWorkerId(nodeId);
    }

    @Override
    public long nodeId() {
        return nodeId;
    }

    @Override
    public String workerId() {
        return workerId;
    }

    private String buildWorkerId(long nodeId) {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignore) { }
        long startedAt = Instant.now().toEpochMilli();
        // host-nodeId-bootTime-random(짧게)
        String shortRand = UUID.randomUUID().toString().substring(0, 8);
        return host + "-" + nodeId + "-" + startedAt + "-" + shortRand;
    }
}
