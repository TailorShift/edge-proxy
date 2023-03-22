package io.hackfest;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.enterprise.context.ApplicationScoped;
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

@ApplicationScoped
public class SignatureService {
    private final static String DEVICE_ID = "Shop1_dev1";
    private final static String KEYSTORE_PATH = "keystore.jks";
    private final static String KEYSTORE_PASSWORD = "banana";

    private final Signature sig = Signature.getInstance("SHA256withRSA");
    private final KeyStore keyStore;
    private final PrivateKey key;

    public SignatureService() throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
        Security.addProvider(new BouncyCastleProvider());
        keyStore = KeyStore.Builder.newInstance(Paths.get(KEYSTORE_PATH).toFile(), new KeyStore.PasswordProtection(KEYSTORE_PASSWORD.toCharArray())).getKeyStore();
        key = (PrivateKey) keyStore.getKey("certificate", KEYSTORE_PASSWORD.toCharArray());
    }

    public CryptoHeaders buildCryptoHeaders() throws InvalidKeyException, SignatureException {
        long timestamp = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond();
        String signatureBase = DEVICE_ID + "::" + timestamp;

        sig.initSign(key);
        sig.update(signatureBase.getBytes(StandardCharsets.UTF_8));
        byte[] signature = sig.sign();

        return new CryptoHeaders(DEVICE_ID, timestamp, signature);
    }
}
