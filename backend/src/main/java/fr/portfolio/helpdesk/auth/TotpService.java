package fr.portfolio.helpdesk.auth;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

/** Codes à usage unique basés sur le temps (RFC 6238) : Google/Microsoft Authenticator, FreeOTP… */
@Service
public class TotpService {

    public record Enrollment(String secret, String otpauthUrl, String qrCode) {}

    private final DefaultSecretGenerator secrets = new DefaultSecretGenerator();
    private final CodeVerifier verifier;

    public TotpService() {
        DefaultCodeVerifier v = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        v.setAllowedTimePeriodDiscrepancy(1); // tolère ±30 s de décalage d'horloge
        this.verifier = v;
    }

    public Enrollment enroll(String accountEmail) {
        String secret = secrets.generate();
        QrData data = new QrData.Builder()
                .label(accountEmail).secret(secret).issuer("Helpdesk")
                .algorithm(HashingAlgorithm.SHA1).digits(6).period(30).build();
        try {
            var generator = new ZxingPngQrGenerator();
            String qr = getDataUriForImage(generator.generate(data), generator.getImageMimeType());
            return new Enrollment(secret, data.getUri(), qr);
        } catch (QrGenerationException e) {
            throw new IllegalStateException("Génération du QR code impossible", e);
        }
    }

    public boolean isValid(String secret, String code) {
        return secret != null && code != null && verifier.isValidCode(secret, code);
    }
}
