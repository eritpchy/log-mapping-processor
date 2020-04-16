package net.xdow.logmapping;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.printer.PrettyPrinter;
import net.xdow.logmapping.bean.MappingInfo;
import net.xdow.logmapping.util.EncodeUtils;
import net.xdow.logmapping.util.JavaParserUtils;
import net.xdow.logmapping.util.L;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.FileVisitResult.CONTINUE;

public class LogProcessor {

    private final static int ENCODED_FLAG_INDEX_MASK = 0x000001;

    private final Set<String> mKeywordSet = new HashSet<>();
    private final Set<String> mImportKeywordSet = new HashSet<>();
    private final Set<String> mPackageKeywordSet = new HashSet<>();
    private int mParserThreadCount = 0;

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

        public void visit(MethodCallExpr methodCallExpr) {
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

            newArgList.addAll(0, newPreArgList);
            methodCallExpr.setArguments(newArgList);
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

                                MethodCallVisitor visitor = new MethodCallVisitor(sEncodedMap);
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
                                    return true;
                                }).forEach(methodCallExpr -> {
                                    try {
                                        String className = rootPath.relativize(path).toString().replaceAll("[\\/]", ".");
                                        String pos = className + ":" + methodCallExpr.getRange().map(range -> range.begin.line).orElse(-1);
                                        L.d("processing " + methodCallExpr.toString() + " on: " + pos);
                                        visitor.visit(methodCallExpr);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        System.exit(-1);
                                    }
                                });
                                compilationUnit.setStorage(Paths.get(outputDirPath).resolve(localPath), StandardCharsets.UTF_8);
                                compilationUnit.getStorage().ifPresent(storage -> storage.save(new PrettyPrinter()::print));
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
}