package dev.doshikking.rsl;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import dev.doshikking.rsl.RslTokens.Kind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;
import static dev.doshikking.rsl.RslParserDefinition.TYPES;

public final class RslHighlighterFactory extends SyntaxHighlighterFactory {
    @Override
    public @NotNull SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile file) {
        return new Highlighter();
    }

    static final class Highlighter extends SyntaxHighlighterBase {
        static final TextAttributesKey KEYWORD = createTextAttributesKey("RSL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
        static final TextAttributesKey TYPE = createTextAttributesKey("RSL_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME);
        static final TextAttributesKey LITERAL = createTextAttributesKey("RSL_LITERAL", DefaultLanguageHighlighterColors.CONSTANT);
        static final TextAttributesKey STRING = createTextAttributesKey("RSL_STRING", DefaultLanguageHighlighterColors.STRING);
        static final TextAttributesKey NUMBER = createTextAttributesKey("RSL_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
        static final TextAttributesKey OPERATOR = createTextAttributesKey("RSL_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
        static final TextAttributesKey LINE_COMMENT = createTextAttributesKey("RSL_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
        static final TextAttributesKey BLOCK_COMMENT = createTextAttributesKey("RSL_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
        static final TextAttributesKey SPECIAL_NAME = createTextAttributesKey("RSL_SPECIAL_NAME", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE);
        static final TextAttributesKey FORM = createTextAttributesKey("RSL_FORM", DefaultLanguageHighlighterColors.STRING);

        @Override
        public @NotNull Lexer getHighlightingLexer() {
            return new RslParserDefinition.RslLexer();
        }

        @Override
        public TextAttributesKey @NotNull [] getTokenHighlights(IElementType token) {
            if (token == TYPES.get(Kind.KEYWORD)) return pack(KEYWORD);
            if (token == TYPES.get(Kind.TYPE)) return pack(TYPE);
            if (token == TYPES.get(Kind.LITERAL)) return pack(LITERAL);
            if (token == TYPES.get(Kind.STRING) || token == TYPES.get(Kind.OPEN_STRING)) return pack(STRING);
            if (token == TYPES.get(Kind.NUMBER) || token == TYPES.get(Kind.BAD_NUMBER)) return pack(NUMBER);
            if (token == TYPES.get(Kind.FORM) || token == TYPES.get(Kind.OPEN_FORM)) return pack(FORM);
            if (token == TYPES.get(Kind.LINE_COMMENT)) return pack(LINE_COMMENT);
            if (token == TYPES.get(Kind.BLOCK_COMMENT) || token == TYPES.get(Kind.OPEN_COMMENT)) return pack(BLOCK_COMMENT);
            if (token == TYPES.get(Kind.SPECIAL_NAME) || token == TYPES.get(Kind.OPEN_NAME)) return pack(SPECIAL_NAME);
            if (token == TYPES.get(Kind.ASSIGN) || token == TYPES.get(Kind.OPERATOR)) return pack(OPERATOR);
            if (token == TYPES.get(Kind.BAD)) return pack(HighlighterColors.BAD_CHARACTER);
            return TextAttributesKey.EMPTY_ARRAY;
        }
    }
}
