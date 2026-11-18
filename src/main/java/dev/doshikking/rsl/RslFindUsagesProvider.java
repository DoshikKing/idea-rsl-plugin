package dev.doshikking.rsl;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RslFindUsagesProvider implements FindUsagesProvider {
    @Override
    public @NotNull WordsScanner getWordsScanner() {
        var parser = new RslParserDefinition();
        return new DefaultWordsScanner(parser.createLexer(null), RslParserDefinition.identifiers(),
                parser.getCommentTokens(), parser.getStringLiteralElements());
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement element) {
        return element instanceof RslIdentifier identifier && identifier.isDeclaration();
    }

    @Override
    public @Nullable String getHelpId(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public @NotNull String getType(@NotNull PsiElement element) {
        return "RSL symbol";
    }

    @Override
    public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        return element.getText();
    }

    @Override
    public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return element.getText();
    }
}
