package net.xdow.logmapping.util;

public class StringUtils {


    public static String repeat(String str, int repeat) {
        if (str == null) {
            return null;
        } else if (repeat <= 0) {
            return "";
        } else {
            int inputLength = str.length();
            if (repeat != 1 && inputLength != 0) {
                if (inputLength == 1 && repeat <= 8192) {
                    return repeat(str.charAt(0), repeat);
                } else {
                    int outputLength = inputLength * repeat;
                    switch (inputLength) {
                        case 1:
                            return repeat(str.charAt(0), repeat);
                        case 2:
                            char ch0 = str.charAt(0);
                            char ch1 = str.charAt(1);
                            char[] output2 = new char[outputLength];

                            for (int i = repeat * 2 - 2; i >= 0; --i) {
                                output2[i] = ch0;
                                output2[i + 1] = ch1;
                                --i;
                            }

                            return new String(output2);
                        default:
                            StringBuilder buf = new StringBuilder(outputLength);

                            for (int i = 0; i < repeat; ++i) {
                                buf.append(str);
                            }

                            return buf.toString();
                    }
                }
            } else {
                return str;
            }
        }
    }

    public static String repeat(char ch, int repeat) {
        char[] buf = new char[repeat];

        for (int i = repeat - 1; i >= 0; --i) {
            buf[i] = ch;
        }

        return new String(buf);
    }

}
