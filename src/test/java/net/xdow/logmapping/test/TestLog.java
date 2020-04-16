package net.xdow.logmapping.test;

public class TestLog {


    public static int func(Object o) {
        return 1;
    }

    public static void test() {
        int i = 1;
        String bbb = "bbb";
        Exception e = null;
        //asdsda
        Log.debug( "Hello World" /**/, 1, (Float) 1F, (float) 1F, 1D, (byte) 1, (short) 1,
                null, true, 1L, i, '1', new String[]{"1"}, new int[]{1}, new byte[]{0}, new Exception(), e, bbb,
                (long) func(1), 1 + 1, 1 - 1, 1 + "1", 1 + "111" + 2);
    }



    public static int func1(Object o) {
        return 1;
    }



    public static int func2(Object o) {
        return 1;
    }

}
