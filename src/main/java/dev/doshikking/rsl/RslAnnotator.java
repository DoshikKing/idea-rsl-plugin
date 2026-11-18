package dev.doshikking.rsl;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import java.awt.Color;
import org.jetbrains.annotations.NotNull;

public final class RslAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiFile file)) return;
        var problems = RslIdentifier.analysis(file).problems();
        if (problems.isEmpty()) return;

        var scheme = EditorColorsManager.getInstance().getGlobalScheme();
        var attributes = scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).clone();
        Color foreground = scheme.getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES).getForegroundColor();
        if (foreground == null) foreground = attributes.getEffectColor();
        attributes.setForegroundColor(foreground != null ? foreground : JBColor.RED);

        for (RslAnalysis.Problem problem : problems) {
            holder.newAnnotation(HighlightSeverity.ERROR, problem.message())
                    .range(new TextRange(problem.start(), problem.end()))
                    .enforcedTextAttributes(attributes)
                    .create();
        }
    }
}
