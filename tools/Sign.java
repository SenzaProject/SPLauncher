import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.*;

/** Align (4 byte) + sign (v2/v3) + verify. Pengganti zipalign + apksigner. */
public class Sign {
    public static void main(String[] a) throws Exception {
        if (a[0].equals("sign")) {
            File in = new File(a[1]), out = new File(a[2]);
            File aligned = new File(out.getPath() + ".aligned");
            align(in, aligned);
            char[] pw = a[4].toCharArray();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream is = new FileInputStream(a[3])) { ks.load(is, pw); }
            String alias = a[5];
            PrivateKey key = (PrivateKey) ks.getKey(alias, pw);
            List<X509Certificate> chain = new ArrayList<>();
            for (java.security.cert.Certificate c : ks.getCertificateChain(alias)) chain.add((X509Certificate) c);
            ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("sp", key, chain).build();
            new ApkSigner.Builder(Collections.singletonList(cfg))
                .setInputApk(aligned).setOutputApk(out)
                .setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(true)
                .setMinSdkVersion(34).build().sign();
            aligned.delete();
            System.out.println("Signed: " + out);
        } else if (a[0].equals("verify")) {
            ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[1])).build().verify();
            System.out.println("verified=" + r.isVerified() + " v2=" + r.isVerifiedUsingV2Scheme()
                + " v3=" + r.isVerifiedUsingV3Scheme());
            for (ApkVerifier.IssueWithParams e : r.getErrors()) System.out.println("ERR " + e);
            for (ApkVerifier.IssueWithParams e : r.getWarnings()) System.out.println("WARN " + e);
            if (!r.isVerified()) System.exit(1);
        } else if (a[0].equals("checkalign")) {
            checkAlign(new File(a[1]));
        }
    }

    static class Count extends FilterOutputStream {
        long n;
        Count(OutputStream o) { super(o); }
        @Override public void write(int b) throws IOException { out.write(b); n++; }
        @Override public void write(byte[] b, int o, int l) throws IOException { out.write(b, o, l); n += l; }
    }

    static void align(File in, File out) throws Exception {
        try (ZipFile zf = new ZipFile(in); Count c = new Count(new BufferedOutputStream(new FileOutputStream(out)));
             ZipOutputStream zo = new ZipOutputStream(c)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry src = en.nextElement();
                if (src.isDirectory()) continue;
                byte[] data;
                try (InputStream is = zf.getInputStream(src)) { data = is.readAllBytes(); }
                ZipEntry dst = new ZipEntry(src.getName());
                boolean stored = src.getName().equals("resources.arsc") || src.getMethod() == ZipEntry.STORED;
                if (stored) {
                    CRC32 crc = new CRC32(); crc.update(data);
                    dst.setMethod(ZipEntry.STORED); dst.setSize(data.length);
                    dst.setCompressedSize(data.length); dst.setCrc(crc.getValue());
                    int nameLen = src.getName().getBytes(StandardCharsets.UTF_8).length;
                    long off = c.n + 30 + nameLen;
                    int pad = (int) ((4 - (off % 4)) % 4);
                    if (pad > 0) {
                        byte[] ex = new byte[4 + pad];
                        ex[0] = 0x35; ex[1] = (byte) 0xD9; ex[2] = (byte) (pad & 0xff); ex[3] = 0;
                        // total extra length = 4 + pad  => data offset aligned only if (4+pad)%4==pad -> ok
                        dst.setExtra(ex);
                        // recompute: header+name+extra; data offset = c.n+30+nameLen+4+pad
                    }
                } else {
                    dst.setMethod(ZipEntry.DEFLATED);
                }
                dst.setTime(0x0L + 315532800000L); // deterministik: 1980-01-01
                zo.putNextEntry(dst);
                zo.write(data);
                zo.closeEntry();
            }
        }
    }

    static void checkAlign(File f) throws Exception {
        // baca central directory via ZipFile + offset local header manual
        try (RandomAccessFile raf = new RandomAccessFile(f, "r"); ZipFile zf = new ZipFile(f)) {
            int bad = 0;
            // pindai local headers berurutan
            long pos = 0;
            while (true) {
                raf.seek(pos);
                byte[] h = new byte[30];
                if (raf.read(h) < 30) break;
                int sig = (h[0] & 0xff) | (h[1] & 0xff) << 8 | (h[2] & 0xff) << 16 | (h[3] & 0xff) << 24;
                if (sig != 0x04034b50) break;
                int method = (h[8] & 0xff) | (h[9] & 0xff) << 8;
                int flags = (h[6] & 0xff) | (h[7] & 0xff) << 8;
                long csize = ((h[18] & 0xffL) | (h[19] & 0xffL) << 8 | (h[20] & 0xffL) << 16 | (h[21] & 0xffL) << 24);
                int nl = (h[26] & 0xff) | (h[27] & 0xff) << 8;
                int el = (h[28] & 0xff) | (h[29] & 0xff) << 8;
                byte[] nm = new byte[nl]; raf.readFully(nm);
                String name = new String(nm, StandardCharsets.UTF_8);
                long data = pos + 30 + nl + el;
                if (method == 0 && data % 4 != 0) { System.out.println("MISALIGNED " + name); bad++; }
                if ((flags & 8) != 0) break; // data descriptor: ukuran tidak diketahui di header; cukup untuk stored
                pos = data + csize;
            }
            System.out.println("alignment bad=" + bad);
        }
    }
}
