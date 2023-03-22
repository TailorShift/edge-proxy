package io.hackfest;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Singleton
public class SignatureService {

    @ConfigProperty(name = "device.serial")
    String deviceSerial;

    @ConfigProperty(name = "device.keystorePath")
    String keystorePath;
    private final static String KEYSTORE_PASSWORD = "banana";

    private Signature sig;
    private KeyStore keyStore;
    private PrivateKey key;

    @PostConstruct
    public void init() throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
        Security.addProvider(new BouncyCastleProvider());
        sig = Signature.getInstance("SHA256withRSA");
        keyStore = KeyStore.Builder.newInstance(Paths.get(keystorePath).toFile(), new KeyStore.PasswordProtection(KEYSTORE_PASSWORD.toCharArray())).getKeyStore();
        key = (PrivateKey) keyStore.getKey("certificate", KEYSTORE_PASSWORD.toCharArray());
    }

    public CryptoHeaders buildCryptoHeaders() throws InvalidKeyException, SignatureException {
        long timestamp = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond();
        String signatureBase = deviceSerial + "::" + timestamp;

        sig.initSign(key);
        sig.update(signatureBase.getBytes(StandardCharsets.UTF_8));
        byte[] signature = sig.sign();

        return new CryptoHeaders(deviceSerial, timestamp, signature);
    }
}
