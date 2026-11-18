package dev.doshikking.rsl;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class RslAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiFile file)) return;
        for (RslAnalysis.Problem problem : RslAnalysis.analyze(file.getViewProvider().getContents())) {
            holder.newAnnotation(HighlightSeverity.ERROR, problem.message())
                    .range(new TextRange(problem.start(), problem.end()))
                    .create();
        }
    }
}
