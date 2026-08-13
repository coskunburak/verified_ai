package com.verifiedai.ai.application;

/**
 * Marker interface for typed capability inputs accepted by the AI gateway.
 *
 * Implementations remain capability-specific. The generic gateway must never
 * force product modules to exchange untyped maps or provider-specific payloads.
 */
public interface AiCapabilityRequest {
}
