package dev.sp.bugfix.missingdevice;

import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.flt.app.TL; // the application; TL.MRY(UUID) is its "package file for this UUID, or throw"

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Bitwig has no placeholder for a missing native device. When a project uses one whose package it can't find,
 * TL.MRY(UUID) throws DeviceNotFoundException; the device is left without contents, the GUI then crashes on it
 * (NullPointerException in its descriptor getter) and, past that, the audio engine does (segfault).
 *
 * This mod answers that lookup with a placeholder package instead: a copy of the bundled "Missing Device"
 * template (stock DC Offset at its default, so audio passes through unchanged) with the missing UUID written
 * into it, so the device loads real contents. The template is stored unencrypted, so the UUID can be replaced
 * in place: as 36-character text and as 16 raw bytes, both the same length as the template's.
 */
public final class PlaceholderHooks {
    private PlaceholderHooks() {}

    static final Bml.Mod MOD = Bml.mod("bugfix-missing-device-placeholder");
    private static final UUID TEMPLATE = UUID.fromString("5bd4e0c2-7a9e-4f61-b3c8-0e6f2d9a51c7");
    private static final Object LOCK = new Object();

    /** Replaces this.cfM(uuid) (the package file, or null) inside TL.MRY(UUID), which throws on null. */
    @Hook(id = "missing-device-placeholder-file", target = TL.class, method = "MRY(Ljava/util/UUID;)Ljava/io/File;",
          at = At.REPLACE_CALL, callOwner = TL.class, call = "cfM(Ljava/util/UUID;)Ljava/io/File;", occurrence = 1)
    public static File packageFile(TL app, UUID uuid) {
        File file = app.cfM(uuid);
        if (file != null || uuid == null) return file;
        try {
            File stub = placeholder(uuid);
            MOD.log("no package for uuid=" + uuid + ": using the placeholder " + stub);
            return stub;
        } catch (Throwable t) {
            MOD.log("can't make a placeholder for uuid=" + uuid, t);
            return null; // Bitwig throws DeviceNotFoundException, as without the mod
        }
    }

    private static File placeholder(UUID uuid) throws Exception {
        synchronized (LOCK) {
            Path dir = MOD.resource("data").resolve("placeholders");
            Path out = dir.resolve(uuid + ".bwdevice");
            if (Files.isRegularFile(out)) return out.toFile();
            byte[] bytes;
            try (InputStream in = PlaceholderHooks.class.getResourceAsStream("missing-device.bwdevice")) {
                if (in == null) throw new IllegalStateException("template missing from helper.jar");
                bytes = in.readAllBytes();
            }
            int text = replace(bytes, TEMPLATE.toString().getBytes(StandardCharsets.US_ASCII),
                               uuid.toString().getBytes(StandardCharsets.US_ASCII));
            int raw = replace(bytes, raw(TEMPLATE), raw(uuid));
            if (text < 1 || raw < 2) throw new IllegalStateException("template UUID found text x" + text + " raw x" + raw);
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "placeholder", ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return out.toFile();
        }
    }

    private static byte[] raw(UUID u) {
        return ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();
    }

    /** Replaces every occurrence of from (same length as to) in data; returns the count. */
    private static int replace(byte[] data, byte[] from, byte[] to) {
        int n = 0;
        outer:
        for (int i = 0; i + from.length <= data.length; i++) {
            for (int j = 0; j < from.length; j++) if (data[i + j] != from[j]) continue outer;
            System.arraycopy(to, 0, data, i, to.length);
            n++;
            i += from.length - 1;
        }
        return n;
    }
}
