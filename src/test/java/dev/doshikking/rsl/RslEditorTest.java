package dev.doshikking.rsl;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class RslEditorTest extends BasePlatformTestCase {
    public void testNavigationAndUsagesRespectScopes() {
        String source = """
                var amount = 1;
                macro calc(amount)
                  return amount
                end;
                AM<caret>OUNT = amount + obj.amount;
                """;
        myFixture.configureByText("symbols.mac", source);
        var declaration = TargetElementUtil.findTargetElement(
                myFixture.getEditor(), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
        assertNotNull(declaration);
        assertEquals(source.indexOf("amount"), declaration.getTextOffset());
        var usages = myFixture.findUsages(declaration);
        assertEquals(usages.toString(), 2, usages.size());
    }

    public void testMissingSemicolonAcrossLines() {
        String source = "var amount = 1\n\namount = 2;";
        myFixture.configureByText("separator.mac", source);
        var errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
        assertEquals(errors.toString(), 1, errors.size());
        var problem = errors.getFirst();
        assertEquals(source.lastIndexOf("amount"), problem.getStartOffset());
        assertEquals("Ожидается «;» между инструкциями", problem.getDescription());
        var attributes = problem.getTextAttributes(myFixture.getFile(), EditorColorsManager.getInstance().getGlobalScheme());
        assertNotNull(attributes.getForegroundColor());
        assertNotNull(attributes.getEffectColor());

        myFixture.configureByText("valid.mac", "macro run()\nvar amount = 1;\namount = 2\nend");
        assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
    }

    public void testIndexedAssignment() {
        myFixture.configureByText("indexed.mac",
                "supplierBillID[supplierBillID.size] = javaObj.supplierBillID.get(i);");
        assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
    }

    public void testHighlightsAndAnalyzesMacFile() {
        String source = "CoNsT LIMIT = 5;\nlimit = 7;";
        var file = myFixture.configureByText("sample.mac", source);
        assertSame(RslFileType.INSTANCE, file.getFileType());

        var highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(
                file.getLanguage(), getProject(), file.getVirtualFile());
        assertNotNull(highlighter);
        var lexer = highlighter.getHighlightingLexer();
        lexer.start(source);
        assertEquals("RSL_KEYWORD", highlighter.getTokenHighlights(lexer.getTokenType())[0].getExternalName());

        var errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
        assertEquals(errors.toString(), 1, errors.size());
        var problem = errors.getFirst();
        assertEquals("limit", source.substring(problem.getStartOffset(), problem.getEndOffset()));
        assertTrue(problem.getDescription(), problem.getDescription().startsWith("Нельзя изменить"));
    }
}
