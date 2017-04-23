package tk.ardentbot.rethink.models;

import lombok.Getter;
import tk.ardentbot.core.translate.LangFactory;
import tk.ardentbot.core.translate.Language;

public class TranslationModel {
    @Getter
    private String command_identifier;
    @Getter
    private String translation;
    @Getter
    private String id;
    private String language;
    @Getter
    private boolean verified;
    @Getter
    private String uuid;

    public TranslationModel(String command_identifier, String translation, String id, String language, boolean verified) {
        this.command_identifier = command_identifier;
        this.translation = translation;
        this.id = id;
        this.language = language;
        this.verified = verified;
    }

    public Language getLanguage() {
        return LangFactory.getLanguage(language);
    }
}