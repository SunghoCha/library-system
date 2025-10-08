package msa.common.snowflake;

public interface InstanceIdentity {
    long nodeId();       // Snowflake용, 0..1023
    String workerId();   // 클레임/리스 owner용, 문자열
}
