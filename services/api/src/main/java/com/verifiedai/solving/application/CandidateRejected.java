package com.verifiedai.solving.application;

public final class CandidateRejected extends RuntimeException {
    private final String code;
    public CandidateRejected(String code) { super(code); this.code=code; }
    public String code() { return code; }
}
