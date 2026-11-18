package dev.doshikking.rsl;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Map;

import static dev.doshikking.rsl.RslHighlighterFactory.Highlighter.*;

public final class RslColorSettings implements ColorSettingsPage {
    private static final AttributesDescriptor[] ATTRIBUTES = {
            new AttributesDescriptor("Keyword", KEYWORD),
            new AttributesDescriptor("Built-in type", TYPE),
            new AttributesDescriptor("Special value", LITERAL),
            new AttributesDescriptor("String", STRING),
            new AttributesDescriptor("Number", NUMBER),
            new AttributesDescriptor("Operator", OPERATOR),
            new AttributesDescriptor("Line comment", LINE_COMMENT),
            new AttributesDescriptor("Block comment", BLOCK_COMMENT),
            new AttributesDescriptor("Special identifier", SPECIAL_NAME),
            new AttributesDescriptor("Output form", FORM)
    };

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new RslHighlighterFactory.Highlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        return """
                /* Внешний комментарий /* вложенный */ */
                // Макрос вывода суммы
                const LIMIT: money = $120.50;
                var {Сумма счёта}: money = LIMIT;
                macro show(flag: bool)
                  if (flag == true)
                    [ Сумма: #### ] ({Сумма счёта}:r);
                    println("Готово");
                  end;
                end;
                """;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return ATTRIBUTES.clone();
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "RSL";
    }
}
