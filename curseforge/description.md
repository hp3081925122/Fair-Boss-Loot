# Fair Boss Loot

**Fair Boss Loot** is a **pure server-side** mod that ensures every player who meaningfully contributes to defeating a boss or tracked mob receives their own separate, protected loot drops and experience—instead of everyone fighting over a single shared pile.

No client installation is required to join a multiplayer server running this mod, and it also works seamlessly in singleplayer and LAN worlds.

## Key Features

### Damage-Contribution Tracking (Anti-Leeching)
- Tracks **effective damage** dealt by each player (clamped to actual health + absorption lost, so overkill finishing blows never inflate damage share).
- Configurable **minimum damage threshold** (default: `5%` of max health, or switch to a flat damage value) and **combat participation window** (default: `60` seconds) to prevent 1-arrow freeloading.
- Configurable **maximum rewarded players** (sorted by damage descending) and **killer policy** (`ALWAYS`, `FILL`, or `REQUIREMENTS`).
- **Summon, Pet & Projectile Attribution**: Automatically traces damage from arrows/projectiles, area-effect clouds, primed TNT, tamed pets (`OwnableEntity`), and common modded summons back to their player owner (while ignoring automated `FakePlayer` machines).

### Full-Compatibility Hybrid Drop Pipeline
- **Independent Loot Table Rolls**: Every eligible player gets their own fresh roll of the entity's loot table using **their own Looting enchantment, Luck attribute, and perturbed seed**—preventing identical cloned RNG gear while keeping Global Loot Modifiers intact.
- **Custom Boss Death Drops**: Automatically captures drops generated in `dropCustomDeathLoot` (such as the Wither's Nether Star and modded bosses' custom death drops) and grants a fair share to each eligible player.
- **Third-Party Mod Event Drops**: Captures extra drops added by other mods during `LivingDropsEvent` without canceling the event or swallowing third-party drops.
- **Anti-Dupe Equipment Protection**: Specifically distinguishes worn/picked-up mob equipment and keeps it non-duplicated by default, preventing gear-duplication exploits.
- **Personal Experience Orbs**: Supports `FULL_PER_PLAYER`, `PROPORTIONAL`, or `VANILLA` XP modes (including multi-wave Ender Dragon XP), spawning personal XP orbs that natively trigger Mending and cannot be seen, attracted, or picked up by other players.

### Flexible Delivery Modes & Drop Protection
- **`ISOLATED_DROP` (Default)**: Drops spawn at the kill site, visible and pickable **only** by their rightful owner.
  - Built-in **environment protection** against fire, lava, cactus, and explosions.
  - **Hopper theft protection** prevents hoppers from sucking up personal drops.
  - **Void Rescue**: Personal drops falling into the void are automatically rescued into the owner's persistent pending vault.
  - Configurable glowing outline and extended lifespan (default: 10 minutes).
- **`DIRECT_INVENTORY`**: Sends rewards straight into each participant's inventory; overflow or offline/dead players automatically receive their loot in a persistent **Pending Loot Vault** with a clickable chat claim button and login reminder.
- **`HYBRID`**: Inserts into inventory first, dropping overflow safely at the player's feet (or into the vault if offline/dead).

### Damage Summary & Commands
- Sends a clean, localized **damage contribution summary** in chat after defeating a tracked boss, displaying each participant's rank, damage dealt, percentage share, and reward status (rendered server-side per player locale so unmodded clients never see raw translation keys).
- **Commands** (`/fairbossloot` or `/fbl`):
  - `/fbl claim` — Claim stored items from your pending loot vault.
  - `/fbl list` — Preview items currently stored in your pending loot vault.
  - `/fbl clear <player>` *(OP)* — Clear a player's pending loot vault.
  - `/fbl inspect <entity>` *(OP)* — Inspect live damage contribution records on a target mob.

### Flexible Configuration & Tags
- Easy-to-edit TOML config (`config/fair_boss_loot-common.toml`)—no datapacks required for standard customization.
- Targets `#c:bosses` / `#forge:bosses`, Ender Dragon, Wither, Warden, and Elder Guardian by default via `#fair_boss_loot:eligible_entities`, plus TOML whitelist/blacklist (`modid:entity`, `modid:*`, `#tag`), optional max-health auto-tracking for elites, global all-mobs toggle, and `#fair_boss_loot:non_duplicable` item exclusion.

---

# Fair Boss Loot（公平首领战利品）

**Fair Boss Loot** 是一款**纯服务端**模组：在多人合作击败 Boss 或受追踪生物时，根据实际伤害贡献，为每位达标玩家分别发放独立且受保护的战利品与经验，不再抢夺同一份掉落。

联机服务器仅需服务端安装，客户端无需安装即可正常进服；同时也完全兼容单人存档与局域网联机。

## 核心特性

### 伤害贡献追踪与防白嫖机制
- 精确统计每位玩家造成的**有效伤害**（按生物实际损失的生命值与吸收护盾截断，杜绝超高溢出伤害最后一击刷占比）。
- 支持配置**最低伤害门槛**（默认占目标最大生命值 `5%`，也可切换固定伤害值）与**有效参战时间窗口**（默认 `60` 秒），有效防止蹭一箭白嫖全套 Boss 掉落。
- 支持配置**最大获奖人数上限**（按伤害降序）与**击杀者兜底策略**（`ALWAYS` / `FILL` / `REQUIREMENTS`）。
- **召唤物 / 宠物 / 投射物归属追溯**：自动将箭矢、药水云、TNT、驯服宠物（`OwnableEntity`）及常见第三方模组召唤物的伤害计入玩家主人名下，同时自动排除假人（`FakePlayer`）。

### 全兼容混合掉落管线
- **战利品表独立重掷**：为每位达标玩家套用**各自的抢夺附魔、幸运值与扰动种子**单独重掷战利品表，避免直接复制出完全同词条的随机装备，并完整兼容全局战利品修改器（GLM）。
- **自定义死亡掉落兼容**：自动捕获 `dropCustomDeathLoot` 生成的 Boss 专属掉落（如凋灵下界之星及第三方模组 Boss 自定义掉落），按人各发一份。
- **第三方模组事件兼容**：在 `LivingDropsEvent` 末尾捕获其他模组追加的额外掉落按人发放，绝不取消原事件或吞掉其他模组的掉落物。
- **装备防刷复制保护**：精确区分生物穿戴或捡起的装备掉落，默认不复制装备，彻底堵死“扔装备给怪捡起来刷双倍”的漏洞。
- **专属经验球分配**：支持 `FULL_PER_PLAYER`（每人完整一份）、`PROPORTIONAL`（按伤害占比拆分）与 `VANILLA`（原版），兼容常规 Boss 与末影龙分批掉落经验，原生触发经验修补，且非归属玩家不可见、无法吸引或拾取。

### 三种发放模式与全方位防毁保护
- **`ISOLATED_DROP`（默认，地面个人隔离掉落物）**：掉落物生成在击杀点，**仅对应玩家可见且仅其可拾取**。
  - **环境保护**：免疫火焰、岩浆、仙人掌与爆炸销毁。
  - **防漏斗偷取**：阻止漏斗吸走个人隔离掉落物。
  - **虚空自动救援**：隔离掉落物落入虚空时自动转入归属玩家的持久化待领暂存库并通知领取。
  - 支持发光高亮与延长存活时间（默认 10 分钟）。
- **`DIRECT_INVENTORY`（直发背包）**：奖励直接进入背包；背包已满或玩家离线/死亡时自动存入持久化**待领暂存库**，聊天栏提供可点击领取按钮，并在下次登录时自动提醒。
- **`HYBRID`（混合模式）**：优先直接进背包，背包满则受保护掉落在玩家脚下（离线/死亡则进暂存库）。

### 伤害贡献结算播报与实用指令
- 击杀受追踪 Boss 后向参战玩家播报清晰美观的**伤害贡献结算单**（排名、有效伤害、伤害占比、获奖状态）；文本按玩家客户端语言在服务端渲染，未装本模组的客户端同样显示完整中/英文，不会出现原始语言键。
- **内置指令**（`/fairbossloot` 或短别名 `/fbl`）：
  - `/fbl claim` — 从待领暂存库领取战利品到背包。
  - `/fbl list` — 预览待领暂存库中的物品列表与来源 Boss。
  - `/fbl clear <玩家>` *（管理员）* — 清空指定玩家的待领暂存库。
  - `/fbl inspect <目标实体>` *（管理员）* — 实时查看目标生物当前的伤害贡献追踪详情。

### 开箱即用的 TOML 配置与标签扩展
- 所有规则均可通过 `config/fair_boss_loot-common.toml` 直接调整，无需手写数据包。
- 默认通过标签 `#fair_boss_loot:eligible_entities` 覆盖 `#c:bosses` / `#forge:bosses`、末影龙、凋灵、监守者与远古守卫者，并支持黑白名单（支持 `modid:id`、`modid:*`、`#tag`）、高血量精英自动纳入、全局全生物开关及 `#fair_boss_loot:non_duplicable` 禁复制物品清单。
