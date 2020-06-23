package net.xdow.logmapping;

import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import net.xdow.logmapping.bean.MappingInfo;
import net.xdow.logmapping.util.EncodeUtils;
import net.xdow.logmapping.util.JavaParserUtils;
import net.xdow.logmapping.util.L;
import net.xdow.logmapping.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.FileVisitResult.CONTINUE;

public class LogProcessor {

    private final static int ENCODED_FLAG_INDEX_MASK = 0x000001;

    private final Set<String> mKeywordSet = new HashSet<>();
    private final Set<String> mImportKeywordSet = new HashSet<>();
    private final Set<String> mPackageKeywordSet = new HashSet<>();
    private int mParserThreadCount = 0;
    private AtomicInteger mProcessRefMagicIndex = new AtomicInteger(0);

    public LogProcessor(String[] keywords) {
        for (String keyword : keywords) {
            mKeywordSet.add(keyword.replaceAll("(.*\\.)(\\w+\\.\\w+$)", "$2"));
            mImportKeywordSet.add(keyword.replaceAll("(.*)(\\.\\w+$)", "$1"));
            mPackageKeywordSet.add(keyword.replaceAll("(.*)(\\.\\w+)(\\.\\w+$)", "$1"));
        }
    }

    public LogProcessor setParserThreadCount(int threadCount) {
        mParserThreadCount = threadCount;
        return this;
    }

    private class MethodCallVisitor {

        private final HashMap<Integer, MappingInfo> mEncodedMap;

        public MethodCallVisitor(HashMap<Integer, MappingInfo> encodedMap) {
            mEncodedMap = encodedMap;
        }

        public void visit(MethodCallExpr methodCallExpr, HashMap<String, String> processedMap) {
            int encodedIndexFlag = 0;
            NodeList<Expression> newArgList = new NodeList<>();
            NodeList<Expression> newPreArgList = new NodeList<>();
            NodeList<Expression> originArgList = methodCallExpr.getArguments();
            for (int i = 0; i < originArgList.size(); i++) {
                Expression originArgExpr = originArgList.get(i);
                if (process(newArgList, originArgExpr)) {
                    encodedIndexFlag |= (ENCODED_FLAG_INDEX_MASK << i);
                }
            }

            //class
            String classExprString = Optional.ofNullable(JavaParserUtils.getParentClass(methodCallExpr)).map(v -> v.getNameAsString() + ".class").orElse("\"\"");
            newPreArgList.add(StaticJavaParser.parseExpression(classExprString));
            //line
            String lineExprString = String.valueOf(methodCallExpr.getRange().map(v -> v.begin.line).orElse(0));
            newPreArgList.add(StaticJavaParser.parseExpression(lineExprString));
            //method - encoded
            String methodDeclString = Optional.ofNullable(JavaParserUtils.getParentMethod(methodCallExpr))
                    .map(v -> v.getDeclarationAsString(false, false, false)
                            .replaceAll(".+ ", ""))
                    .orElse("\"\"");
            int methodDeclStringHash = EncodeUtils.encode(mEncodedMap, methodDeclString);
            EncodeUtils.addToEncodedMap(mEncodedMap, methodDeclStringHash, new MappingInfo(MappingInfo.Type.MethodDecl, methodDeclString));
            newPreArgList.add(StaticJavaParser.parseExpression(String.valueOf(methodDeclStringHash)));
            //encodedIndexFlag
            newPreArgList.add(StaticJavaParser.parseExpression(String.valueOf(encodedIndexFlag)));

            // Preserving line number, there is an bug in LexicalPreservingPrinter, while using methodCallExpr.setArguments
            // application will stuck in com.github.javaparser.printer.lexicalpreservation.DifferenceElementCalculator.java:152
            newArgList.addAll(0, newPreArgList);
            MethodCallExpr clone = new MethodCallExpr(methodCallExpr.getScope().orElse(null), methodCallExpr.getNameAsString());;
            clone.setArguments(newArgList);
            int extractLineCount = methodCallExpr.getRange().map(Range::getLineCount).orElse(1) - 1;
            String extractLineBreak = extractLineCount > 0 ? StringUtils.repeat("\n", extractLineCount) : "";
            methodCallExpr.addArgument(buildProcessMagicIndexKey());
            processedMap.put(LexicalPreservingPrinter.print(methodCallExpr), clone.toString() + extractLineBreak);
        }

        /**
         * @param newArgList
         * @param expression
         * @return true - encoded
         */
        private boolean process(NodeList<Expression> newArgList, Expression expression) {
            if (expression instanceof StringLiteralExpr) {
                String value = ((StringLiteralExpr) expression).getValue();
                int hash = EncodeUtils.encode(mEncodedMap, value);
                EncodeUtils.addToEncodedMap(mEncodedMap, hash, new MappingInfo(MappingInfo.Type.String, value));
                newArgList.add(StaticJavaParser.parseExpression(String.valueOf(hash)));
                return true;
            } else if (expression instanceof IntegerLiteralExpr) {
                String value = ((IntegerLiteralExpr) expression).getValue();
                int hash = EncodeUtils.encode(mEncodedMap, value);
                EncodeUtils.addToEncodedMap(mEncodedMap, hash, new MappingInfo(MappingInfo.Type.Integer, value));
                newArgList.add(StaticJavaParser.parseExpression(String.valueOf(hash)));
                return true;
            } else if (expression instanceof DoubleLiteralExpr) {
                String value = ((DoubleLiteralExpr) expression).getValue();
                int hash = EncodeUtils.encode(mEncodedMap, value);
                EncodeUtils.addToEncodedMap(mEncodedMap, hash, new MappingInfo(MappingInfo.Type.Double, value));
                newArgList.add(StaticJavaParser.parseExpression(String.valueOf(hash)));
                return true;
            } else if (expression instanceof NullLiteralExpr) {
                String value = "null";
                int hash = EncodeUtils.encode(mEncodedMap, value);
                EncodeUtils.addToEncodedMap(mEncodedMap, hash, new MappingInfo(MappingInfo.Type.Null, value));
                newArgList.add(StaticJavaParser.parseExpression(String.valueOf(hash)));
                return true;
            } else if (expression instanceof BooleanLiteralExpr) {
                String value = String.valueOf(((BooleanLiteralExpr) expression).getValue());
                int hash = EncodeUtils.encode(mEncodedMap, value);
                EncodeUtils.addToEncodedMap(mEncodedMap, hash, new MappingInfo(MappingInfo.Type.Boolean, value));
                newArgList.add(StaticJavaParser.parseExpression(String.valueOf(hash)));
                return true;
            } else if (expression instanceof CharLiteralExpr) {
                String value = ((CharLiteralExpr) expression).getValue();
                int hash = EncodeUtils.encode(mEncodedMap, value);
                EncodeUtils.addToEncodedMap(mEncodedMap, hash, new MappingInfo(MappingInfo.Type.Char, value));
                newArgList.add(StaticJavaParser.parseExpression(String.valueOf(hash)));
                return true;
            } else if (expression instanceof LongLiteralExpr) {
                String value = ((LongLiteralExpr) expression).getValue();
                int hash = EncodeUtils.encode(mEncodedMap, value);
                EncodeUtils.addToEncodedMap(mEncodedMap, hash, new MappingInfo(MappingInfo.Type.Long, value));
                newArgList.add(StaticJavaParser.parseExpression(String.valueOf(hash)));
                return true;
            } else if (expression instanceof LiteralExpr) {
                String value = expression.toString();
                int hash = EncodeUtils.encode(mEncodedMap, value);
                EncodeUtils.addToEncodedMap(mEncodedMap, hash, new MappingInfo(MappingInfo.Type.Unknown, value));
                newArgList.add(StaticJavaParser.parseExpression(String.valueOf(hash)));
                return true;
            } else if (expression instanceof CastExpr) {
                List<Expression> childList = expression.getChildNodesByType(Expression.class);
                if (childList.size() > 0) {
                    if (process(newArgList, childList.get(0))) {
                        return true;
                    }
                }
                newArgList.add(expression);
            } else if (expression instanceof ArrayCreationExpr) {
                String value = expression.toString();
                int hash = EncodeUtils.encode(mEncodedMap, value);
                EncodeUtils.addToEncodedMap(mEncodedMap, hash, new MappingInfo(MappingInfo.Type.Array, value));
                newArgList.add(StaticJavaParser.parseExpression(String.valueOf(hash)));
                return true;
            } else if (expression instanceof BinaryExpr) {
                if (expression.findAll(LiteralExpr.class).size() > 0) {
                    if (expression.findAll(StringLiteralExpr.class).size() > 0) {
                        process((BinaryExpr) expression);
                    } else {
                        String position = JavaParserUtils.getParentClass(expression).getFullyQualifiedName().orElse(null) + ".java:"
                                + expression.getRange().map(v -> v.begin.line).orElse(0);
                        System.err.println("WARN: BinaryExpr will not transform! expression: " + expression.toString()
                                + " on: " + position);
                    }
                }
                newArgList.add(expression);
            } else {
                newArgList.add(expression);
            }
            return false;
        }

        private void process(BinaryExpr binaryExpr) {
            Expression leftBinaryExpr = binaryExpr.getLeft();
            if (leftBinaryExpr instanceof StringLiteralExpr) {
                String value = leftBinaryExpr.toString();
                int hash = EncodeUtils.encode(mEncodedMap, value);
                EncodeUtils.addToEncodedMap(mEncodedMap, hash, new MappingInfo(MappingInfo.Type.String, value));
                binaryExpr.setLeft(StaticJavaParser.parseExpression("\"\t@" + hash + "\t\""));
            } else if (leftBinaryExpr instanceof BinaryExpr) {
                process((BinaryExpr) leftBinaryExpr);
            }
            Expression rightBinaryExpr = binaryExpr.getRight();
            if (rightBinaryExpr instanceof StringLiteralExpr) {
                String value = rightBinaryExpr.toString();
                int hash = EncodeUtils.encode(mEncodedMap, value);
                EncodeUtils.addToEncodedMap(mEncodedMap, hash, new MappingInfo(MappingInfo.Type.String, value));
                binaryExpr.setRight(StaticJavaParser.parseExpression("\"\t@" + hash + "\t\""));
            } else if (rightBinaryExpr instanceof BinaryExpr) {
                process((BinaryExpr) rightBinaryExpr);
            }
        }
    }

    public void run(String[] inputDirPaths, String outputDirPath, String mappingFilePath) throws IOException {
        L.d("processor inputDirPath: " + Arrays.toString(inputDirPaths) + " outputDirPath: " + outputDirPath + " mappingFilePath: " + mappingFilePath);
        HashMap<Integer, MappingInfo> sEncodedMap = new HashMap<>();
        try {
            ExecutorService pool = Executors.newFixedThreadPool(mParserThreadCount <= 0 ? Runtime.getRuntime().availableProcessors() : mParserThreadCount);
            for (String inputDirPath : inputDirPaths) {
                Path rootPath = Paths.get(inputDirPath);
                Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                        if (!path.toString().toLowerCase().endsWith(".java")) {
                            return CONTINUE;
                        }
                        pool.submit(() -> {
                            try {
                                L.d("processing file: " + path);
                                Path localPath = rootPath.relativize(path);
                                CompilationUnit compilationUnit = StaticJavaParser.parse(path);
                                AtomicReference<Boolean> lexicalPreservingPrinterSetupRef = new AtomicReference<>(false);
                                AtomicReference<Boolean> foundRef = new AtomicReference<>(false);
                                MethodCallVisitor visitor = new MethodCallVisitor(sEncodedMap);
                                HashMap<String, String> processedMap = new HashMap<>();
                                compilationUnit.findAll(MethodCallExpr.class, methodCallExpr -> {
                                    AtomicReference<Boolean> reference = new AtomicReference<>(false);
                                    //test target package == log package
                                    compilationUnit.getPackageDeclaration().ifPresent(packageDeclaration -> {
                                        if (mPackageKeywordSet.contains(packageDeclaration.getNameAsString())) {
                                            reference.set(true);
                                        }
                                    });
                                    if (!reference.get()) {
                                        //test target package -> log import
                                        compilationUnit.getImports().forEach(importDeclaration -> {
                                            if (mImportKeywordSet.contains(importDeclaration.getNameAsString())) {
                                                reference.set(true);
                                            }
                                        });
                                    }
                                    if (!reference.get()) {
                                        return false;
                                    }

                                    String methodDeclName = methodCallExpr.getScope().map(Node::toString).orElse("")
                                            + "." + methodCallExpr.getNameAsString();
                                    if (!mKeywordSet.contains(methodDeclName)) {
                                        return false;
                                    }
                                    foundRef.set(true);
                                    return true;
                                }).forEach(methodCallExpr -> {
                                    try {
                                        if (!lexicalPreservingPrinterSetupRef.get()) {
                                            lexicalPreservingPrinterSetupRef.set(true);
                                            LexicalPreservingPrinter.setup(compilationUnit);
                                        }

                                        String className = rootPath.relativize(path).toString().replaceAll("[\\/]", ".");
                                        String pos = className + ":" + methodCallExpr.getRange().map(range -> range.begin.line).orElse(-1);
                                        L.d("processing " + methodCallExpr.toString() + " on: " + pos);
                                        visitor.visit(methodCallExpr, processedMap);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        System.exit(-1);
                                    }
                                });
                                Path outputPath = Paths.get(outputDirPath).resolve(localPath);
                                outputPath.toFile().getParentFile().mkdirs();
                                if (lexicalPreservingPrinterSetupRef.get()) {
                                    compilationUnit.setStorage(outputPath, StandardCharsets.UTF_8);
                                    compilationUnit.getStorage().ifPresent(storage -> storage.save(cu -> {
                                        try {
                                            // Preserving line number
                                            String java = LexicalPreservingPrinter.print(cu);
                                            Iterator<Map.Entry<String, String>> it = processedMap.entrySet().iterator();
                                            while(it.hasNext()) {
                                                Map.Entry<String, String> entry = it.next();
                                                String originalCode = entry.getKey();
                                                String replacedCode = entry.getValue();
                                                java = java.replace(originalCode, replacedCode);
                                            }
                                            return java;
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            System.exit(-1);
                                            return null;
                                        }
                                    }));
                                } else {
                                    Files.deleteIfExists(outputPath);
                                    Files.copy(path, outputPath);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.exit(-1);
                            }
                        });
                        return CONTINUE;
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            EncodeUtils.saveEncodedMap(sEncodedMap, new File(mappingFilePath));
        }
    }

    public String buildProcessMagicIndexKey() {
        return "\"magic_index_" + mProcessRefMagicIndex.addAndGet(1) + "\"";
    }
}
