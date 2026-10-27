package ru.rpautocomplete;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Хранилище шаблонов: config/rpautocomplete/templates.json */
public final class TemplateStore {
    public static final String FORMAT = "rpautocomplete-templates";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final TemplateStore INSTANCE = new TemplateStore();

    public static TemplateStore get() { return INSTANCE; }

    public record ImportResult(int added, int skipped, int files) {}

    /** Формат файла (и для хранения, и для импорта/экспорта). */
    static final class FileData {
        String format = FORMAT;
        int version = 1;
        List<Template> templates = new ArrayList<>();
    }

    private final List<Template> items = new ArrayList<>();
    private volatile int version = 0;

    /** Увеличивается при каждом изменении шаблонов (нужно словарю, чтобы пересобрать личные слова). */
    public int version() { return version; }

    private TemplateStore() {}

    public List<Template> all() { return Collections.unmodifiableList(items); }

    private Path file() { return Io.dir().resolve("templates.json"); }

    // ------------------------------------------------------------------ загрузка / сохранение

    public void load() {
        version++;
        items.clear();
        Path p = file();
        if (!Files.exists(p)) {
            items.addAll(demo());
            save();
            return;
        }
        try {
            items.addAll(parse(Io.read(p)));
        } catch (Exception e) {
            RpAutocompleteClient.LOGGER.error("templates.json повреждён, делаю копию и создаю демо-набор", e);
            boolean backedUp = false;
            try {
                Path bak = p.resolveSibling("templates.broken-" + System.currentTimeMillis() + ".json");
                Files.copy(p, bak);
                backedUp = true;
            } catch (IOException ignored) {
                // копию сделать не удалось — старый файл не трогаем
            }
            items.clear();
            items.addAll(demo());
            if (backedUp) save();
        }
    }

    public void save() {
        version++;
        try {
            Io.write(file(), exportJson());
        } catch (IOException e) {
            RpAutocompleteClient.LOGGER.error("Не удалось сохранить templates.json", e);
        }
    }

    // ------------------------------------------------------------------ изменения

    public void add(Template t) {
        t.normalize();
        items.add(t);
        save();
    }

    public void remove(Template t) {
        items.remove(t);
        save();
    }

    public void markUsed(Template t) {
        t.uses++;
        t.lastUsed = System.currentTimeMillis();
        save();
    }

    // ------------------------------------------------------------------ импорт / экспорт

    public String exportJson() {
        FileData d = new FileData();
        d.templates = new ArrayList<>(items);
        return GSON.toJson(d);
    }

    /** Сохраняет все шаблоны в config/rpautocomplete/export/rp-templates-<дата>.json */
    public Path exportToFile() throws IOException {
        String name = "rp-templates-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";
        Path p = Io.dir().resolve("export").resolve(name);
        Io.write(p, exportJson());
        return p;
    }

    /** Импорт из текста JSON (из файла или из буфера обмена). */
    public ImportResult importJson(String json) {
        return merge(parse(json), 0);
    }

    /** Импорт всех *.json из config/rpautocomplete/import */
    public ImportResult importFromFolder() throws IOException {
        Path dir = Io.dir().resolve("import");
        Files.createDirectories(dir);
        int added = 0, skipped = 0, files = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : ds) {
                files++;
                try {
                    ImportResult r = merge(parse(Io.read(p)), 0);
                    added += r.added();
                    skipped += r.skipped();
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(p.getFileName() + ": " + e.getMessage());
                }
            }
        }
        return new ImportResult(added, skipped, files);
    }

    private ImportResult merge(List<Template> incoming, int files) {
        int added = 0, skipped = 0;
        for (Template t : incoming) {
            if (isDuplicate(t)) {
                skipped++;
                continue;
            }
            for (Template e : items) {
                if (e.id.equals(t.id)) {
                    t.id = UUID.randomUUID().toString();
                    break;
                }
            }
            items.add(t);
            added++;
        }
        if (added > 0) save();
        return new ImportResult(added, skipped, files);
    }

    private boolean isDuplicate(Template t) {
        for (Template e : items) {
            if (e.text.equals(t.text) && e.abbreviations.size() == t.abbreviations.size()
                    && e.abbreviations.containsAll(t.abbreviations)) {
                return true;
            }
        }
        return false;
    }

    /** Разбор JSON: объект {"templates":[...]} или просто массив [...]. */
    static List<Template> parse(String json) {
        String s = json == null ? "" : json.strip();
        if (s.startsWith("\uFEFF")) s = s.substring(1).strip();
        if (s.isEmpty()) throw new IllegalArgumentException("пустой текст");
        List<Template> raw;
        try {
            if (s.startsWith("[")) {
                Template[] arr = GSON.fromJson(s, Template[].class);
                raw = arr == null ? new ArrayList<>() : Arrays.asList(arr);
            } else {
                FileData d = GSON.fromJson(s, FileData.class);
                raw = (d == null || d.templates == null) ? new ArrayList<>() : d.templates;
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("это не похоже на JSON с шаблонами (" + e.getMessage() + ")");
        }
        List<Template> out = new ArrayList<>();
        for (Template t : raw) {
            if (t == null) continue;
            t.normalize();
            if (t.text.isBlank()) continue;
            out.add(t);
        }
        return out;
    }

    // ------------------------------------------------------------------ демо-набор

    private static List<Template> demo() {
        List<Template> l = new ArrayList<>();
        Template a = new Template("Приветствие и проверка документов", "Документы",
                "/todo Здравствуйте. Предъявите, пожалуйста, документы для проверки.", "здр", "док");
        a.favorite = true;
        l.add(a);
        Template b = new Template("Показать удостоверение", "Полиция",
                "/me достал удостоверение из внутреннего кармана куртки и предъявил его собеседнику.", "дост", "уд");
        b.favorite = true;
        l.add(b);
        // Четыре варианта на одно сокращение «проверка»: попробуйте «/rp проверка»
        l.add(new Template("Проверка документов", "Документы",
                "/me внимательно изучил предъявленные документы, сверив фотографию с лицом владельца.", "проверка", "док"));
        l.add(new Template("Задержание", "Задержание",
                "/me крепко взял задерживаемого за руку, завёл её за спину и надел наручники.", "проверка", "задерж"));
        l.add(new Template("Обыск", "Обыск",
                "/me начал осматривать одежду и карманы, проверяя наличие запрещённых предметов.", "проверка", "обыск"));
        l.add(new Template("Оказание медицинской помощи", "Медицина",
                "/me осмотрел пострадавшего, проверил пульс и дыхание и начал оказывать первую помощь.", "проверка", "мед"));
        l.add(new Template("Рация", "Полиция",
                "/do Рация закреплена на плече и включена на приём.", "рация", "рц"));
        l.add(new Template("Открыть дверь", "Повседневные",
                "/me открыл дверь и вошёл в помещение.", "дверь", "вош"));
        l.add(new Template("Посмотреть на часы", "Повседневные",
                "/me посмотрел на наручные часы.", "часы"));
        l.add(new Template("Передать документы ({имя} — заполнить)", "Документы",
                "/me передал документы гражданину {имя} для дальнейшей проверки.", "перед"));
        l.add(new Template("Вопрос о цели визита", "Разговоры",
                "/todo Подскажите, с какой целью вы здесь находитесь?", "цель"));
        l.add(new Template("Просьба предъявить документы (обычная речь)", "Разговоры",
                "Здравствуйте, предъявите документы, пожалуйста. Это плановая проверка.", "предъяв"));
        l.add(new Template("Проверить пульс", "Медицина",
                "/me приложил два пальца к шее пострадавшего, проверяя пульс.", "пульс"));
        return l;
    }
}
