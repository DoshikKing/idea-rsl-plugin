package dev.doshikking.rsl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import dev.doshikking.rsl.RslTokens.Kind;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

public final class RslParserDefinition implements ParserDefinition {
    private static final IFileElementType FILE = new IFileElementType(RslFileType.INSTANCE.getLanguage());
    static final Map<Kind, IElementType> TYPES = new EnumMap<>(Kind.class);

    static {
        for (Kind kind : Kind.values()) {
            if (kind != Kind.EOF) {
                TYPES.put(kind, new IElementType(kind.name(), RslFileType.INSTANCE.getLanguage()));
            }
        }
        TYPES.put(Kind.SPACE, TokenType.WHITE_SPACE);
        TYPES.put(Kind.BAD, TokenType.BAD_CHARACTER);
    }

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new RslLexer();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return (root, builder) -> {
            var file = builder.mark();
            // ponytail: flat PSI for editor services; add declaration nodes when navigation needs them.
            while (!builder.eof()) builder.advanceLexer();
            file.done(root);
            return builder.getTreeBuilt();
        };
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return TokenSet.create(TYPES.get(Kind.LINE_COMMENT), TYPES.get(Kind.BLOCK_COMMENT), TYPES.get(Kind.OPEN_COMMENT));
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return TokenSet.create(TYPES.get(Kind.STRING), TYPES.get(Kind.OPEN_STRING), TYPES.get(Kind.FORM));
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        return new ASTWrapperPsiElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new PsiFileBase(viewProvider, RslFileType.INSTANCE.getLanguage()) {
            @Override
            public @NotNull RslFileType getFileType() {
                return RslFileType.INSTANCE;
            }
        };
    }

    static final class RslLexer extends LexerBase {
        private CharSequence buffer = "";
        private int end;
        private int start;
        private int state;
        private RslTokens.Match token = new RslTokens.Match(Kind.EOF, 0);

        @Override
        public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
            this.buffer = buffer;
            end = endOffset;
            start = startOffset;
            state = initialState;
            token = RslTokens.scan(buffer, start, end, state);
        }

        @Override
        public int getState() {
            return state;
        }

        @Override
        public IElementType getTokenType() {
            return TYPES.get(token.kind());
        }

        @Override
        public int getTokenStart() {
            return start;
        }

        @Override
        public int getTokenEnd() {
            return token.end();
        }

        @Override
        public void advance() {
            state = RslTokens.nextState(buffer, start, token, state);
            start = token.end();
            token = RslTokens.scan(buffer, start, end, state);
        }

        @Override
        public @NotNull CharSequence getBufferSequence() {
            return buffer;
        }

        @Override
        public int getBufferEnd() {
            return end;
        }
    }
}
