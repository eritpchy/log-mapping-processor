package net.xdow.logmapping.test;

import net.xdow.logmapping.Launcher;

public class TestMain {

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
