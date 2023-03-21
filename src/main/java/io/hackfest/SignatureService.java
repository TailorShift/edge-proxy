package io.hackfest;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Mac;
import javax.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

@ApplicationScoped
public class SignatureService {
    private final static String DEVICE_ID = UUID.randomUUID().toString();
    private final static String KEYSTORE_PATH = "keystore.jks";
    private final static String KEYSTORE_PASSWORD = "banana";

    private final Mac mac = Mac.getInstance("HmacSHA256");
    private final KeyStore keyStore;
    private final Key key;

    public SignatureService() throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
        Security.addProvider(new BouncyCastleProvider());
        keyStore = KeyStore.Builder.newInstance(Paths.get(KEYSTORE_PATH).toFile(), new KeyStore.PasswordProtection(KEYSTORE_PASSWORD.toCharArray())).getKeyStore();
        key = keyStore.getKey("certificate", KEYSTORE_PASSWORD.toCharArray());
    }

    public CryptoHeaders buildCryptoHeaders() throws InvalidKeyException {
        long timestamp = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond();
        String signatureBase = DEVICE_ID + "::" + timestamp;

        mac.init(key);
        byte[] signature = mac.doFinal(signatureBase.getBytes(StandardCharsets.UTF_8));

        return new CryptoHeaders(DEVICE_ID, timestamp, signature);
    }
}
