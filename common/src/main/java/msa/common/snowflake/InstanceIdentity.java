package msa.common.snowflake;

public interface InstanceIdentity {
    long nodeId();       // Snowflake용
    String workerId();   // 클레임/리스 owner용
}
