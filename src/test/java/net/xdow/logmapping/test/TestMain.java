package net.xdow.logmapping.test;

import net.xdow.logmapping.Launcher;

public class TestMain {


    public static int func(Object o) {
        return 1;
    }

    public static void test() {
        int i = 1;
        String bbb = "bbb";
        Exception e = null;
        Log.debug( "Hello World", 1, (Float) 1F, (float) 1F, 1D, (byte) 1, (short) 1,
                null, true, 1L, i, '1', new String[]{"1"}, new int[]{1}, new byte[]{0}, new Exception(), e, bbb,
                (long) func(1), 1 + 1, 1 - 1, 1 + "1", 1 + "111" + 2);
    }

    public static void main(String[] args) throws Exception {
        Launcher.main(new String[]{
                "-v",
                "--input-dir", "src/test/java",
                "--output-dir", "out/target/src/test/java",
                "--mapping-file", "out/target/mapping.txt",
                "--log-keyword", "net.xdow.logmapping.test.Log.debug",
        });
    }
}
