package com.verifiedai.mastery;

import java.util.UUID;
/** Synchronous notification inside the producer's owner-locked transaction. Not a new observation. */
public record MasteryEvidenceChanged(UUID owner) { }
