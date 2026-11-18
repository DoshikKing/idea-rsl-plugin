package dev.doshikking.rsl;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public final class RslFileType extends LanguageFileType {
    public static final RslFileType INSTANCE = new RslFileType();

    private RslFileType() {
        super(new Language("RSL") {});
    }

    @Override
    public @NotNull String getName() {
        return "RSL";
    }

    @Override
    public @NotNull String getDescription() {
        return "RS-Bank macro module";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "mac";
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }
}
