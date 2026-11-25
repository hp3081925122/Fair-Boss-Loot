## Fair Boss Loot 1.0.0 — Initial Release

- **Server-side only**: No client installation required for multiplayer servers (also works in singleplayer/LAN), with server-side per-player localization (`en_us` / `zh_cn`) so unmodded clients never see raw translation keys.
- **Damage contribution tracking**: Tracks effective damage (clamped against overkill, including absorption shields), configurable damage threshold (percentage or flat), combat time window, player cap, killer policy (`ALWAYS` / `FILL` / `REQUIREMENTS`), and automatic owner attribution for projectiles, tamed pets, and modded summons (ignoring `FakePlayer`).
- **Full-compat hybrid drop pipeline**: Independently re-rolls entity loot tables per eligible player with their own Looting/Luck, duplicates `dropCustomDeathLoot` & third-party `LivingDropsEvent` extra drops without canceling events, and excludes mob worn equipment by default to prevent duplication exploits.
- **Personal experience orbs**: Supports `FULL_PER_PLAYER`, `PROPORTIONAL`, and `VANILLA` modes (including multi-wave Ender Dragon XP), isolated per owner and natively compatible with Mending.
- **Three delivery modes & drop protection**: `ISOLATED_DROP` (per-player visibility/pickup, fire/lava/cactus/explosion immunity, hopper theft blocking, void rescue to vault), `DIRECT_INVENTORY` (direct to inventory + persistent Pending Loot Vault), and `HYBRID`.
- **Chat summary & commands**: Localized post-kill damage contribution summary and `/fairbossloot` (`/fbl`) commands (`claim`, `list`, `clear`, `inspect`).

---

## Fair Boss Loot 1.0.0 — 首次发布

- **纯服务端运行**：联机服务端单端安装即可，客户端免装（同时支持单机与局域网）；内置服务端多语言渲染（简体中文 / 英文），未装模组的客户端也能正常显示本地化提示。
- **伤害贡献追踪**：精确统计有效伤害（截断溢出伤害并计入吸收护盾），支持最低伤害占比/固定值、有效时间窗口、人数上限、击杀者兜底策略（`ALWAYS` / `FILL` / `REQUIREMENTS`），自动追溯投射物、驯服宠物与常见模组召唤物主人（自动排除 `FakePlayer`）。
- **全兼容混合掉落管线**：按每位达标玩家各自的抢夺与幸运独立重掷战利品表；自动捕获并按人发放 `dropCustomDeathLoot` 与第三方 `LivingDropsEvent` 额外掉落而不吞原事件；默认排除生物穿戴装备防止复制漏洞。
- **专属经验球分配**：支持 `FULL_PER_PLAYER`、`PROPORTIONAL`、`VANILLA` 三种模式（兼容末影龙分批经验），归属隔离且原生触发经验修补。
- **三种发放模式与完整保护**：支持 `ISOLATED_DROP`（仅本人可见可捡、火/岩浆/仙人掌/爆炸免疫、漏斗防偷、虚空自动救援进暂存库）、`DIRECT_INVENTORY`（直发背包 + 持久化待领暂存库）与 `HYBRID`。
- **伤害结算播报与指令**：击杀后播报参战伤害排名与获奖状态，提供 `/fairbossloot`（`/fbl`）全套领取与排查指令（`claim` / `list` / `clear` / `inspect`）。
