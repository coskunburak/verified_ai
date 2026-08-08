package com.verifiedai.identity.infrastructure.configuration;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

record RsaKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
}
