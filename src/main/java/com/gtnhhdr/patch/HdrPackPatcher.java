package com.gtnhhdr.patch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes an HDR copy of a Complementary Unbound r5.8.1 + Euphoria Patches 1.9.3 shader pack folder.
 * <p>
 * No code from Complementary or Euphoria Patches is contained here. The places to change are found by fingerprints
 * (the first 16 hex digits of SHA-256 over each trimmed line) of the player's own files, and only our own lines are
 * inserted. The HDR tonemap is built from the pack's own {@code DoCompTonemap}, copied out of the player's
 * {@code tonemaps.glsl} with a few of its lines swapped for ours. The original pack is never touched; the result is a
 * new folder named {@code "HDR " + name}. Every inserted line sits behind {@code #ifdef HDR_ENABLED}, which only
 * GTNH-HDR defines, so the copy looks like the original without the mod.
 * <p>
 * Also runnable on its own: {@code java -cp GTNH-HDR-<version>.jar com.gtnhhdr.patch.HdrPackPatcher <shaderpacks dir>}.
 */
public final class HdrPackPatcher {

    public static final String PREFIX = "HDR ";
    static final String MARK = "GTNH-HDR patch";
    /** Packs patched in place by the pre-release version carry this marker. */
    static final String OLD_MARK = "HDR Output patch";

    private HdrPackPatcher() {}

    /** Insert {@code lines} before line {@code at} of the unique run of lines matching {@code run}. */
    private static final class Insert {

        final String file;
        final String[] run;
        final int at;
        final String[] lines;

        Insert(String file, String[] run, int at, String... lines) {
            this.file = file;
            this.run = run;
            this.at = at;
            this.lines = lines;
        }
    }

    private static final String PIPELINE = "lib/pipelineSettings.glsl", COMPOSITE5 = "program/composite5.glsl",
        LENS_FLARE = "lib/misc/lensFlare.glsl", FINAL = "program/final.glsl", TONEMAPS = "lib/colors/tonemaps.glsl";

    private static final String[] RUN_COLORTEX19 = { "28f9025f9e91814d", "ed9623369ff082ab" };
    private static final String[] RUN_C5_INCLUDES = { "151ff771bc0016c4", "46274a08dc2a7985" };
    private static final String[] RUN_C5_TONEMAP = { "78f8bc3c96c120a6", "e2a01dc0c78780de", "cd31727502550a74",
        "9654a8a6c10a18eb", "1e258076b154c8a5", "dc12e978f9dca6ec" };
    private static final String[] RUN_C5_OUTPUT = { "24c609b85ade390d", "a3e16f788c3bee22" };
    private static final String[] RUN_FLARE_MIX = { "c36e5dc178c9652d", "e3b0c44298fc1c14", "b526acb671bb4fb0",
        "d10b36aa74a59bcf" };
    private static final String[] RUN_FINAL_INCLUDES = { "5058e558ed734926", "38087c7dac08f8d7" };
    private static final String[] RUN_FINAL_DITHER = { "f47530351ac5c26e", "aca57eda939135bf" };

    private static final List<Insert> INSERTS = Arrays.asList(
        new Insert(PIPELINE, RUN_COLORTEX19, 2,
            "",
            "// " + MARK + ": colortex3 carries the final colour normalised to the display peak (lib/misc/hdrOutput.glsl).",
            "// Same 4 bytes per pixel as RGBA8, 4x finer steps; its alpha is never read after the gbuffer stage.",
            "#ifdef HDR_ENABLED",
            "/*",
            "const int colortex3Format = RGB10_A2;",
            "*/",
            "#endif"),
        new Insert(COMPOSITE5, RUN_C5_INCLUDES, 2,
            "#ifdef HDR_ENABLED // " + MARK,
            "    #include \"/lib/misc/hdrOutput.glsl\"",
            "    #include \"/lib/colors/hdrTonemap.glsl\"",
            "#endif"),
        new Insert(COMPOSITE5, RUN_C5_TONEMAP, 0,
            "    #ifdef HDR_ENABLED // " + MARK + ": HDR tonemap, or the original SDR one for the A/B views",
            "    hdrOut = HdrIsHdrPixel(texCoord.x);",
            "    if (hdrOut) {",
            "        color = DoCompTonemapHDR(color);",
            "    } else {",
            "    #endif"),
        new Insert(COMPOSITE5, RUN_C5_TONEMAP, 6,
            "    #ifdef HDR_ENABLED",
            "    }",
            "    #endif"),
        new Insert(COMPOSITE5, RUN_C5_OUTPUT, 0,
            "    #ifdef HDR_ENABLED // " + MARK + ": store 0..1 relative to the peak (+10-bit dither)",
            "        if (hdrOut) color = HdrStoreNormalised(color, dither);",
            "    #endif",
            ""),
        new Insert(LENS_FLARE, RUN_FLARE_MIX, 2,
            "    #ifdef HDR_ENABLED // " + MARK + ": add the flare on top instead of washing towards SDR white",
            "        if (hdrOut) { color += flare; return; }",
            "    #endif"),
        new Insert(FINAL, RUN_FINAL_INCLUDES, 2,
            "#ifdef HDR_ENABLED // " + MARK,
            "    #include \"/lib/misc/hdrOutput.glsl\"",
            "#endif"),
        new Insert(FINAL, RUN_FINAL_DITHER, 1,
            "    #ifdef HDR_ENABLED // " + MARK + ": 16-bit output needs no 8-bit dither; world -> UI-relative brightness",
            "    if (HdrIsHdrPixel(texCoord.x)) color = HdrFinalToUi(color); else",
            "    #endif"));

    // Lines of the pack's DoCompTonemap that the HDR copy changes. Function start, then single lines inside it.
    private static final String FN_START = "d2ea4003441e32fa";
    private static final Map<String, String[]> TONEMAP_REPLACE = new LinkedHashMap<>();
    private static final String FN_CURVE = "ed3dc493c0a0685b";

    static {
        TONEMAP_REPLACE.put(FN_START, new String[] {
            "vec3 DoCompTonemapHDR(vec3 color) { // " + MARK + ": this pack's DoCompTonemap, shoulder ending at the peak",
            "    float P = HdrPeakRel();" });
        // exposure: also keep the input non-negative for the shoulder's exp()
        TONEMAP_REPLACE.put("6c5092b0800bfd37", new String[] { "    color = max(TM_EXPOSURE * color, vec3(0.0));" });
        // path to white: only for much brighter input than in SDR
        TONEMAP_REPLACE.put("1b04cf4cff120ab1", new String[] { "    float wpInputCurveMax = 16.0 * P * P;" });
        // ... and towards the peak instead of SDR white
        TONEMAP_REPLACE.put("939212b98dc56b49", new String[] { "    colorOut = mix(colorOut, vec3(HdrEncode(P)), whitePath);" });
        // T_LOWER_CURVE / T_UPPER_CURVE: above paper white keep the upper exponent instead of extrapolating it
        TONEMAP_REPLACE.put("18ab576e31c5bf0c", new String[] {
            "    colorOut = pow(max(colorOut, vec3(0.0)), mix(vec3(T_LOWER_CURVE - 0.10), vec3(T_UPPER_CURVE - 0.30), "
                + "sqrt(clamp(colorOut, 0.0, 1.0))));" });
        // no clamp to 1.0 at the end
        TONEMAP_REPLACE.put("0a06426770763fd4", new String[0]);
        TONEMAP_REPLACE.put("443e0aa5d9586ff7", new String[] { "    return colorOut;" });
    }

    /** Inserted after the curve line: from x = 1 an exponential shoulder with the same slope rolls into the peak. */
    private static final String[] TONEMAP_SHOULDER = {
        "    // " + MARK + ": the curve above, with d = 1, has value k and slope s at x = 1; above that roll into P",
        "    float hdrK = 1.0 / (b.x + c.x);",
        "    float hdrS = a.x * c.x / ((b.x + c.x) * (b.x + c.x));",
        "    float hdrRoom = max(P - hdrK, 1e-4);",
        "    vec3 hdrShoulder = hdrK + hdrRoom * (1.0 - exp(-max(color - 1.0, vec3(0.0)) * (hdrS / hdrRoom)));",
        "    colorOut = mix(colorOut, hdrShoulder, greaterThan(color, vec3(1.0)));" };

    private static final String TONEMAP_HEADER = "// " + MARK + " - generated on this PC from this pack's own lib/colors/tonemaps.glsl.\n"
        + "// Below linear 1.0 it is exactly the pack's DoCompTonemap, so TM_EXPOSURE, TM_CONTRAST, dark lift, dark\n"
        + "// desaturation and T_LOWER/UPPER_CURVE keep their meaning. Above it the shoulder ends at the display peak instead of\n"
        + "// SDR white, and colours bleach to white only for far brighter input. Output: sRGB-encoded, paper white = 1.0.\n"
        + "// Approach after rrtt217's ComplementaryReimagined-hdr.\n";

    // ------------------------------------------------------------------------------------------------------------

    /** One line per pack looked at, for the screen and the log. */
    public static List<String> makeHdrCopies(File shaderpacks) {
        final List<String> out = new ArrayList<>();
        final File[] entries = shaderpacks.listFiles();
        if (entries == null) {
            out.add("No shaderpacks folder found at " + shaderpacks);
            return out;
        }
        Arrays.sort(entries);
        for (File pack : entries) {
            final String name = pack.getName();
            final String lower = name.toLowerCase();
            if (!lower.contains("complementary") || name.startsWith(PREFIX)) continue;
            if (pack.isFile()) {
                if (lower.endsWith(".zip")) out.add(name + ": a .zip - unzip it into a folder first.");
                continue;
            }
            if (!new File(pack, "shaders").isDirectory()) continue;
            try {
                out.add(name + ": " + makeHdrCopy(pack));
            } catch (Exception e) {
                out.add(name + ": failed (" + e + ")");
            }
        }
        if (out.isEmpty()) out.add("No Complementary + Euphoria Patches shader pack folder found in " + shaderpacks);
        return out;
    }

    /** Returns a short result text; throws only on unexpected I/O errors. */
    public static String makeHdrCopy(File pack) throws Exception {
        final File shaders = new File(pack, "shaders");
        final Map<String, Text> files = new LinkedHashMap<>();
        for (String rel : new String[] { PIPELINE, COMPOSITE5, LENS_FLARE, FINAL, TONEMAPS }) {
            final File f = new File(shaders, rel);
            if (!f.isFile()) return "not supported (missing " + rel + ")";
            final Text t = Text.read(f);
            if (t.contains(MARK) || t.contains(OLD_MARK)) return "already contains the HDR patch, nothing to do";
            files.put(rel, t);
        }

        // 1. Plan everything first; refuse if any place isn't found exactly once.
        final List<String> problems = new ArrayList<>();
        final Map<String, List<int[]>> where = new LinkedHashMap<>(); // file -> {line index, insert index}
        for (int i = 0; i < INSERTS.size(); i++) {
            final Insert ins = INSERTS.get(i);
            final int start = files.get(ins.file).findUniqueRun(ins.run);
            if (start < 0) problems.add(ins.file);
            else where.computeIfAbsent(ins.file, k -> new ArrayList<>()).add(new int[] { start + ins.at, i });
        }
        final String tonemap = buildTonemap(files.get(TONEMAPS), problems);
        if (!problems.isEmpty()) {
            return "not supported - only Complementary Unbound r5.8.1 + Euphoria Patches 1.9.3 is (differs in "
                + String.join(", ", new java.util.LinkedHashSet<>(problems)) + ")";
        }

        final File target = new File(pack.getParentFile(), PREFIX + pack.getName());
        if (target.exists()) return "\"" + target.getName() + "\" already exists - delete it to make a fresh one";

        // 2. Copy the pack and its settings file, then write our changes into the copy only.
        try {
            copyTree(pack.toPath(), target.toPath());
            final File settings = new File(pack.getParentFile(), pack.getName() + ".txt");
            if (settings.isFile()) {
                Files.copy(settings.toPath(), new File(pack.getParentFile(), target.getName() + ".txt").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            }
            final File targetShaders = new File(target, "shaders");
            for (Map.Entry<String, List<int[]>> e : where.entrySet()) {
                final Text t = files.get(e.getKey());
                final List<int[]> list = e.getValue();
                // bottom-up so earlier line numbers stay valid; same line: later edit first keeps the table order
                list.sort((x, y) -> x[0] != y[0] ? Integer.compare(y[0], x[0]) : Integer.compare(y[1], x[1]));
                for (int[] w : list) t.lines.addAll(w[0], Arrays.asList(INSERTS.get(w[1]).lines));
                t.write(new File(targetShaders, e.getKey()));
            }
            new Text(Arrays.asList((TONEMAP_HEADER + tonemap).split("\n", -1)), "\r\n")
                .write(new File(targetShaders, "lib/colors/hdrTonemap.glsl"));
            new Text(Arrays.asList(resource("/assets/gtnhhdr/shaderpatch/hdrOutput.glsl").split("\n", -1)), "\r\n")
                .write(new File(targetShaders, "lib/misc/hdrOutput.glsl"));
        } catch (Exception e) {
            deleteTree(target.toPath());
            throw e;
        }
        return "created \"" + target.getName() + "\" - choose it in Options > Video Settings > Shaders";
    }

    private static String buildTonemap(Text tonemaps, List<String> problems) {
        final List<String> l = tonemaps.lines;
        int start = -1;
        for (int i = 0; i < l.size(); i++) {
            if (fp(l.get(i)).equals(FN_START)) {
                if (start >= 0) start = -2;
                else if (start == -1) start = i;
            }
        }
        if (start < 0) {
            problems.add(TONEMAPS);
            return "";
        }
        int end = start;
        while (end < l.size() && !l.get(end).equals("}")) end++;
        if (end >= l.size()) {
            problems.add(TONEMAPS);
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        final Map<String, Integer> seen = new LinkedHashMap<>();
        for (int i = start; i <= end; i++) {
            final String line = l.get(i), f = fp(line);
            final String[] repl = TONEMAP_REPLACE.get(f);
            if (repl != null || f.equals(FN_CURVE)) seen.merge(f, 1, Integer::sum);
            if (repl != null) {
                for (String r : repl) sb.append(r).append('\n');
            } else {
                sb.append(line).append('\n');
            }
            if (f.equals(FN_CURVE)) for (String r : TONEMAP_SHOULDER) sb.append(r).append('\n');
        }
        for (String f : TONEMAP_REPLACE.keySet()) {
            if (seen.getOrDefault(f, 0) != 1) problems.add(TONEMAPS);
        }
        if (seen.getOrDefault(FN_CURVE, 0) != 1) problems.add(TONEMAPS);
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------------------

    static String fp(String line) {
        try {
            final byte[] h = MessageDigest.getInstance("SHA-256")
                .digest(line.trim().getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", h[i] & 0xFF));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A text file as lines, remembering its line endings. */
    private static final class Text {

        final List<String> lines;
        final String eol;
        private List<String> fps;

        Text(List<String> lines, String eol) {
            this.lines = new ArrayList<>(lines);
            this.eol = eol;
        }

        static Text read(File f) throws IOException {
            final String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return new Text(Arrays.asList(s.replace("\r\n", "\n").split("\n", -1)), s.contains("\r\n") ? "\r\n" : "\n");
        }

        boolean contains(String s) {
            for (String l : lines) if (l.contains(s)) return true;
            return false;
        }

        /** Start of the only run of lines with these fingerprints, or -1 (none or several). */
        int findUniqueRun(String[] run) {
            if (fps == null) {
                fps = new ArrayList<>(lines.size());
                for (String l : lines) fps.add(fp(l));
            }
            int found = -1;
            outer:
            for (int i = 0; i + run.length <= fps.size(); i++) {
                for (int j = 0; j < run.length; j++) if (!fps.get(i + j).equals(run[j])) continue outer;
                if (found >= 0) return -1;
                found = i;
            }
            return found;
        }

        void write(File f) throws IOException {
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), String.join(eol, lines).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = HdrPackPatcher.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing " + path);
            final ByteArrayOutputStream bo = new ByteArrayOutputStream();
            final byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path p) {
        try {
            if (!Files.exists(p)) return;
            Files.walkFileTree(p, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java -cp GTNH-HDR-<version>.jar com.gtnhhdr.patch.HdrPackPatcher <shaderpacks folder>");
            System.exit(2);
        }
        for (String line : makeHdrCopies(new File(args[0]))) System.out.println(line);
    }
}
