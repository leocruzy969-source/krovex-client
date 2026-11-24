import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Signs launcher updates so the Krovex Launcher's starter ("Boot") only ever runs updates made by us.
 *
 *   java UpdateSigner.java gen  <private-key-file> <public-key-file>   one time: makes an RSA-3072 key pair (base64 files)
 *   java UpdateSigner.java sign <private-key-file> <jar> [<sig-file>]  writes <jar>.sig (base64 SHA256withRSA over the jar bytes)
 *
 * The PRIVATE key never goes anywhere near GitHub. The public key is compiled into Boot.
 */
public final class UpdateSigner {
    public static void main(String[] args) throws Exception {
        if (args.length >= 3 && args[0].equals("gen")) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            KeyPair pair = generator.generateKeyPair();
            Files.writeString(Path.of(args[1]), Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
            Files.writeString(Path.of(args[2]), Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()), StandardCharsets.US_ASCII);
            System.out.println("keys written");
        } else if (args.length >= 3 && args[0].equals("sign")) {
            byte[] keyBytes = Base64.getDecoder().decode(Files.readString(Path.of(args[1])).strip());
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(Files.readAllBytes(Path.of(args[2])));
            Path out = args.length > 3 ? Path.of(args[3]) : Path.of(args[2] + ".sig");
            Files.writeString(out, Base64.getEncoder().encodeToString(signature.sign()), StandardCharsets.US_ASCII);
            System.out.println("signed " + args[2] + " -> " + out);
        } else {
            System.err.println("usage: gen <private> <public> | sign <private> <jar> [sig]");
            System.exit(2);
        }
    }
}
