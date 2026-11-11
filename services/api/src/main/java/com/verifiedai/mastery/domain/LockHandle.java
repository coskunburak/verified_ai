package com.verifiedai.mastery.domain;

public interface LockHandle extends AutoCloseable {
    @Override
    void close();
}
