package com.github.braisdom.funcsql.annotations;

import com.github.braisdom.funcsql.generator.BasicMethodGenerator;
import com.github.braisdom.funcsql.generator.ClassImportable;
import com.github.braisdom.funcsql.generator.MethodGenerator;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Set;

@SupportedSourceVersion(value = SourceVersion.RELEASE_8)
@SupportedAnnotationTypes(value = {"com.github.braisdom.funcsql.annotations.DomainModel"})
public class AnnotationProcessor extends AbstractProcessor {

    private static final java.util.List<MethodGenerator> methodGenerators = new ArrayList<>();

    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;

    static {
        methodGenerators.add(new BasicMethodGenerator());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        this.trees = JavacTrees.instance(processingEnv);
        this.treeMaker = TreeMaker.instance(((JavacProcessingEnvironment) processingEnv).getContext());
        this.names = Names.instance(((JavacProcessingEnvironment) processingEnv).getContext());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(DomainModel.class);

        elements.forEach(element -> {
            JCTree jcTree = trees.getTree(element);
            final java.util.List<String> methodsCache = new ArrayList<>();
            final JCTree.JCCompilationUnit imports = (JCTree.JCCompilationUnit) trees.getPath(element).getCompilationUnit();

            jcTree.accept(new TreeTranslator() {

                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                    super.visitClassDef(jcClassDecl);
                    cacheMethod(methodsCache, jcClassDecl.defs);

                    for(MethodGenerator methodGenerator : methodGenerators) {
                        ClassImportable.ImportItem[] importItems = methodGenerator.getImportItems();
                        JCTree.JCMethodDecl[] jcMethodDecls = methodGenerator.generate(treeMaker, names, element);

                        processImport(imports, importItems);
                        processMethods(jcClassDecl, element, jcMethodDecls);
                    }
                }
            });
        });

        return true;
    }

    private void processMethods(JCTree.JCClassDecl jcClassDecl, Element element, JCTree.JCMethodDecl[] jcMethodDecls) {
        for (JCTree.JCMethodDecl jcMethodDecl : jcMethodDecls)
            jcClassDecl.defs = jcClassDecl.defs.append(jcMethodDecl);
    }

    private void processImport(JCTree.JCCompilationUnit imports, ClassImportable.ImportItem[] importItems) {
        for(ClassImportable.ImportItem importItem : importItems) {
            imports.defs = imports.defs.append(
                    treeMaker.Import(
                            treeMaker.Select(
                                    treeMaker.Ident(names.fromString(importItem.getPackageName())),
                                    names.fromString(importItem.getClassName())),
                            false)
            );
        }
    }

    private void cacheMethod(java.util.List<String> methodsCache, List<JCTree> defs) {
        for (JCTree def : defs) {
            if(def.getKind() == Tree.Kind.METHOD) {
                methodsCache.add(((JCTree.JCMethodDecl)def).getName().toString());
            }
        }
    }
}