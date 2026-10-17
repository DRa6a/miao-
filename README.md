# 喵~ (Miao)

一个**纯服务端**的 Fabric 模组：拦截服务器里玩家发出的、以及与玩家相关的消息（聊天、死亡、进退服、进度提示……），
按配置在指定位置插入插入语（默认「喵」）。

- 位置、内容都能调，甚至能精确到"在『你好』两个字后面插"
- 每个玩家可以单独配置，没配的走全局默认
- 玩家自己在聊天里就能带插入语（`[miao:喵@after:你好]`），优先级高于服务器配置
- 客户端不需要装这个模组

## 环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | >= 0.19.5 |
| Fabric API | 需要 |
| Java | >= 25 |

把 `build/libs/miao-1.0.0.jar` 丢进服务端的 `mods/` 即可。

## 配置文件

首次启动会在 `config/miao.json` 生成默认配置：

```json
{
  "enabled": true,
  "debug": false,
  "defaults": {
    "enabled": true,
    "inserts": ["喵"],
    "pickMode": "random",
    "position": "suffix",
    "fallback": "suffix",
    "categoryPositions": {},
    "targets": {
      "chat": true,
      "death": true,
      "join": true,
      "quit": true,
      "advancement": true,
      "other": false
    }
  },
  "players": {},
  "marker": {
    "enabled": true,
    "prefix": "[miao:",
    "maxLength": 32,
    "maxPerMessage": 3,
    "allowPosition": true,
    "allowDisable": true,
    "stripExcess": true
  }
}
```

### 顶层字段

| 字段 | 说明 |
| --- | --- |
| `enabled` | 总开关，关掉后模组完全不介入任何消息 |
| `debug` | 打开后每条被处理的消息都会打印到控制台，调位置表达式时很有用 |
| `defaults` | 全局默认档 |
| `players` | 每玩家覆盖档，key 可以是玩家名，也可以是 UUID（UUID 优先） |
| `marker` | 聊天内联标记设置 |

### 档位字段（`defaults` 与 `players` 里的每个条目）

| 字段 | 说明 |
| --- | --- |
| `enabled` | 对该玩家是否启用 |
| `inserts` | 插入语候选池，多条时按 `pickMode` 取一条 |
| `pickMode` | `random`（默认）/ `first`（永远第一条）/ `cycle`（轮流） |
| `position` | 插入位置表达式，见下表 |
| `fallback` | 位置解析失败或锚点找不到时的兜底位置，默认 `suffix` |
| `categoryPositions` | 给某一类消息单独指定位置，优先级高于 `position`，例如 `{"death": "prefix"}` |
| `targets` | 各类消息的开关：`chat` / `death` / `join` / `quit` / `advancement` / `other` |

**玩家档只需要写要覆盖的字段**，其余自动继承全局默认。例如只给某个玩家换个位置：

```json
"players": {
  "Alice": { "position": "after:你好" },
  "069a79f4-44e9-4726-a5be-fca90e38aaf5": { "enabled": false }
}
```

## 插入位置表达式

配置和内联标记共用同一套语法，大小写不敏感：

| 表达式 | 含义 | 示例（原文 `你好世界`，插入 `喵`） |
| --- | --- | --- |
| `suffix` | 末尾（默认），也可写 `end` / `tail` | `你好世界喵` |
| `prefix` | 开头，也可写 `start` / `head` | `喵你好世界` |
| `index:N` | 第 N 个字符处（0 起；负数从末尾算，`-1` 即末尾） | `index:2` → `你好喵世界` |
| `percent:P` | 按比例插入，P 为 0~100（也可写 `50%`） | `percent:50` → `你好喵世界` |
| `after:文本` | 在文本第一次出现之后 | `after:你好` → `你好喵世界` |
| `afterLast:文本` | 在文本最后一次出现之后 | — |
| `before:文本` | 在文本第一次出现之前 | `before:世界` → `你好喵世界` |
| `beforeLast:文本` | 在文本最后一次出现之前 | — |

锚点文本里可以有空格和中文标点，写不进 JSON 时记得转义（`"` → `\"`、`\` → `\\`）。

**锚点找不到**时不会报错，会自动退回 `fallback`（默认末尾）。

## 玩家自带插入语（内联标记）

玩家在聊天里直接写标记，服务器会把它解析出来并从正文中剥离：

```
你好[miao:喵]                 →  你好喵            （位置用服务器给这个玩家配的）
你好[miao:喵@after:你好]      →  你好喵
[miao:喵@prefix]你好          →  喵你好
你好[miao:喵@percent:50]      →  你好喵
你好[miao:off]                →  你好              （本条不插语）
```

规则：

- 只要消息里出现有效标记，服务器就**不再追加**自己的插入语——客户端优先
- 一条消息最多生效 `marker.maxPerMessage` 个标记
- 标记内容会过滤 `§` 格式化符号与控制字符，长度受 `marker.maxLength` 限制
- 写法非法（比如没有闭合 `]`、内容为空）时，原样保留，不做任何处理

`marker` 段可调：

| 字段 | 说明 |
| --- | --- |
| `prefix` | 标记起始串，默认 `[miao:`（裸写 `[miao]` 也支持） |
| `maxLength` | 插入语最大长度（按字符计） |
| `maxPerMessage` | 单条消息最多几个标记 |
| `allowPosition` | 是否允许标记自带 `@位置` |
| `allowDisable` | 是否允许 `[miao:off]` |
| `stripExcess` | 超出上限的标记是剥离（`true`）还是原样显示（`false`） |

## 命令

`/miao` 需要权限等级 2（OP）。

| 命令 | 说明 |
| --- | --- |
| `/miao` | 查看当前状态 |
| `/miao reload` | 重新读取配置文件（`/reload` 也会自动重载） |
| `/miao info [玩家]` | 查看某个玩家最终生效的配置 |
| `/miao test <文本>` | 用当前执行者的配置预览处理结果 |
| `/miao position <玩家> <表达式>` | 设置某玩家的插入位置 |
| `/miao insert <玩家> <文本>` | 设置某玩家的插入语（替换候选池） |
| `/miao insertadd <玩家> <文本>` | 往候选池里追加一条 |
| `/miao enable\|disable <玩家>` | 开关某玩家 |
| `/miao target <玩家> <类别> <true\|false>` | 开关某类消息 |
| `/miao reset <玩家>` | 清除该玩家的单独配置，回到全局默认 |

命令改动会立即写回 `config/miao.json`。

## 实现方式

| 目标 | 拦截点 | 处理方式 |
| --- | --- | --- |
| 玩家聊天 | `PlayerList#broadcastChatMessage(PlayerChatMessage, ServerPlayer, ChatType.Bound)` | 替换消息的"无签名内容"，聊天栏排版与签名校验都不受影响 |
| `/say` 一类命令聊天 | 同上的 `CommandSourceStack` 重载 | 重建一条系统聊天消息 |
| 死亡 / 进服 / 退服 / 进度 / 其它广播 | `PlayerList#broadcastSystemMessage`、`broadcastSystemToTeam`、`broadcastSystemToAllExceptTeam` | 改写 Component |
| 关联玩家 | `ServerPlayer#die`、`PlayerList#placeNewPlayer`、`ServerGamePacketListenerImpl#removePlayerFromWorld`、`PlayerAdvancements#award` | 打一个线程内上下文标记，广播时取出，用于匹配每玩家配置 |

首尾位置（`suffix` / `prefix`）是直接给原 Component 追加兄弟节点，**不会破坏客户端翻译**；
中间位置则只能把消息先渲染成纯文本再重建。

### 已知限制

- 死亡、进退服、进度这些是**翻译型消息**，中间位置需要先按服务器语言渲染（服务端只有 `en_us`），
  所以对这类消息用 `after:被` 这类中文锚点通常匹配不到；建议这类消息用 `suffix` / `prefix`，
  或者用 `categoryPositions` 单独给它们配首尾位置。
- 修改后的聊天消息内容不再被聊天签名覆盖（这是服务端装饰消息的正常做法），
  举报系统里看到的仍是玩家原始文本。

## 构建

```bash
./gradlew build          # 产物在 build/libs/miao-1.0.0.jar
./gradlew runServer      # 本地起一个开发服务器
```

`tools/rcon.py` 是个小工具，方便在开发服务器上跑命令：

```bash
python tools/rcon.py "miao test 你好世界" "say 你好世界"
```

## License

MIT
