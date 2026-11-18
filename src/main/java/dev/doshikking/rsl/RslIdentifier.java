package dev.doshikking.rsl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class RslIdentifier extends ASTWrapperPsiElement implements PsiNameIdentifierOwner {
    RslIdentifier(@NotNull ASTNode node) {
        super(node);
    }

    static RslAnalysis.Result analysis(PsiFile file) {
        return CachedValuesManager.getCachedValue(file,
                () -> CachedValueProvider.Result.create(RslAnalysis.inspect(file.getViewProvider().getContents()), file));
    }

    boolean isDeclaration() {
        return analysis(getContainingFile()).declarations().contains(getTextOffset());
    }

    @Override
    public @Nullable String getName() {
        return isDeclaration() ? getText() : null;
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        return isDeclaration() ? getFirstChild() : null;
    }

    @Override
    public PsiElement setName(@NotNull String name) {
        throw new IncorrectOperationException("RSL rename is unavailable");
    }

    @Override
    public @NotNull SearchScope getUseScope() {
        return new LocalSearchScope(getContainingFile());
    }

    @Override
    public @Nullable PsiReference getReference() {
        if (!analysis(getContainingFile()).references().containsKey(getTextOffset())) return null;
        return new PsiReferenceBase<RslIdentifier>(this, TextRange.from(0, getTextLength())) {
            @Override
            public @Nullable PsiElement resolve() {
                PsiFile file = myElement.getContainingFile();
                Integer offset = analysis(file).references().get(myElement.getTextOffset());
                if (offset == null) return null;
                PsiElement leaf = file.findElementAt(offset);
                return leaf != null && leaf.getParent() instanceof RslIdentifier identifier ? identifier : null;
            }
        };
    }
}
