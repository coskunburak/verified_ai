package com.verifiedai.mastery.domain;

public interface MasteryStateLockStore {
    LockHandle lock(OwnerKey owner);
}
