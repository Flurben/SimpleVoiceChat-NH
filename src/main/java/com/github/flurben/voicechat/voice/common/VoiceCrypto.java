package com.github.flurben.voicechat.voice.common;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-128-GCM with a random 12 byte IV per packet. The tag authenticates the sender, so no extra secret is sent. */
public final class VoiceCrypto {

    public static final int KEY_SIZE = 16;
    public static final int IV_SIZE = 12;
    public static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ThreadLocal<Cipher> CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance("AES/GCM/NoPadding");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    });

    private VoiceCrypto() {}

    public static byte[] generateKey() {
        byte[] key = new byte[KEY_SIZE];
        RANDOM.nextBytes(key);
        return key;
    }

    /** @return IV followed by the cipher text and tag */
    public static byte[] encrypt(byte[] key, byte[] plain, int off, int len) throws GeneralSecurityException {
        byte[] iv = new byte[IV_SIZE];
        RANDOM.nextBytes(iv);
        Cipher c = CIPHER.get();
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] out = new byte[IV_SIZE + c.getOutputSize(len)];
        System.arraycopy(iv, 0, out, 0, IV_SIZE);
        c.doFinal(plain, off, len, out, IV_SIZE);
        return out;
    }

    public static byte[] decrypt(byte[] key, byte[] data, int off, int len) throws GeneralSecurityException {
        if (len < IV_SIZE + TAG_BITS / 8) {
            throw new GeneralSecurityException("Packet too short");
        }
        Cipher c = CIPHER.get();
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, data, off, IV_SIZE));
        return c.doFinal(data, off + IV_SIZE, len - IV_SIZE);
    }
}
