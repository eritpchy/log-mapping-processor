package net.xdow.logmapping.util;

public class L {

    private final static String TAG = "LogMapping";

    private static boolean sVerbose = false;

    public static void d(String message) {
        if (!sVerbose) {
            return;
        }
        System.out.print(TAG);
        System.out.print(": ");
        System.out.println(message);
    }

    public static void setVerbose(boolean verbose) {
        sVerbose = verbose;
    }
}
