package a6a.dr.config;

import a6a.dr.Miao;
import a6a.dr.core.MessageProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 配置文件热加载：轮询文件的时间戳/大小，一变就重新读一遍。
 *
 * <p>用轮询而不是 WatchService，是因为编辑器保存时经常"先写临时文件再改名"，
 * WatchService 在不同平台/编辑器下事件形态差别很大，轮询反而更稳。</p>
 */
public final class ConfigWatcher {

    private static ScheduledExecutorService executor;
    private static long lastModified = Long.MIN_VALUE;
    private static long lastSize = Long.MIN_VALUE;
    private static boolean warnedBroken;

    private ConfigWatcher() {
    }

    public static synchronized void start() {
        if (executor != null) {
            return;
        }
        syncBaseline();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "miao-config-watcher");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(ConfigWatcher::tick, 2L, 2L, TimeUnit.SECONDS);
        Miao.LOGGER.info("配置热加载已开启（每 2 秒检查一次 {}）", ConfigManager.path());
    }

    public static synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** 自己写盘之后调用，避免把自己的写入当成"外部修改"再读一遍。 */
    public static synchronized void syncBaseline() {
        Path file = ConfigManager.path();
        try {
            if (Files.exists(file)) {
                lastModified = Files.getLastModifiedTime(file).toMillis();
                lastSize = Files.size(file);
            }
        } catch (Exception ignored) {
            // 拿不到就算了，下一次 tick 会自己重新比对
        }
        warnedBroken = false;
    }

    private static void tick() {
        try {
            if (!ConfigManager.get().autoReload) {
                return;
            }
            Path file = ConfigManager.path();
            if (!Files.exists(file)) {
                return;
            }
            long modified = Files.getLastModifiedTime(file).toMillis();
            long size = Files.size(file);
            if (modified == lastModified && size == lastSize) {
                return;
            }
            lastModified = modified;
            lastSize = size;

            if (!ConfigManager.load()) {
                // load() 失败时会保留旧配置，说明这份文件还写坏了
                if (!warnedBroken) {
                    Miao.LOGGER.warn("配置文件似乎写坏了，先继续用旧配置；修好后会自动重试");
                    warnedBroken = true;
                }
                return;
            }
            warnedBroken = false;
            MessageProcessor.resetCounters();
            Miao.LOGGER.info("检测到配置变更，已自动热加载");
        } catch (Throwable t) {
            Miao.LOGGER.warn("配置热加载检查出错，已跳过这一轮", t);
        }
    }
}
