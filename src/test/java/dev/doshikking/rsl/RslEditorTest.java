package dev.doshikking.rsl;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.actionSystem.IdeActions;
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

    public void testDiagnosticsRefreshAfterEdits() {
        myFixture.configureByText("editing.mac", "var amount = 1<caret>;\namount = 2;");
        assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE);
        var errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
        assertEquals(errors.toString(), 1, errors.size());
        assertEquals("Ожидается «;» между инструкциями", errors.getFirst().getDescription());

        myFixture.type(';');
        assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
    }

    public void testStructuralDiagnosticsInEditor() {
        myFixture.configureByText("structure.mac", """
                macro run()
                  if ()
                    print(1 2);
                  else
                    print(3);
                  elif (false)
                    print(4);
                  end
                """);
        var descriptions = myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
                .map(info -> info.getDescription()).toList();
        assertEquals(4, descriptions.size());
        assertTrue(descriptions.toString(), descriptions.stream().anyMatch(text -> text.contains("выражение в условии")));
        assertTrue(descriptions.toString(), descriptions.stream().anyMatch(text -> text.contains("списка аргументов")));
        assertTrue(descriptions.toString(), descriptions.stream().anyMatch(text -> text.contains("перед «else»")));
        assertTrue(descriptions.toString(), descriptions.stream().anyMatch(text -> text.contains("«macro» не закрыт")));
    }

    public void testWarnsAboutAssignmentInConditions() {
        String source = """
                if (value = 1)
                  value = 2;
                elif (value != 2)
                  while ((value = 3) and ready)
                    value = 4
                  end;
                elif (value = 5)
                  value = 6;
                elif (value == 7)
                  value = 8;
                end;
                """;
        myFixture.configureByText("conditions.mac", source);
        var warnings = myFixture.doHighlighting(HighlightSeverity.WARNING);
        assertEquals(warnings.toString(), 3, warnings.size());
        assertEquals(source.indexOf("= 1"), warnings.get(0).getStartOffset());
        assertEquals(source.indexOf("= 3"), warnings.get(1).getStartOffset());
        assertEquals(source.indexOf("= 5"), warnings.get(2).getStartOffset());
        for (var warning : warnings) {
            assertEquals(HighlightSeverity.WARNING, warning.getSeverity());
            assertEquals(warning.getStartOffset() + 1, warning.getEndOffset());
            assertTrue(warning.getDescription().contains("«==»"));
        }
    }

    public void testWarnsAboutMissingSemicolonAtEndOfIfBranch() {
        String source = "if(Type != null)\u2028     javaObj.setType(Type)\u2028end;";
        myFixture.configureByText("branch.mac", source);
        var warnings = myFixture.doHighlighting(HighlightSeverity.WARNING);
        assertEquals(warnings.toString(), 1, warnings.size());
        var warning = warnings.getFirst();
        assertEquals(HighlightSeverity.WARNING, warning.getSeverity());
        assertEquals(source.lastIndexOf(')'), warning.getStartOffset());
        assertEquals("Добавьте «;» в конце инструкции в ветке «if»", warning.getDescription());

        myFixture.configureByText("terminated.mac", "if (Type != null)\n javaObj.setType(Type);\nelse\nend;");
        assertEmpty(myFixture.doHighlighting(HighlightSeverity.WARNING));
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
