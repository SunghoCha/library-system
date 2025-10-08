package msa.bookloan.application.port.out.lock;

import java.util.concurrent.TimeUnit;

public interface DistributedLock {
    boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit timeUnit)
            throws InterruptedException;
    void unlock(String key);
}
