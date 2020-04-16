package net.xdow.logmapping.test;

public class Log {

    public static void debug(Object... arg) {
        // see net.xdow.logmapping.LogProcessor.ENCODED_FLAG_INDEX_MASK
        int mask = 0x0000001;
        for (int i = 0; i < arg.length; i++) {
            Object object = arg[i];
            if (i == 1) {
                System.out.print(':');
            } else if (i == 2) {
                System.out.print('@');
            } else if (i > 3) {
                int argIndex = i - 4;
                int encodedFlag = Integer.parseInt(String.valueOf(arg[3]));
                boolean isEncoded = (encodedFlag & (mask << argIndex)) == (mask << argIndex);
                if (isEncoded) {
                    System.out.print('@');
                }
            }
            System.out.print(object);
            if (i != 0) {
                System.out.print('\t');
            }
        }
        System.out.print('\n');
    }
}
