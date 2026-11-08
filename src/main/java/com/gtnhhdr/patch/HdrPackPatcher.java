package com.gtnhhdr.patch;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Makes an HDR copy of a supported shader pack: Complementary Unbound r5.8.1 + Euphoria Patches 1.9.3, or Eclipse
 * (also with Chroma Patch). Folders and .zip files both work.
 * <p>
 * No code from those packs is contained here. The places to change are found by fingerprints (the first 16 hex digits
 * of SHA-256 over each trimmed line) of the player's own files, and only our own lines are inserted. The HDR tonemap
 * is built from the pack's own tonemap function, copied out of the player's file with a few of its lines swapped for
 * ours. The original pack is never touched; the result is a new folder named {@code "HDR " + name}. Every inserted
 * line sits behind {@code #ifdef HDR_ENABLED}, which only GTNH-HDR defines, so the copy looks like the original
 * without the mod.
 * <p>
 * Also runnable on its own: {@code java -cp GTNH-HDR-<version>.jar com.gtnhhdr.patch.HdrPackPatcher <shaderpacks dir>}.
 */
public final class HdrPackPatcher {

    public static final String PREFIX = "HDR ";
    static final String MARK = "GTNH-HDR patch";
    /** Packs patched in place by the pre-release version carry this marker. */
    static final String OLD_MARK = "HDR Output patch";

    private static final String TONEMAP_OUT = "lib/colors/hdrTonemap.glsl", OUTPUT_OUT = "lib/misc/hdrOutput.glsl";

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

    /** How to make the HDR tonemap from one function of the pack. */
    private static final class Tonemap {

        final String file, fnStart, curveLine, header;
        final Map<String, String[]> replace;
        final String[] afterCurve;

        Tonemap(String file, String fnStart, Map<String, String[]> replace, String curveLine, String[] afterCurve,
            String header) {
            this.file = file;
            this.fnStart = fnStart;
            this.replace = replace;
            this.curveLine = curveLine;
            this.afterCurve = afterCurve;
            this.header = header;
        }
    }

    /** Everything needed to patch one family of shader packs. */
    private static final class Recipe {

        final String supported;
        final String[] files;
        final List<Insert> inserts;
        final Tonemap tonemap;

        Recipe(String supported, String[] files, List<Insert> inserts, Tonemap tonemap) {
            this.supported = supported;
            this.files = files;
            this.inserts = inserts;
            this.tonemap = tonemap;
        }
    }

    // --- Complementary Unbound r5.8.1 + Euphoria Patches 1.9.3 --------------------------------------------------

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

    private static final List<Insert> COMP_INSERTS = Arrays.asList(
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
    private static final String COMP_FN_START = "d2ea4003441e32fa";
    private static final Map<String, String[]> COMP_REPLACE = new LinkedHashMap<>();
    private static final String COMP_FN_CURVE = "ed3dc493c0a0685b";

    static {
        COMP_REPLACE.put(COMP_FN_START, new String[] {
            "vec3 DoCompTonemapHDR(vec3 color) { // " + MARK + ": this pack's DoCompTonemap, shoulder ending at the peak",
            "    float P = HdrPeakRel();" });
        // exposure: also keep the input non-negative for the shoulder's exp()
        COMP_REPLACE.put("6c5092b0800bfd37", new String[] { "    color = max(TM_EXPOSURE * color, vec3(0.0));" });
        // path to white: only for much brighter input than in SDR
        COMP_REPLACE.put("1b04cf4cff120ab1", new String[] { "    float wpInputCurveMax = 16.0 * P * P;" });
        // ... and towards the peak instead of SDR white
        COMP_REPLACE.put("939212b98dc56b49", new String[] { "    colorOut = mix(colorOut, vec3(HdrEncode(P)), whitePath);" });
        // T_LOWER_CURVE / T_UPPER_CURVE: above paper white keep the upper exponent instead of extrapolating it
        COMP_REPLACE.put("18ab576e31c5bf0c", new String[] {
            "    colorOut = pow(max(colorOut, vec3(0.0)), mix(vec3(T_LOWER_CURVE - 0.10), vec3(T_UPPER_CURVE - 0.30), "
                + "sqrt(clamp(colorOut, 0.0, 1.0))));" });
        // no clamp to 1.0 at the end
        COMP_REPLACE.put("0a06426770763fd4", new String[0]);
        COMP_REPLACE.put("443e0aa5d9586ff7", new String[] { "    return colorOut;" });
    }

    /** Inserted after the curve line: from x = 1 an exponential shoulder with the same slope rolls into the peak. */
    private static final String[] COMP_SHOULDER = {
        "    // " + MARK + ": the curve above, with d = 1, has value k and slope s at x = 1; above that roll into P",
        "    float hdrK = 1.0 / (b.x + c.x);",
        "    float hdrS = a.x * c.x / ((b.x + c.x) * (b.x + c.x));",
        "    float hdrRoom = max(P - hdrK, 1e-4);",
        "    vec3 hdrShoulder = hdrK + hdrRoom * (1.0 - exp(-max(color - 1.0, vec3(0.0)) * (hdrS / hdrRoom)));",
        "    colorOut = mix(colorOut, hdrShoulder, greaterThan(color, vec3(1.0)));" };

    private static final String COMP_HEADER = "// " + MARK + " - generated on this PC from this pack's own lib/colors/tonemaps.glsl.\n"
        + "// Below linear 1.0 it is exactly the pack's DoCompTonemap, so TM_EXPOSURE, TM_CONTRAST, dark lift, dark\n"
        + "// desaturation and T_LOWER/UPPER_CURVE keep their meaning. Above it the shoulder ends at the display peak instead of\n"
        + "// SDR white, and colours bleach to white only for far brighter input. Output: sRGB-encoded, paper white = 1.0.\n"
        + "// Approach after rrtt217's ComplementaryReimagined-hdr.\n";

    private static final Recipe COMPLEMENTARY = new Recipe("Complementary Unbound r5.8.1 + Euphoria Patches 1.9.3",
        new String[] { PIPELINE, COMPOSITE5, LENS_FLARE, FINAL, TONEMAPS }, COMP_INSERTS,
        new Tonemap(TONEMAPS, COMP_FN_START, COMP_REPLACE, COMP_FN_CURVE, COMP_SHOULDER, COMP_HEADER));

    // --- Eclipse (stock Unstable, Chroma Patch and edits of those) ----------------------------------------------
    //
    // Eclipse's bloom and TAA run before its tonemap in float buffers, so everything up to composite11 is already
    // linear HDR. After the tonemap the picture lives in colortex7 (RGBA8, whose alpha also holds translucency masks),
    // so under HDR composite11, composite12 and final use colortex2 instead: already RGBA16F, cleared every frame and
    // read by nothing after composite4.

    private static final String E_C11 = "dimensions/composite11.fsh", E_C12 = "dimensions/composite12.fsh",
        E_FINAL = "dimensions/final.fsh", E_TRANSFORMS = "lib/color_transforms.glsl";

    private static final String[] RUN_E_INCLUDES = { "322ec57a20c53047", "641d2337fcdf4b19" };
    private static final String[] RUN_E_TARGETS = { "b76685c91688f505" };
    private static final String[] RUN_E_SAMPLER = { "0c3fb6c59996cf29" };
    private static final String[] RUN_E_C11_TONEMAP = { "e74cf965281db37d", "b74904dc4f092d02", "cd31727502550a74",
        "f7d85309e81b4f32", "06b0c0363b0d5433", "e3b0c44298fc1c14", "96b6bdac2f4d453d", "1e258076b154c8a5",
        "e3b0c44298fc1c14", "abe8b68d2c692b34" };
    private static final String[] RUN_E_C12_GRADE = { "913eb0aa7837aeec" };
    private static final String[] RUN_E_C12_OUTPUT = { "5366bda8375e529f" };
    private static final String[] RUN_E_FINAL_OUTPUT = { "f20da654ebdcca24" };

    private static String[] eclipseRename() {
        return new String[] {
            "#ifdef HDR_ENABLED // " + MARK + ": the HDR picture is in colortex2 from composite11 on",
            "    #define colortex7 colortex2",
            "#endif" };
    }

    private static String[] eclipseTargetsBefore() {
        return new String[] { "    #ifdef HDR_ENABLED // " + MARK, "    /* RENDERTARGETS:2 */", "    #else" };
    }

    private static String[] eclipseIncludeOutput() {
        return new String[] { "#ifdef HDR_ENABLED // " + MARK, "    #include \"/lib/misc/hdrOutput.glsl\"", "#endif" };
    }

    private static final List<Insert> ECLIPSE_INSERTS = Arrays.asList(
        new Insert(E_C11, RUN_E_INCLUDES, 2,
            "#ifdef HDR_ENABLED // " + MARK,
            "    #include \"/lib/misc/hdrOutput.glsl\"",
            "    #include \"/lib/colors/hdrTonemap.glsl\"",
            "#endif"),
        new Insert(E_C11, RUN_E_TARGETS, 0, eclipseTargetsBefore()),
        new Insert(E_C11, RUN_E_TARGETS, 1, "    #endif"),
        new Insert(E_C11, RUN_E_C11_TONEMAP, 0,
            "    #ifdef HDR_ENABLED // " + MARK + ": HDR tonemap, or the original SDR one for the A/B views",
            "    if (HdrIsHdrPixel(texcoord.x)) {",
            "        gl_FragData[0].rgb = HdrEncode(HdrAgxTonemap(col));",
            "    } else {",
            "    #endif"),
        new Insert(E_C11, RUN_E_C11_TONEMAP, 10,
            "    #ifdef HDR_ENABLED",
            "    }",
            "    #endif"),

        new Insert(E_C12, RUN_E_SAMPLER, 0, eclipseRename()),
        new Insert(E_C12, RUN_E_INCLUDES, 2, eclipseIncludeOutput()),
        new Insert(E_C12, RUN_E_TARGETS, 0, eclipseTargetsBefore()),
        new Insert(E_C12, RUN_E_TARGETS, 1, "    #endif"),
        new Insert(E_C12, RUN_E_C12_GRADE, 0,
            "  #ifdef HDR_ENABLED // " + MARK + ": saturation and grading only work on 0..1, set highlights aside",
            "  vec3 hdrAbove = max(color - 1.0, vec3(0.0));",
            "  color -= hdrAbove;",
            "  #endif"),
        new Insert(E_C12, RUN_E_C12_OUTPUT, 0,
            "  #ifdef HDR_ENABLED // " + MARK + ": put the highlights back, no 8-bit clamp or dither",
            "  if (HdrIsHdrPixel(texcoord.x)) gl_FragData[0] = vec4(max(color + hdrAbove, vec3(0.0)), 0.0); else",
            "  #endif"),

        new Insert(E_FINAL, RUN_E_SAMPLER, 0, eclipseRename()),
        new Insert(E_FINAL, RUN_E_INCLUDES, 2, eclipseIncludeOutput()),
        new Insert(E_FINAL, RUN_E_FINAL_OUTPUT, 0,
            "  #ifdef HDR_ENABLED // " + MARK + ": world -> UI-relative brightness",
            "  if (HdrIsHdrPixel(texcoord.x)) COLOR = HdrFinalToUiExtended(COLOR);",
            "  #endif"));

    private static final String ECLIPSE_FN_START = "ee275507aee522a8";
    private static final Map<String, String[]> ECLIPSE_REPLACE = new LinkedHashMap<>();

    static {
        ECLIPSE_REPLACE.put(ECLIPSE_FN_START, new String[] {
            "vec3 HdrAgxTonemap(vec3 color) { // " + MARK + ": this pack's AgX with the top of the curve ending at the peak",
            "    float P = HdrPeakRel();" });
        // keep the log value open at the top so channels stay apart above the old white
        ECLIPSE_REPLACE.put("16b1a1a81aaf45dc", new String[] { "    color = max(log2(max(color, vec3(1e-10))), vec3(AgxMinEv));" });
        ECLIPSE_REPLACE.put("23f857f7c8fcd6a5", new String[] {
            "    color = HdrAgxCurve(color, (log2(HDR_AGX_KNEE) - AgxMinEv) / (AgxMaxEv - AgxMinEv),",
            "        (log2(HDR_AGX_END) - AgxMinEv) / (AgxMaxEv - AgxMinEv), P);" });
        ECLIPSE_REPLACE.put("e4938092b21dcedb", new String[] { "    color = pow(max(color, vec3(0.0)), vec3(2.2));" });
        // instead of a clamp to 1.0: scale the whole colour down to the peak, so its hue stays
        ECLIPSE_REPLACE.put("fb1fc74702d8faf7", new String[] {
            "    float hdrTop = max(color.r, max(color.g, color.b));",
            "    if (hdrTop > P) color *= P / hdrTop;" });
    }

    private static final String ECLIPSE_HEADER = "// " + MARK + " - generated on this PC from this pack's own lib/color_transforms.glsl.\n"
        + "// Below HDR_AGX_KNEE it is exactly the pack's AgX. Above it the curve rises to the display peak, reached at\n"
        + "// HDR_AGX_END. Scene brightness 1.0 is about 65% of SDR white in AgX; glowing blocks reach 5 x Emissive_Brightness.\n"
        + "// Output: linear, paper white = 1.0.\n"
        + "#define HDR_AGX_KNEE 1.5\n"
        + "#define HDR_AGX_END (8.0 * max(Emissive_Brightness, 1.0))\n"
        + "\n"
        + "// The pack's sigmoid up to the knee, then a smooth cubic (same value and slope at the knee) to peak^(1/2.2).\n"
        + "vec3 HdrAgxCurve(vec3 u, float knee, float end, float P) {\n"
        + "    float atKnee = agxDefaultContrastApprox(vec3(knee)).x;\n"
        + "    float slope = (agxDefaultContrastApprox(vec3(knee + 0.001)).x - agxDefaultContrastApprox(vec3(knee - 0.001)).x) / 0.002;\n"
        + "    float top = max(pow(P, 1.0 / 2.2), atKnee + 1e-3);\n"
        + "    float span = end - knee;\n"
        + "    vec3 t = clamp((u - knee) / span, 0.0, 1.0);\n"
        + "    vec3 t2 = t * t, t3 = t2 * t;\n"
        + "    vec3 above = (2.0 * t3 - 3.0 * t2 + 1.0) * atKnee + (t3 - 2.0 * t2 + t) * span * slope + (3.0 * t2 - 2.0 * t3) * top;\n"
        + "    return mix(agxDefaultContrastApprox(u), above, greaterThan(u, vec3(knee)));\n"
        + "}\n\n";

    private static final Recipe ECLIPSE = new Recipe("Eclipse (with or without Chroma Patch)",
        new String[] { E_C11, E_C12, E_FINAL, E_TRANSFORMS }, ECLIPSE_INSERTS,
        new Tonemap(E_TRANSFORMS, ECLIPSE_FN_START, ECLIPSE_REPLACE, null, new String[0], ECLIPSE_HEADER));

    private static final List<Recipe> RECIPES = Arrays.asList(COMPLEMENTARY, ECLIPSE);

    // ------------------------------------------------------------------------------------------------------------

    /** One line per pack handled, for the screen and the log. */
    public static List<String> makeHdrCopies(File shaderpacks) {
        final List<String> out = new ArrayList<>();
        final File[] entries = shaderpacks.listFiles();
        if (entries == null) {
            out.add("No shaderpacks folder found at " + shaderpacks);
            return out;
        }

        // The pack in use (or the original of the HDR copy in use) if we can handle it: copying every Eclipse zip
        // lying around would cost 50 MB each.
        final File selected = selectedPack(shaderpacks);
        if (selected != null) {
            out.add(selected.getName() + ": " + tryCopy(selected));
            return out;
        }

        Arrays.sort(entries);
        for (File pack : entries) {
            if (pack.getName().startsWith(PREFIX) || recipeOf(pack) == null) continue;
            out.add(pack.getName() + ": " + tryCopy(pack));
        }
        if (out.isEmpty()) {
            out.add("No Complementary + Euphoria Patches or Eclipse shader pack found in " + shaderpacks);
        }
        return out;
    }

    private static String tryCopy(File pack) {
        try {
            return makeHdrCopy(pack);
        } catch (Exception e) {
            return "failed (" + e + ")";
        }
    }

    /** The supported pack chosen in config/shaders.properties, or null. */
    private static File selectedPack(File shaderpacks) {
        final File config = new File(shaderpacks.getAbsoluteFile().getParentFile(), "config/shaders.properties");
        if (!config.isFile()) return null;
        final Properties p = new Properties();
        try (InputStream in = new FileInputStream(config)) {
            p.load(in);
        } catch (IOException e) {
            return null;
        }
        String name = p.getProperty("shaderPack");
        if (name == null || name.trim().isEmpty()) return null;
        name = name.trim();
        if (name.startsWith(PREFIX)) name = name.substring(PREFIX.length());
        for (String candidate : new String[] { name, name + ".zip" }) {
            final File pack = new File(shaderpacks, candidate);
            if (pack.exists() && recipeOf(pack) != null) return pack;
        }
        return null;
    }

    /** Which recipe's files this pack has, or null if it's another kind of pack (or not a pack at all). */
    private static Recipe recipeOf(File pack) {
        try (Source source = Source.open(pack)) {
            if (source == null) return null;
            for (Recipe r : RECIPES) {
                boolean all = true;
                for (String rel : r.files) all &= source.read(rel) != null;
                if (all) return r;
            }
        } catch (IOException ignored) {}
        return null;
    }

    /** Returns a short result text; throws only on unexpected I/O errors. */
    public static String makeHdrCopy(File pack) throws Exception {
        final Recipe recipe = recipeOf(pack);
        if (recipe == null) return "not supported (not a Complementary + Euphoria Patches or Eclipse pack)";

        try (Source source = Source.open(pack)) {
            final Map<String, Text> files = new LinkedHashMap<>();
            for (String rel : recipe.files) {
                final Text t = Text.parse(source.read(rel));
                if (t.contains(MARK) || t.contains(OLD_MARK)) return "already contains the HDR patch, nothing to do";
                files.put(rel, t);
            }

            // 1. Plan everything first; refuse if any place isn't found exactly once.
            final List<String> problems = new ArrayList<>();
            final Map<String, List<int[]>> where = new LinkedHashMap<>(); // file -> {line index, insert index}
            for (int i = 0; i < recipe.inserts.size(); i++) {
                final Insert ins = recipe.inserts.get(i);
                final int start = files.get(ins.file).findUniqueRun(ins.run);
                if (start < 0) problems.add(ins.file);
                else where.computeIfAbsent(ins.file, k -> new ArrayList<>()).add(new int[] { start + ins.at, i });
            }
            final String tonemap = buildTonemap(recipe.tonemap, files.get(recipe.tonemap.file), problems);
            if (!problems.isEmpty()) {
                return "not supported - only " + recipe.supported + " is (differs in "
                    + String.join(", ", new java.util.LinkedHashSet<>(problems)) + ")";
            }

            final String targetName = PREFIX + (pack.isFile() ? pack.getName().replaceAll("(?i)\\.zip$", "") : pack.getName());
            final File target = new File(pack.getParentFile(), targetName);
            if (target.exists()) return "\"" + targetName + "\" already exists - delete it to make a fresh one";

            // 2. Copy the pack and its settings file, then write our changes into the copy only.
            try {
                source.copyTo(target.toPath());
                final File settings = new File(pack.getParentFile(), pack.getName() + ".txt");
                if (settings.isFile()) {
                    Files.copy(settings.toPath(), new File(pack.getParentFile(), targetName + ".txt").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                }
                final File targetShaders = new File(target, "shaders");
                for (Map.Entry<String, List<int[]>> e : where.entrySet()) {
                    final Text t = files.get(e.getKey());
                    final List<int[]> list = e.getValue();
                    // bottom-up so earlier line numbers stay valid; same line: later edit first keeps the table order
                    list.sort((x, y) -> x[0] != y[0] ? Integer.compare(y[0], x[0]) : Integer.compare(y[1], x[1]));
                    for (int[] w : list) t.lines.addAll(w[0], Arrays.asList(recipe.inserts.get(w[1]).lines));
                    t.write(new File(targetShaders, e.getKey()));
                }
                new Text(Arrays.asList((recipe.tonemap.header + tonemap).split("\n", -1)), "\r\n")
                    .write(new File(targetShaders, TONEMAP_OUT));
                new Text(Arrays.asList(resource("/assets/gtnhhdr/shaderpatch/hdrOutput.glsl").split("\n", -1)), "\r\n")
                    .write(new File(targetShaders, OUTPUT_OUT));
            } catch (Exception e) {
                deleteTree(target.toPath());
                throw e;
            }
            return "created \"" + targetName + "\" - choose it in Options > Video Settings > Shaders";
        }
    }

    private static String buildTonemap(Tonemap spec, Text source, List<String> problems) {
        final List<String> l = source.lines;
        int start = -1;
        for (int i = 0; i < l.size(); i++) {
            if (fp(l.get(i)).equals(spec.fnStart)) {
                if (start >= 0) start = -2;
                else if (start == -1) start = i;
            }
        }
        if (start < 0) {
            problems.add(spec.file);
            return "";
        }
        int end = start;
        while (end < l.size() && !l.get(end).equals("}")) end++;
        if (end >= l.size()) {
            problems.add(spec.file);
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        final Map<String, Integer> seen = new LinkedHashMap<>();
        for (int i = start; i <= end; i++) {
            final String line = l.get(i), f = fp(line);
            final String[] repl = spec.replace.get(f);
            if (repl != null || f.equals(spec.curveLine)) seen.merge(f, 1, Integer::sum);
            if (repl != null) {
                for (String r : repl) sb.append(r).append('\n');
            } else {
                sb.append(line).append('\n');
            }
            if (f.equals(spec.curveLine)) for (String r : spec.afterCurve) sb.append(r).append('\n');
        }
        for (String f : spec.replace.keySet()) {
            if (seen.getOrDefault(f, 0) != 1) problems.add(spec.file);
        }
        if (spec.curveLine != null && seen.getOrDefault(spec.curveLine, 0) != 1) problems.add(spec.file);
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

        static Text parse(String s) {
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

    /** A shader pack on disk: a folder, or a zip with shaders/ at its root or one folder down. */
    private interface Source extends Closeable {

        /** A file under shaders/, or null if the pack doesn't have it. */
        String read(String rel) throws IOException;

        /** Writes the whole pack (with shaders/ at the root) into {@code to}. */
        void copyTo(Path to) throws IOException;

        @Override
        void close() throws IOException;

        /** Null if this isn't a shader pack. */
        static Source open(File pack) throws IOException {
            if (pack.isDirectory()) return new File(pack, "shaders").isDirectory() ? new DirSource(pack) : null;
            if (!pack.isFile() || !pack.getName().toLowerCase().endsWith(".zip")) return null;
            final ZipFile zip;
            try {
                zip = new ZipFile(pack);
            } catch (IOException e) {
                return null;
            }
            final String prefix = ZipSource.findPrefix(zip);
            if (prefix == null) {
                zip.close();
                return null;
            }
            return new ZipSource(zip, prefix);
        }
    }

    private static final class DirSource implements Source {

        private final File pack;

        DirSource(File pack) {
            this.pack = pack;
        }

        @Override
        public String read(String rel) throws IOException {
            final File f = new File(new File(pack, "shaders"), rel);
            return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        }

        @Override
        public void copyTo(Path to) throws IOException {
            copyTree(pack.toPath(), to);
        }

        @Override
        public void close() {}
    }

    private static final class ZipSource implements Source {

        private final ZipFile zip;
        private final String prefix;

        ZipSource(ZipFile zip, String prefix) {
            this.zip = zip;
            this.prefix = prefix;
        }

        /** "" or "SomeFolder/" - where shaders/ is - or null if the zip has none. */
        static String findPrefix(ZipFile zip) {
            String nested = null;
            final Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                final String n = en.nextElement().getName().replace('\\', '/');
                if (n.startsWith("shaders/")) return "";
                final int slash = n.indexOf('/');
                if (nested == null && slash > 0 && n.startsWith("shaders/", slash + 1)) nested = n.substring(0, slash + 1);
            }
            return nested;
        }

        @Override
        public String read(String rel) throws IOException {
            final ZipEntry e = zip.getEntry(prefix + "shaders/" + rel);
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e)) {
                return new String(readAll(in), StandardCharsets.UTF_8);
            }
        }

        @Override
        public void copyTo(Path to) throws IOException {
            final Path root = to.toAbsolutePath().normalize();
            final Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                final ZipEntry e = en.nextElement();
                final String n = e.getName().replace('\\', '/');
                if (!n.startsWith(prefix) || n.length() == prefix.length()) continue;
                final Path out = root.resolve(n.substring(prefix.length())).normalize();
                if (!out.startsWith(root)) throw new IOException("unsafe path in zip: " + n);
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (InputStream in = zip.getInputStream(e)) {
                        Files.copy(in, out);
                    }
                }
            }
        }

        @Override
        public void close() throws IOException {
            zip.close();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        final ByteArrayOutputStream bo = new ByteArrayOutputStream();
        final byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0;) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = HdrPackPatcher.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing " + path);
            return new String(readAll(in), StandardCharsets.UTF_8).replace("\r\n", "\n");
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
