package a6a.dr;

import a6a.dr.command.MiaoCommand;
import a6a.dr.config.ConfigManager;
import a6a.dr.config.ConfigWatcher;
import a6a.dr.core.MessageProcessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 喵~ —— 给服务器里的消息加上插入语的服务端模组。
 */
public class Miao implements ModInitializer {

	public static final String MOD_ID = "miao";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConfigManager.load();

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> MiaoCommand.register(dispatcher));

		// /reload 之后顺手重新读一遍配置
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				ConfigManager.load();
				MessageProcessor.resetCounters();
			}
		});

		// 配置文件热加载：服务器起来后开始盯着 config/miao.json
		ServerLifecycleEvents.SERVER_STARTED.register(server -> ConfigWatcher.start());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ConfigWatcher.stop());

		LOGGER.info("喵~ 已加载，配置文件：{}", ConfigManager.path());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
