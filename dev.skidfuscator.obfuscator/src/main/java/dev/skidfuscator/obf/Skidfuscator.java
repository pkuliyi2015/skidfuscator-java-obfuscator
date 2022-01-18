package dev.skidfuscator.obf;

import dev.skidfuscator.obf.directory.SkiddedDirectory;
import dev.skidfuscator.obf.init.DefaultInitHandler;
import dev.skidfuscator.obf.init.SkidSession;
import dev.skidfuscator.obf.utils.MapleJarUtil;
import org.mapleir.deob.PassGroup;


import java.io.File;
import java.time.Instant;
import java.util.*;

/**
 * @author Ghast
 * @since 21/01/2021
 * SkidfuscatorV2 © 2021
 */
public class Skidfuscator {
    public static Skidfuscator INSTANCE;

    private String[] args;


    public static Skidfuscator init(String[] args) {
        return new Skidfuscator(args);
    }

    public Skidfuscator(String[] args) {
        INSTANCE = this;
        this.args = args;
        this.init();
    }

    public Skidfuscator() {
        this(new String[0]);
    }

    // Temp workaround
    public static boolean preventDump = false;
    public static boolean expireTime = false;

    public void init() {
        if (args.length < 1) {
            System.out.println("Not valid command bro");
            System.exit(1);
            return;
        }
        TreeSet<String> parsedArgs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        parsedArgs.addAll(Arrays.asList(args));

        if(parsedArgs.contains("--antidump")) {
            preventDump = true;
        }

        if(parsedArgs.contains("--autoexpire")){
            expireTime = true;
        }

        final String[] logo = new String[] {
                "",
                "  /$$$$$$  /$$       /$$       /$$  /$$$$$$                                           /$$",
                " /$$__  $$| $$      |__/      | $$ /$$__  $$                                         | $$",
                "| $$  \\__/| $$   /$$ /$$  /$$$$$$$| $$  \\__//$$   /$$  /$$$$$$$  /$$$$$$$  /$$$$$$  /$$$$$$    /$$$$$$   /$$$$$$",
                "|  $$$$$$ | $$  /$$/| $$ /$$__  $$| $$$$   | $$  | $$ /$$_____/ /$$_____/ |____  $$|_  $$_/   /$$__  $$ /$$__  $$",
                " \\____  $$| $$$$$$/ | $$| $$  | $$| $$_/   | $$  | $$|  $$$$$$ | $$        /$$$$$$$  | $$    | $$  \\ $$| $$  \\__/",
                " /$$  \\ $$| $$_  $$ | $$| $$  | $$| $$     | $$  | $$ \\____  $$| $$       /$$__  $$  | $$ /$$| $$  | $$| $$",
                "|  $$$$$$/| $$ \\  $$| $$|  $$$$$$$| $$     |  $$$$$$/ /$$$$$$$/|  $$$$$$$|  $$$$$$$  |  $$$$/|  $$$$$$/| $$",
                " \\______/ |__/  \\__/|__/ \\_______/|__/      \\______/ |_______/  \\_______/ \\_______/   \\___/   \\______/ |__/",
                "",
                "                       Author: Ghast     Version: 1.0.8     Today: " + new Date(Instant.now().toEpochMilli()).toGMTString(),
                "",
                ""
        };

        for (String s : logo) {
            System.out.println(s);
        }


        final File file = new File(args[0]);
        start(file);
    }

    public static File start(final File file) {
        final SkiddedDirectory directory = new SkiddedDirectory(null);
        directory.init();

        final File out = new File(file.getPath() + "-out.jar");
        final SkidSession session = new DefaultInitHandler().init(file, out);
        try {
            MapleJarUtil.dumpJar(session.getClassSource(), session.getJarDownloader(), new PassGroup("Output"),
                    session.getOutputFile().getPath());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return out;
    }
}
