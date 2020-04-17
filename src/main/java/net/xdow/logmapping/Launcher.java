package net.xdow.logmapping;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Switch;
import net.xdow.logmapping.util.L;

public class Launcher {

    private static String getVersionMessage() {
        return "LogProcessor version 1.0.5";
    }

    public static void main(String[] args) throws Exception {

        JSAP jsap = new JSAP();

        jsap.registerParameter(new FlaggedOption("input-dir")
                .setStringParser(JSAP.STRING_PARSER)
                .setRequired(true)
                .setAllowMultipleDeclarations(true)
                .setShortFlag('i')
                .setLongFlag("input-dir")
                .setHelp("(Multiple)java project root directory"));

        jsap.registerParameter(new FlaggedOption("output-dir")
                .setStringParser(JSAP.STRING_PARSER)
                .setRequired(true)
                .setShortFlag('o')
                .setLongFlag("output-dir")
                .setHelp("output destination directory"));

        jsap.registerParameter(new FlaggedOption("mapping-file")
                .setStringParser(JSAP.STRING_PARSER)
                .setRequired(true)
                .setShortFlag('m')
                .setLongFlag("mapping-file")
                .setHelp("mapping.txt destination file"));

        jsap.registerParameter(new FlaggedOption("log-keyword")
                .setStringParser(JSAP.STRING_PARSER)
                .setRequired(true)
                .setAllowMultipleDeclarations(true)
                .setShortFlag('k')
                .setLongFlag("log-keyword")
                .setHelp("(Multiple)log code identifier keyword, ex: com.Log.debug"));

        jsap.registerParameter(new FlaggedOption("jobs")
                .setStringParser(JSAP.INTEGER_PARSER)
                .setRequired(true)
                .setShortFlag('j')
                .setLongFlag("jobs")
                .setDefault(String.valueOf(Runtime.getRuntime().availableProcessors()))
                .setHelp("Allow N jobs at once"));

        jsap.registerParameter(new Switch("verbose")
                .setShortFlag('v')
                .setLongFlag("verbose")
                .setHelp("print debugging messages about its progress"));

        jsap.registerParameter(new Switch("help").setShortFlag('h').setLongFlag("help").setDefault("false"));

        JSAPResult arguments = jsap.parse(args);
        if (!arguments.success()) {
            // print out specific error messages describing the problems
            for (java.util.Iterator<?> errs = arguments.getErrorMessageIterator(); errs.hasNext(); ) {
                System.err.println("Error: " + errs.next());
            }
            System.err.println();
        }
        if (!arguments.success() || arguments.getBoolean("help")) {
            System.err.println(getVersionMessage());
            System.err.println("Obfuscate and transform log constant code like Proguard");
            System.err.println("Copyright (C) 2020 by Jason Eric");
            System.err.println("Web site: https://github.com/eritpchy/log-mapping-processor");
            System.err.println();
            System.err.println("Usage: java -jar <launcher name> [option(s)]");
            System.err.println();
            System.err.println("Options : ");
            System.err.println();
            System.err.println(jsap.getHelp());
            System.exit(-1);
        }

        String[] inputDirPaths = arguments.getStringArray("input-dir");
        String outputDirPath = arguments.getString("output-dir");
        String mappingFilePath = arguments.getString("mapping-file");
        String[] keywords = arguments.getStringArray("log-keyword");
        boolean verbose = arguments.getBoolean("verbose", false);
        L.setVerbose(verbose);
        int parserThreadCount = arguments.getInt("jobs");
        new LogProcessor(keywords)
                .setParserThreadCount(parserThreadCount)
                .run(inputDirPaths, outputDirPath, mappingFilePath);
    }
}
