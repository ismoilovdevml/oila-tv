package uz.oila.tv;

/**
 * Minimal QR Code encoder for short URLs: byte mode, error correction level L, versions 1-5
 * (each a single Reed-Solomon block), fixed mask 0. Enough for "http://<ip>:<port>/?k=<token>".
 */
final class Qr {
    // Version 1..5 at level L: total codewords and error-correction codewords.
    private static final int[] TOTAL = { 0, 26, 44, 70, 100, 134 };
    private static final int[] EC = { 0, 7, 10, 15, 20, 26 };

    /** Returns modules as [row][col], true = dark, or null if {@code text} does not fit. */
    static boolean[][] encode(String text) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int v = 1;
        while (v <= 5 && 4 + 8 + bytes.length * 8 > (TOTAL[v] - EC[v]) * 8) v++;
        if (v > 5) return null;
        int size = 17 + 4 * v;
        boolean[][] m = new boolean[size][size];
        boolean[][] fn = new boolean[size][size];

        for (int i = 0; i < size; i++) {
            set(m, fn, 6, i, i % 2 == 0);
            set(m, fn, i, 6, i % 2 == 0);
        }
        finder(m, fn, 3, 3);
        finder(m, fn, size - 4, 3);
        finder(m, fn, 3, size - 4);
        if (v > 1) {
            int a = size - 7;
            for (int dy = -2; dy <= 2; dy++)
                for (int dx = -2; dx <= 2; dx++)
                    set(m, fn, a + dx, a + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
        }
        format(m, fn, size);

        byte[] data = codewords(bytes, TOTAL[v] - EC[v]);
        byte[] all = new byte[TOTAL[v]];
        System.arraycopy(data, 0, all, 0, data.length);
        System.arraycopy(ecc(data, EC[v]), 0, all, data.length, EC[v]);

        // Zig-zag through two-column strips from the bottom-right, skipping the timing column.
        int bit = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            boolean upward = ((right + 1) & 2) == 0;
            for (int vert = 0; vert < size; vert++) {
                int y = upward ? size - 1 - vert : vert;
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    if (fn[y][x]) continue;
                    if (bit < all.length * 8) {
                        m[y][x] = ((all[bit >>> 3] >>> (7 - (bit & 7))) & 1) != 0;
                        bit++;
                    }
                    if ((x + y) % 2 == 0) m[y][x] = !m[y][x]; // mask 0
                }
            }
        }
        return m;
    }

    private static void set(boolean[][] m, boolean[][] fn, int x, int y, boolean dark) {
        m[y][x] = dark;
        fn[y][x] = true;
    }

    /** 7x7 finder centred at (cx, cy) plus its one-module light separator. */
    private static void finder(boolean[][] m, boolean[][] fn, int cx, int cy) {
        int size = m.length;
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx, y = cy + dy, d = Math.max(Math.abs(dx), Math.abs(dy));
                if (x >= 0 && x < size && y >= 0 && y < size) set(m, fn, x, y, d != 2 && d != 4);
            }
        }
    }

    /** Level L (01) + mask 0, BCH(15,5) protected, written in both copies, plus the dark module. */
    private static void format(boolean[][] m, boolean[][] fn, int size) {
        int data = 1 << 3;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(m, fn, 8, i, bit(bits, i));
        set(m, fn, 8, 7, bit(bits, 6));
        set(m, fn, 8, 8, bit(bits, 7));
        set(m, fn, 7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(m, fn, 14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(m, fn, size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(m, fn, 8, size - 15 + i, bit(bits, i));
        set(m, fn, 8, size - 8, true);
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    /** Byte-mode segment, terminator, then 0xEC/0x11 padding up to {@code capacity} codewords. */
    private static byte[] codewords(byte[] bytes, int capacity) {
        byte[] out = new byte[capacity];
        int pos = 0;
        long acc = 0;
        int nbits = 0;
        int[][] fields = new int[bytes.length + 3][];
        fields[0] = new int[] { 0x4, 4 };
        fields[1] = new int[] { bytes.length, 8 };
        for (int i = 0; i < bytes.length; i++) fields[i + 2] = new int[] { bytes[i] & 0xFF, 8 };
        fields[bytes.length + 2] = new int[] { 0, Math.min(4, capacity * 8 - (12 + bytes.length * 8)) };
        for (int[] f : fields) {
            acc = (acc << f[1]) | f[0];
            nbits += f[1];
            while (nbits >= 8) {
                out[pos++] = (byte) (acc >>> (nbits - 8));
                nbits -= 8;
            }
        }
        if (nbits > 0) out[pos++] = (byte) (acc << (8 - nbits));
        for (int pad = 0xEC; pos < capacity; pad ^= 0xEC ^ 0x11) out[pos++] = (byte) pad;
        return out;
    }

    /** Reed-Solomon remainder over GF(256), polynomial 0x11D. */
    private static byte[] ecc(byte[] data, int degree) {
        byte[] gen = new byte[degree];
        gen[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                gen[j] = (byte) mul(gen[j] & 0xFF, root);
                if (j + 1 < degree) gen[j] ^= gen[j + 1];
            }
            root = mul(root, 2);
        }
        byte[] r = new byte[degree];
        for (byte b : data) {
            int factor = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, degree - 1);
            r[degree - 1] = 0;
            for (int i = 0; i < degree; i++) r[i] ^= (byte) mul(gen[i] & 0xFF, factor);
        }
        return r;
    }

    private static int mul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private Qr() {}
}
