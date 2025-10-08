package msa.bookloan.adapter.out.lock.redis;

import msa.bookloan.application.port.out.lock.DistributedLock;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class StubDistributedLock implements DistributedLock {

    @Override
    public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit timeUnit) {
        // 로컬 개발 및 테스트 시에는 항상 성공했다고 가정
        System.out.println("STUB LOCK ACQUIRED FOR KEY: " + key);
        return true;
    }

    @Override
    public void unlock(String key) {
        // 아무것도 하지 않음
        System.out.println("STUB LOCK RELEASED FOR KEY: " + key);
    }
}
