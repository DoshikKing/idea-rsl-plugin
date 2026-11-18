package dev.doshikking.rsl;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RslAnnotator extends ExternalAnnotator<String, List<RslAnalysis.Problem>> implements DumbAware {
    @Override
    public @NotNull String collectInformation(@NotNull PsiFile file) {
        return file.getText();
    }

    @Override
    public @NotNull List<RslAnalysis.Problem> doAnnotate(String source) {
        return RslAnalysis.analyze(source);
    }

    @Override
    public void apply(@NotNull PsiFile file, List<RslAnalysis.Problem> problems, @NotNull AnnotationHolder holder) {
        if (problems.isEmpty()) return;

        var scheme = EditorColorsManager.getInstance().getGlobalScheme();
        var underline = scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES);
        var referenceStyle = scheme.getAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
        var attributes = underline == null ? null : underline.clone();
        if (attributes != null) {
            var foreground = referenceStyle != null ? referenceStyle.getForegroundColor() : null;
            if (foreground == null) foreground = attributes.getEffectColor();
            attributes.setForegroundColor(foreground);
        }

        for (RslAnalysis.Problem problem : problems) {
            var annotation = holder.newAnnotation(HighlightSeverity.ERROR, problem.message())
                    .range(new TextRange(problem.start(), problem.end()));
            if (attributes != null) annotation.enforcedTextAttributes(attributes);
            annotation.create();
        }
    }
}
