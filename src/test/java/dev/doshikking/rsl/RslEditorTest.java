package dev.doshikking.rsl;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class RslEditorTest extends BasePlatformTestCase {
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
