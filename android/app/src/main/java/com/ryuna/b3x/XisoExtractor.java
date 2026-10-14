package com.ryuna.b3x;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an Xbox disc image (XDVDFS, "XISO") and copies its files out.
 * A port of xboxrecomp/tools/xiso/xdvdfs.py: probe the known game-partition
 * bases for the descriptor at sector 32, then walk the directory tables
 * linearly, sector by sector (as the Python reader does, so a bad tree
 * link cannot silently drop files).
 *
 * No Android classes here, so it can be run and checked on the PC
 * (main()).
 */
final class XisoExtractor {
    static final int SECTOR = 2048;
    static final byte[] MAGIC = "MICROSOFT*XBOX*MEDIA".getBytes(StandardCharsets.US_ASCII);
    /** Bare image, rebuilt images, redump with the video partition first. */
    static final long[] BASES = { 0x00000000L, 0x0000FD90L, 0x00030600L, 0x0FD90000L, 0x18300000L };
    /** Burnout 3: Takedown, NTSC-U. */
    static final int TITLE_ID = 0x4541005B;

    static final class Entry {
        final String path;   // "Data/Globalus.bin"
        final long sector, size;
        Entry(String path, long sector, long size) { this.path = path; this.sector = sector; this.size = size; }
    }

    interface Progress {
        /** Bytes done of total; return false to cancel. */
        boolean update(long done, long total, String file);
    }

    static final class Cancelled extends IOException {
        Cancelled() { super("cancelled"); }
    }

    private final FileChannel ch;
    final long base;
    private final long rootSector, rootSize;
    final List<Entry> files = new ArrayList<>();
    final List<String> dirs = new ArrayList<>();
    long totalBytes;

    XisoExtractor(FileChannel ch) throws IOException {
        this.ch = ch;
        long found = -1;
        for (long b : BASES) {
            ByteBuffer m = read(b + 32L * SECTOR, MAGIC.length);
            if (m != null && startsWithMagic(m)) { found = b; break; }
        }
        if (found < 0) throw new IOException("Not an Xbox disc image (no XDVDFS descriptor).");
        base = found;
        ByteBuffer d = read(base + 32L * SECTOR, SECTOR);
        d.order(ByteOrder.LITTLE_ENDIAN);
        rootSector = d.getInt(0x14) & 0xFFFFFFFFL;
        rootSize = d.getInt(0x18) & 0xFFFFFFFFL;
        walk(rootSector, rootSize, "", 0);
        for (Entry e : files) totalBytes += e.size;
    }

    private static boolean startsWithMagic(ByteBuffer b) {
        for (int i = 0; i < MAGIC.length; i++) if (b.get(i) != MAGIC[i]) return false;
        return true;
    }

    /** Exactly n bytes at off, or null past the end of the image. */
    private ByteBuffer read(long off, int n) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(n);
        while (b.hasRemaining()) {
            int r = ch.read(b, off + b.position());
            if (r < 0) return null;
        }
        b.flip();
        return b;
    }

    private void walk(long sector, long size, String prefix, int depth) throws IOException {
        if (depth > 32) throw new IOException("Directory tree too deep (damaged image?)");
        if (size == 0) return;
        ByteBuffer data = read(base + sector * SECTOR, (int) size);
        if (data == null) throw new IOException("Image truncated (directory at sector " + sector + ")");
        data.order(ByteOrder.LITTLE_ENDIAN);
        for (int s = 0; s < size; s += SECTOR) {
            int end = (int) Math.min(size, s + SECTOR);
            int off = s;
            while (off + 0x0E <= end) {
                int l = data.getShort(off) & 0xFFFF, r = data.getShort(off + 2) & 0xFFFF;
                long sec = data.getInt(off + 4) & 0xFFFFFFFFL;
                long sz = data.getInt(off + 8) & 0xFFFFFFFFL;
                int attr = data.get(off + 12) & 0xFF, nlen = data.get(off + 13) & 0xFF;
                if ((l == 0xFFFF && r == 0xFFFF) || nlen == 0) break;
                if (off + 0x0E + nlen > end) break;
                byte[] nb = new byte[nlen];
                for (int i = 0; i < nlen; i++) nb[i] = data.get(off + 0x0E + i);
                String name = new String(nb, StandardCharsets.US_ASCII);
                if (name.contains("/") || name.equals("..") || name.equals(".")) break;
                String path = prefix.isEmpty() ? name : prefix + "/" + name;
                if ((attr & 0x10) != 0) {
                    dirs.add(path);
                    walk(sec, sz, path, depth + 1);
                } else {
                    files.add(new Entry(path, sec, sz));
                }
                off += (0x0E + nlen + 3) & ~3;
            }
        }
    }

    Entry find(String path) {
        for (Entry e : files) if (e.path.equalsIgnoreCase(path)) return e;
        return null;
    }

    /** Title ID from default.xbe's certificate, or 0. */
    int titleId() throws IOException {
        Entry x = find("default.xbe");
        if (x == null || x.size < 0x200) return 0;
        ByteBuffer h = read(base + x.sector * SECTOR, (int) Math.min(x.size, 0x1000));
        if (h == null) return 0;
        h.order(ByteOrder.LITTLE_ENDIAN);
        if (h.getInt(0) != 0x48454258) return 0;            // "XBEH"
        long imageBase = h.getInt(0x104) & 0xFFFFFFFFL;
        long cert = (h.getInt(0x118) & 0xFFFFFFFFL) - imageBase;
        if (cert < 0 || cert + 12 > h.limit()) return 0;
        return h.getInt((int) cert + 8);
    }

    /** Copies every file under dest (created). */
    void extract(File dest, Progress p) throws IOException {
        if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("Cannot create " + dest);
        for (String d : dirs) {
            File f = new File(dest, d);
            if (!f.isDirectory() && !f.mkdirs()) throw new IOException("Cannot create " + f);
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(1 << 20);
        long done = 0;
        for (Entry e : files) {
            if (p != null && !p.update(done, totalBytes, e.path)) throw new Cancelled();
            File out = new File(dest, e.path);
            try (FileOutputStream fo = new FileOutputStream(out); FileChannel oc = fo.getChannel()) {
                long pos = base + e.sector * SECTOR, left = e.size;
                while (left > 0) {
                    buf.clear();
                    if (left < buf.capacity()) buf.limit((int) left);
                    int r = ch.read(buf, pos);
                    if (r <= 0) throw new IOException("Image truncated in " + e.path);
                    buf.flip();
                    while (buf.hasRemaining()) oc.write(buf);
                    pos += r; left -= r; done += r;
                    if (p != null && !p.update(done, totalBytes, e.path)) throw new Cancelled();
                }
            }
        }
        if (p != null) p.update(totalBytes, totalBytes, "");
    }

    /** PC check: java XisoExtractor.java <iso> [<dest>] */
    public static void main(String[] a) throws Exception {
        try (FileChannel c = FileChannel.open(new File(a[0]).toPath())) {
            XisoExtractor x = new XisoExtractor(c);
            System.out.printf("base 0x%X, %d files, %d dirs, %d bytes, title %08X%n",
                    x.base, x.files.size(), x.dirs.size(), x.totalBytes, x.titleId());
            if (a.length > 1) x.extract(new File(a[1]), null);
        }
    }
}
