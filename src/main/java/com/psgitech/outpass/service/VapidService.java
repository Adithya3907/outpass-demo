package com.psgitech.outpass.service;

import com.psgitech.outpass.domain.AppSetting;
import com.psgitech.outpass.repo.Repos;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/** VAPID key pair used to sign Web Push messages. Generated once and kept in the database. */
@Service
@RequiredArgsConstructor
@Slf4j
public class VapidService {
    private static final String PUB = "vapid.public";
    private static final String PRIV = "vapid.private";

    private final Repos.AppSettings settings;
    private volatile String publicKey;
    private volatile String privateKey;

    @PostConstruct
    public synchronized void init() {
        var pub = settings.findById(PUB);
        var priv = settings.findById(PRIV);
        if (pub.isPresent() && priv.isPresent()) {
            publicKey = pub.get().getValue();
            privateKey = priv.get().getValue();
            return;
        }
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = g.generateKeyPair();
            ECPublicKey pk = (ECPublicKey) kp.getPublic();
            ECPrivateKey sk = (ECPrivateKey) kp.getPrivate();
            byte[] x = fixed32(pk.getW().getAffineX());
            byte[] y = fixed32(pk.getW().getAffineY());
            byte[] point = new byte[65];
            point[0] = 0x04;
            System.arraycopy(x, 0, point, 1, 32);
            System.arraycopy(y, 0, point, 33, 32);
            Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
            publicKey = enc.encodeToString(point);
            privateKey = enc.encodeToString(fixed32(sk.getS()));
            settings.save(new AppSetting(PUB, publicKey));
            settings.save(new AppSetting(PRIV, privateKey));
            log.info("Generated a new VAPID key pair for Web Push.");
        } catch (Exception e) {
            throw new IllegalStateException("Could not create VAPID keys", e);
        }
    }

    public String getPublicKey() { return publicKey; }
    public String getPrivateKey() { return privateKey; }

    private static byte[] fixed32(BigInteger v) {
        byte[] raw = v.toByteArray();
        byte[] out = new byte[32];
        int n = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - n, out, 32 - n, n);
        return out;
    }
}
