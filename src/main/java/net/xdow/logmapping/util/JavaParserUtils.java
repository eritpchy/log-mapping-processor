package net.xdow.logmapping.util;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;

import java.util.concurrent.atomic.AtomicReference;


public class JavaParserUtils {

    /**
     *
     * @param expression
     * @return Nullable
     */
    public static ClassOrInterfaceDeclaration getParentClass(Expression expression) {
        AtomicReference<ClassOrInterfaceDeclaration> parent = new AtomicReference<>();
        expression.walk(Node.TreeTraversal.PARENTS, node -> {
            if (node instanceof ClassOrInterfaceDeclaration) {
                parent.set((ClassOrInterfaceDeclaration) node);
            }
        });
        return parent.get();
    }

    /**
     *
     * @param expression
     * @return Nullable
     */
    public static MethodDeclaration getParentMethod(Expression expression) {
        AtomicReference<MethodDeclaration> parent = new AtomicReference<>();
        expression.walk(Node.TreeTraversal.PARENTS, node -> {
            if (node instanceof MethodDeclaration) {
                parent.set((MethodDeclaration) node);
            }
        });
        return parent.get();
    }
}
