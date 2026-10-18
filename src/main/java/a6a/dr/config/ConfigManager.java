package a6a.dr.config;

import a6a.dr.Miao;
import a6a.dr.core.Settings;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置的加载、保存与生效配置缓存。
 */
public final class ConfigManager {

    private static final String FILE_NAME = "miao.json";

    private static volatile MiaoConfig config = new MiaoConfig();
    private static final Map<String, Settings> CACHE = new ConcurrentHashMap<>();
    private static Path path;

    private ConfigManager() {
    }

    public static Path path() {
        if (path == null) {
            path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        }
        return path;
    }

    public static MiaoConfig get() {
        return config;
    }

    /**
     * 读取配置。文件不存在时会生成一份默认配置。
     *
     * @return 是否成功读到/生成了配置；失败时保留上一份配置
     */
    public static boolean load() {
        Path file = path();
        CACHE.clear();
        if (!Files.exists(file)) {
            MiaoConfig fresh = new MiaoConfig();
            fresh.normalize();
            config = fresh;
            save();
            Miao.LOGGER.info("已生成默认配置：{}", file);
            return true;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            MiaoConfig loaded = MiaoConfig.GSON.fromJson(reader, MiaoConfig.class);
            if (loaded == null) {
                loaded = new MiaoConfig();
            }
            loaded.normalize();
            config = loaded;
            Miao.LOGGER.info("已加载配置：{}", file);
            return true;
        } catch (Exception e) {
            Miao.LOGGER.error("配置读取失败，继续使用上一份配置：{}", file, e);
            return false;
        }
    }

    public static synchronized void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                MiaoConfig.GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            Miao.LOGGER.error("配置写入失败：{}", file, e);
        } finally {
            // 自己写盘不算"外部修改"，更新热加载的基准
            ConfigWatcher.syncBaseline();
        }
    }

    /** 取某个玩家的生效配置（带缓存，配置重载时整体失效）。 */
    public static Settings settingsFor(@Nullable UUID uuid, @Nullable String name) {
        String key = uuid != null ? uuid.toString() : (name == null ? "" : "name:" + name.toLowerCase());
        Settings cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Settings resolved = Settings.resolve(config, uuid == null ? null : uuid.toString(), name);
        CACHE.put(key, resolved);
        return resolved;
    }

    /** 修改配置后调用，让缓存失效并落盘。 */
    public static void touch() {
        CACHE.clear();
        save();
    }

    /** 取或创建某玩家的覆盖档。 */
    public static MiaoConfig.Profile profileOf(String key) {
        return config.players.computeIfAbsent(key, k -> MiaoConfig.Profile.empty());
    }

    @Nullable
    public static MiaoConfig.Profile findProfile(String key) {
        MiaoConfig.Profile direct = config.players.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, MiaoConfig.Profile> e : config.players.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }
}
