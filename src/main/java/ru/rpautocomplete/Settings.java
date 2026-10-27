package ru.rpautocomplete;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Настройки мода (файл config/rpautocomplete/settings.json). */
public final class Settings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Settings instance = new Settings();

    public static Settings get() { return instance; }

    /** Главный выключатель автодополнения. */
    public boolean enabled = true;
    /** Сколько вариантов показывать в списке (1..8). */
    public int maxSuggestions = 4;
    /** Минимум символов сокращения/начала фразы для подсказки (1..4). */
    public int minChars = 2;
    /** Явный поиск: «/rp слово» показывает все подходящие шаблоны. Пусто = выключено. */
    public String trigger = "/rp";
    /** Команды, внутри которых работает автодополнение обычных фраз. */
    public List<String> rpCommands = new ArrayList<>(List.of("me", "do", "todo", "try", "ame"));
    /** Защита: Enter сразу после вставки не отправляет (мс). 0 = выключено. */
    public int guardEnterMs = 700;
    /** Защита: не отправлять, пока в тексте остались {заполнители}. */
    public boolean guardPlaceholders = true;
    /** Т9 по словам: подсказка окончания слова («греч» → «гречка»). */
    public boolean wordsEnabled = true;
    /** Сколько слов показывать (1..9). */
    public int maxWords = 5;
    /** С какой по счёту буквы начинать подсказывать слова (1..4). */
    public int wordMinChars = 2;
    /** Ставить пробел после вставленного слова. */
    public boolean wordAddSpace = true;
    /** Не подсказывать матерные слова. */
    public boolean filterProfanity = true;
    public boolean showPreview = true;
    public boolean showHint = true;
    /** Клавиши в чате (коды GLFW): Tab, стрелка вниз, стрелка вверх. */
    public int keyAccept = Keys.TAB;
    public int keyNext = Keys.DOWN;
    public int keyPrev = Keys.UP;

    public static void load() {
        Path p = Io.dir().resolve("settings.json");
        if (Files.exists(p)) {
            try {
                Settings s = GSON.fromJson(Io.read(p), Settings.class);
                if (s != null) {
                    s.sanitize();
                    instance = s;
                }
            } catch (Exception e) {
                RpAutocompleteClient.LOGGER.warn("Не удалось прочитать settings.json, используются настройки по умолчанию", e);
            }
        } else {
            instance.save();
        }
    }

    public void save() {
        try {
            Io.write(Io.dir().resolve("settings.json"), GSON.toJson(this));
        } catch (IOException e) {
            RpAutocompleteClient.LOGGER.warn("Не удалось сохранить settings.json", e);
        }
    }

    public void sanitize() {
        maxSuggestions = Math.max(1, Math.min(8, maxSuggestions));
        minChars = Math.max(1, Math.min(4, minChars));
        maxWords = Math.max(1, Math.min(9, maxWords));
        wordMinChars = Math.max(1, Math.min(4, wordMinChars));
        if (trigger == null) trigger = "";
        trigger = trigger.strip();
        if (rpCommands == null) rpCommands = new ArrayList<>();
        List<String> clean = new ArrayList<>();
        for (String c : rpCommands) {
            if (c == null) continue;
            String x = Norm.n(c.strip());
            while (x.startsWith("/")) x = x.substring(1);
            if (!x.isEmpty() && !clean.contains(x)) clean.add(x);
        }
        rpCommands = clean;
        if (guardEnterMs < 0) guardEnterMs = 0;
        if (guardEnterMs > 5000) guardEnterMs = 5000;
        if (keyAccept <= 0) keyAccept = Keys.TAB;
        if (keyNext <= 0) keyNext = Keys.DOWN;
        if (keyPrev <= 0) keyPrev = Keys.UP;
    }
}
