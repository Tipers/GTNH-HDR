package com.gtnhhdr.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.launchwrapper.Launch;

/**
 * logs/GTNH-HDR.log, written synchronously. Window creation happens before the game can show errors, and a failure
 * there kills the JVM before log4j output is complete, so the startup steps are also recorded here.
 */
public final class HdrEarlyLog {

    private static PrintWriter out;

    private HdrEarlyLog() {}

    public static synchronized void log(String msg) {
        try {
            if (out == null) {
                final File dir = new File(Launch.minecraftHome != null ? Launch.minecraftHome : new File("."), "logs");
                dir.mkdirs();
                out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(dir, "GTNH-HDR.log")), StandardCharsets.UTF_8),
                    true);
            }
            out.println("[" + new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + "] " + msg);
        } catch (Throwable ignored) {}
    }

    public static void log(String msg, Throwable t) {
        log(msg + ": " + t);
        synchronized (HdrEarlyLog.class) {
            if (out != null) t.printStackTrace(out);
        }
    }
}
