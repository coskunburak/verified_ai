package com.verifiedai.mastery.domain;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class InMemoryMasteryStateLockStore implements MasteryStateLockStore {

    private final ConcurrentHashMap<OwnerKey, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public LockHandle lock(OwnerKey owner) {
        ReentrantLock lock = locks.computeIfAbsent(owner, k -> new ReentrantLock());
        lock.lock();
        return lock::unlock;
    }
}
