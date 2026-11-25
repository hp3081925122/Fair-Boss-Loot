<p align="center">
  <img src="curseforge/icon.png" alt="Fair Boss Loot Icon" width="160" />
</p>

# Fair Boss Loot

**Fair Boss Loot** is a **pure server-side** Minecraft mod that gives every player who meaningfully contributes damage to defeating a boss or tracked mob their own separate, protected loot drops and experience—instead of everyone fighting over a single shared pile.

- **CurseForge**: https://www.curseforge.com/minecraft/mc-mods/fair-boss-loot
- **Supported Versions**:
  - `1201-forge-Fair Boss Loot` — Minecraft `1.20.1` (Forge, Java 17)
  - `1211-neoforge-Fair Boss Loot` — Minecraft `1.21.1` (NeoForge, Java 21)
  - `2612-neoforge-Fair Boss Loot` — Minecraft `26.1.2` (NeoForge, Java 25)

## Features

- **Server-side only**: Clients do not need to install the mod to join a multiplayer server (also works in singleplayer and LAN), with server-side per-player localization (`en_us` / `zh_cn`).
- **Effective damage tracking**: Tracks real health + absorption damage dealt per player (clamped against overkill), configurable damage threshold (`5%` of max health by default, or flat), combat time window (`60s`), max rewarded player cap, killer policy (`ALWAYS` / `FILL` / `REQUIREMENTS`), and automatic owner attribution for projectiles, tamed pets (`OwnableEntity`), and common modded summons (ignoring `FakePlayer`).
- **Full-compat hybrid drop pipeline**:
  - Independently re-rolls loot tables per eligible player with their own Looting enchantment, Luck, and perturbed seed.
  - Captures and duplicates `dropCustomDeathLoot` drops (e.g., Wither Nether Star & modded bosses) and third-party `LivingDropsEvent` extra drops without canceling the event.
  - Excludes worn/picked-up mob equipment by default to prevent item-duplication exploits.
- **Personal experience orbs**: Supports `FULL_PER_PLAYER`, `PROPORTIONAL`, and `VANILLA` modes (including multi-wave Ender Dragon XP), isolated per owner and natively compatible with Mending.
- **Three delivery modes & protection**: `ISOLATED_DROP` (per-player visibility/pickup, fire/lava/cactus/explosion immunity, hopper theft blocking, void rescue into vault), `DIRECT_INVENTORY`, and `HYBRID` with a persistent **Pending Loot Vault**.
- **Damage summary & commands**: Localized post-kill damage ranking summary in chat and `/fairbossloot` (`/fbl`) commands (`claim`, `list`, `clear`, `inspect`).

## Building

Enter the target version directory and build with the matching JDK:

```powershell
# 1.20.1 Forge (Java 17)
cd "1201-forge-Fair Boss Loot"
.\gradlew.bat jar

# 1.21.1 NeoForge (Java 21)
cd "1211-neoforge-Fair Boss Loot"
.\gradlew.bat jar

# 26.1.2 NeoForge (Java 25)
cd "2612-neoforge-Fair Boss Loot"
.\gradlew.bat jar
```

---

# Fair Boss Loot（公平首领战利品）

**Fair Boss Loot** 是一款**纯服务端** Minecraft 模组：在多人合作击败 Boss 或受追踪生物时，根据实际伤害贡献，为每位达标玩家分别发放独立且受保护的战利品与经验，不再抢夺同一份掉落。

- **CurseForge**：https://www.curseforge.com/minecraft/mc-mods/fair-boss-loot
- **支持版本目录**：
  - `1201-forge-Fair Boss Loot` — Minecraft `1.20.1`（Forge，Java 17）
  - `1211-neoforge-Fair Boss Loot` — Minecraft `1.21.1`（NeoForge，Java 21）
  - `2612-neoforge-Fair Boss Loot` — Minecraft `26.1.2`（NeoForge，Java 25）

## 核心特性

- **纯服务端免装**：联机服务端单端安装即可，客户端无需安装（同时兼容单人存档与局域网）；内置服务端按客户端语言本地化渲染（简体中文 / 英文），未装模组客户端也不会出现原始语言键。
- **有效伤害追踪与防白嫖**：截断溢出伤害并计入吸收护盾，支持最低伤害占比/固定值（默认 `5%`）、有效时间窗口（默认 `60` 秒）、人数上限、击杀者策略（`ALWAYS` / `FILL` / `REQUIREMENTS`），自动追溯投射物、驯服宠物及常见模组召唤物主人（自动排除假人 `FakePlayer`）。
- **全兼容混合掉落管线**：按每位达标玩家各自的抢夺与幸运独立重掷战利品表；捕获并按人发放 `dropCustomDeathLoot` 与第三方 `LivingDropsEvent` 额外掉落而不吞事件；默认不复制生物穿戴装备以防刷复制漏洞。
- **专属经验球**：支持 `FULL_PER_PLAYER`、`PROPORTIONAL`、`VANILLA`（含末影龙分批经验），归属隔离且原生触发经验修补。
- **三种发放模式与全方位防毁**：`ISOLATED_DROP`（仅本人可见可捡、火/岩浆/仙人掌/爆炸免疫、漏斗防偷、虚空自动救援进暂存库）、`DIRECT_INVENTORY` 与 `HYBRID`，附持久化待领暂存库与登录可点击领取提醒。
- **伤害结算播报与指令**：击杀后播报伤害排名与获奖状态，提供 `/fairbossloot`（`/fbl`）指令（`claim` / `list` / `clear` / `inspect`）。
