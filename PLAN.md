# Millénaire → Minecraft 26.2 (Fabric) 遷移計畫

> 狀態：規劃中 · 最後更新 2026-06-20
> 目標：在 **Fabric / Minecraft 26.2** 上做一個 **全功能對等** 的 Millénaire clean-room 重寫。
> **設計依據**：[`docs/INTENT.md`](docs/INTENT.md)（意圖分析書總綱）+ `docs/intent/01–06`（各子系統意圖分析）。實作每層前先讀對應子檔。

---

## 1. 專案目標與原則

- **目標**：把 Millénaire（千年村莊）遷移到 Minecraft **26.2**，loader 用 **Fabric**，達到與舊版（1.12.2 / 8.1.1）**全功能對等**。
- **核心原則**（使用者拍板）：
  1. **複製設計意圖**，不照搬舊實作——舊 1.12.2 很多做法已不適合現代 MC。
  2. **復用美術與內容資料**（材質、模型、建築 schematic、村民/任務/語言資料）。
  3. **Java 程式碼全部重寫**（clean-room）；舊源碼只當設計參考。
- **沒有文檔** → 靠 **反編譯 Minecraft 官方源碼**（Mojmap）找可用 API。這是現階段唯一可靠的「文檔替代品」。

---

## 1.5 已拍板決策（Locked decisions）

| # | 決策 | 選擇 | 影響 |
|---|---|---|---|
| D1 | 發布與授權 | **Clean-room 後公開發布** | 程式全自寫；復用美術/內容、標註來源後公開；授權風險自行承擔（見 §8，仍**建議發布前釐清/聯繫原作者**） |
| D2 | 多人/伺服器 | **第一級目標** | L0 起即照 client-server 架構，封包同步、server 權威從頭做對；不留「單人捷徑」 |
| D3 | 首個里程碑 | **全文化資料一起載入** | L1 一次讀齊五大文化（byzantines/hindi/japanese/mayan/norman）；功能仍逐層推進 |
| D4 | 命名空間 / mod id | **沿用 `millenaire`** | registry ID、資產路徑、內容引用免改，相容最大 |

---

## 2. 目標技術棧（已確認版本）

| 項目 | 版本 | 備註 |
|---|---|---|
| Minecraft | **26.2** | RC1 = 2026-06-11；2026 起改年份制版本號（26 = 2026 年第二季 drop） |
| Loader | **Fabric Loader 0.19.3** | Millénaire 歷來是 Forge/NeoForge，**無 Fabric 基礎** → 全新架構 |
| Fabric API | **0.152.2+26.2** | 2026-06-18 |
| Loom | **1.17** | Gradle plugin |
| Gradle | **9.5.1** | |
| Java | **25** | 26.x 工具鏈要求（待最終確認，1.21=Java21、26.x 上調） |
| Mappings | **Mojang 官方 (Mojmap)** | 自 26.1 起 MC 已去混淆；**Yarn 已停止支援** |

參考連結：
- Fabric for 26.2 — https://fabricmc.net/2026/06/15/262.html
- Porting to 26.1（mapping/breaking changes 說明） — https://docs.fabricmc.net/develop/porting/

---

## 3. 反編譯 / 開發工作流

由於缺文檔，標準流程是讓 Loom 反編譯出可讀的 MC 源碼來查 API：

1. `./gradlew genSources` → Loom 用 Mojmap + Vineflower 反編譯 Minecraft，產出可讀 Java。
2. 在 IDE 直接「跳到定義」讀官方類別/方法簽名（例如 worldgen、entity AI、structure、registry 等）。
3. 對照舊 1.12.2 子系統的「設計意圖」，在 26.2 找對應的新 API 重新實作。
4. 遇到不確定的 API，先用現代移植版（gblfxt NeoForge / DizzyMii）當「別人怎麼翻譯」的樣本，再驗證在 Fabric 是否適用（NeoForge↔Fabric API 不同，需轉換）。

---

## 4. 參考資產盤點（已下載到 `_reference/`）

| 路徑 | 來源 | 內容 |
|---|---|---|
| `_reference/kinniken-src/` | github.com/Kinniken/Millenaire（原作者官方 repo） | **Java 源碼 163 檔 / 61.6k 行** + 完整內容 |
| `_reference/public/` | gitlab.com/Millenaire/Public | 內容資料（buildings/villagers/languages/quests），含社群翻譯 |

**內容統計**（kinniken-src/millenaire/）：
- 文化：`byzantines / hindi / japanese / mayan / norman`（每個含 buildings/villagers/villages/shops/namelists/quests）
- **1128 個 `.txt`**（DSL 定義）+ **959 個 `.png`**（建築分層圖 + 材質）
- 語言：`en / fr`（Public repo 另有更多社群語言）

**其他可參考的現代移植版**（study-only，不直接複製）：
- `gblfxt/Millenaire-rewrite-1.21.1`（NeoForge 1.21.1，clean-room）
- `DizzyMii/Millenaire_port`（1.12.2 → 1.21.1 NeoForge）
- `Leviaria/Millenaire-Reborn`（Fabric，1.21.8；目前 404，可能轉私有）— 路線與本專案最接近

---

## 5. 舊源碼子系統地圖（Java，按規模）

| 子系統 (package) | 檔數 | 處理策略 |
|---|---|---|
| `common/goal`（+ leasure/generic） | 53 | **重寫**：NPC AI/職業/作息。設計意圖保留，改用現代 MC `Goal`/`Brain` 系統 |
| `common`（核心：MillVillager/Building/Village…） | 25 | **重寫**：核心領域模型 |
| `client/gui` | 17 | **重寫**：用現代 Screen/widget；UI 佈局沿用 |
| `common/building` | 11 | **重寫**：建築/升級系統 + schematic 載入 |
| `common/pathing/atomicstryker`（自帶 A*） | 9 | **替換**：改用 MC 原生 pathfinding，除非有特殊需求 |
| `common/forge` + `client/forge` | 10 | **丟棄**：Forge 專屬，改寫成 Fabric entrypoint/event |
| `common/block` `item` `entity` `network` | 14 | **重寫**：用現代 registry / networking (payload) API |
| `client/texture` 等其餘 | ~24 | **重寫/調整** |

> 重點：`goal`（NPC 行為）與 `building`（schematic）是兩個最大、最易被低估的子系統，也是 Millénaire 的靈魂。

---

## 6. 內容 DSL 格式策略 — 評估與建議

### 舊格式現況（意圖分析實測 — 共 **4 種微 DSL**，詳見 `docs/intent/03`、`04`）
1. **`key=value` 行式**：村民/shops/config/語言。多值用重複 key 或逗號。例（carpenter.txt）`native_name=Charpentier` / `goal=makeTimberFrameOak` / `requiredGood=wood,64`。
2. **`key:value` 冒號式**：建築計畫 / villages / lonebuildings。**value 不可含 `:`**。例（armoury_A.txt 首行）`max:1;priority:50;width:13;length:7;shop:armoury;tag:armoury;...`。
3. **`a;b;c` 分號式**：goods.txt / dialogues / reputation。
4. **逗號 CSV**：traded_goods。
- **跨格式通用慣例**：key 大小寫不敏感、good 名轉小寫、**重複 key = list append**（核心慣用法）、雙語值寫 `法文 / 英文` 整段原存、base-64 貨幣兩記法 `a/b/c`(進位) 與 `a*b`(連乘) 不可混用。
- **建築幾何在分層 PNG**：單檔水平並排所有 Y 樓層（`nbfloors=(imgWidth+1)/(width+1)`）、像素 X 鏡像、alpha≠0xFF=empty；色→方塊經 `blocklist.txt`（5 欄 `name;blockRef;meta;secondStep;R/G/B`）。**編碼規則已完整解出於 `docs/intent/03`。**

### 決定（使用者授權我評估後拍板）：**保留原 DSL + 寫新 Fabric 端解析器（hybrid）** ✅

**理由：**
1. **量太大**：2000+ 內容檔。全轉成 datapack JSON 本身就是一個獨立的大工程，延後一切、且有保真度流失風險。
2. **PNG 分層 schematic 無 MC 原生等價物**：MC `.nbt` structure 會丟失每棟建築的升級路徑（`startLevel`、多階 `_A/_B`）與 shop/tag 等語義。
3. **解析器成本低**：四種格式都簡單，寫 4 個 parser 比批次轉檔可靠，且能**立即 100% 復用內容 + 社群翻譯**。
4. **可擴充**：解析後存進現代 runtime 資料結構；對**新內容**另開 datapack/JSON 疊加管道。雙語值/重複 key 池使原版 lang.json 不夠用 → 在地化也走自訂格式。

**結論：bulk 沿用原 DSL（L1 寫 4 個 parser + PNG 解碼），新內容走 datapack。** L1 實作時若發現難解處再局部轉換。**首個里程碑一次讀齊五大文化（D3）。**

---

## 7. 目標架構分層（L0–L7）

> 「全功能對等」是終點；路線仍逐層可跑、逐步補齊，不會等到最後才第一次啟動。

> **⭐ 來自意圖分析的關鍵回饋**：1.12.2 的**方塊 metadata（`Block + int meta`）**滲透在 building / content / economy / crops 四個子系統，而 26.2 已無 metadata。**因此 L0 就要先立「邏輯方塊名 ↔ 26.2 BlockState」映射層**——做對了，2000+ 內容檔（PNG/blocklist/goods）可一次性免改，這是整個遷移成敗的關鍵（INTENT.md §5 耦合 A）。同理 `GuiText` 多頁多行文字框架是所有 UI 的地基，也宜早立雛形。

| 層 | 內容 | 完成判準 (Definition of Done) |
|---|---|---|
| **L0 骨架** | Gradle/Loom 專案、Java 25 工具鏈、`genSources` 反編譯環境、mod 能在 26.2 載入、註冊 1 個測試 item/block（namespace `millenaire`/D4）；**⭐ 立「邏輯方塊名 ↔ BlockState」映射層骨架（耦合 A）**；**⭐ `GuiText` 文字框架雛形**；**⭐ client-server payload 通道骨架（D2 多人第一級，先跑通 1 個 server→client 同步封包）** | mod 在**專用伺服器 + 客戶端**皆載入；映射層能把邏輯方塊名解析成 26.2 BlockState；GuiText 能畫一頁多行文字；測試封包能 server→client 同步 |
| **L1 內容載入器** | **4 個微 DSL parser**（`key=`/`key:`/分號/CSV）+ **分層 PNG 解碼**（鏡像/alpha/nbfloors 公式）+ blocklist 5 欄解析 + **good 名中介層**（good→方塊/item，接 L0 映射層）→ runtime 資料結構；保留重複 key=append、雙語值、base-64 貨幣記法 | **一次讀齊五大文化全部資料（D3）**；log 印出各文化建築清單與 good 對應無錯；任挑一棟建築能還原成方塊座標表 |
| **L2 世界生成** | 村莊選址、文化分配、結構放置（對接 26.2 worldgen/structure API） | 新世界能生成一個空村莊地基於合理地點 |
| **L3 建築系統** | schematic 逐步建造、升級階（`_A/_B`、startLevel） | 村莊隨時間建出 core 建築並能升級 |
| **L4 NPC** | 村民實體、AI goals、作息、姓名/性別/職業、繁衍、關係 | 村民生成、走動、執行職業 goal、能繁衍 |
| **L5 經濟** | 生產、貿易 GUI、貨幣（denier）、shops | 玩家能與村民交易、村莊有生產循環 |
| **L6 玩家進度** | 聲望、任務、卷軸/lore、控制村莊 | 任務可接可完成、聲望影響互動、可建控制村莊 |
| **L7 全文化 + 打磨** | byzantines/hindi/japanese/mayan/norman 全跑通、UI 打磨、效能、（未來 Public repo 更多文化/語言） | 五大文化皆可遊玩、與舊版功能對等 |

---

## 8. 風險與未決事項

- **授權 (License)** ⚠️（**決策 D1：clean-room 後公開發布**）：Kinniken/Millenaire repo **無 LICENSE 檔**；Millénaire 歷來用自訂授權。既然要公開發布，**程式 100% 自寫**、復用的美術/內容須**標註來源**，且**強烈建議發布前聯繫原作者取得美術/內容使用許可**（社群移植版都強調 clean-room 即因此）——這是公開路線下最大的法務風險，未釐清前不要對外發布。
- **⭐ metadata 映射層（耦合 A）**：方塊 metadata 消失橫跨 building/content/economy/crops 四系統，映射層設計品質決定整個遷移成敗 → 已提前到 L0。
- **⚠️ silent-air 風險（Codex 審計 P2）**：扁平化表不完整時，未知 logical block 經 `resolve()` 回 AIR、被 placer 跳過 → 建築「看似成功但缺方塊」。緩解：載入期輸出**覆蓋率報告**（量化 distinct 建築方塊有多少未對映、列出 top 未對映名稱），把無聲風險變成可量測數字；後續補完扁平化表至接近 100%。完整對映前不可視為「復用 PNG 成功」。
- **⭐ worldgen 哲學衝突（耦合 B）**：舊版「每區塊 lazy 依鄰近地形選址」與現代 deterministic worldgen 衝突 → L2 改「玩家附近 post-gen」放置，需早定方案。
- **Java 25**：26.x 確切 JDK 需在 L0 用 Loom 實測確認。
- **NPC AI 架構**：意圖分析建議**保留自訂「單一最高優先序」排程器**（湧現作息），而非原生 `GoalSelector`（多 goal+flag 互斥）；於 L4 落定（詳 `docs/intent/01`）。
- **Pathfinding**：原版自帶 A*（atomicstryker），改用 MC 原生 `PathNavigation`+自訂 `NodeEvaluator`，**保留 per-goal 尋路設定 + 不可達瞬移防卡死**。
- **玩家識別**：舊版用顯示名 → 必須改 **UUID**（含舊存檔遷移）。
- **26.2 仍在 RC**：API 可能有最後變動；以正式版為準。

---

## 9. 下一步

1. ~~計畫定稿~~ ✅
2. ~~**L0 骨架**~~ ✅ **完成（2026-06-20）**：Gradle+Loom 1.17.11 / Fabric API 0.152.2+26.2 / Java 25 專案；`genSources` 反編譯就緒；三地基（LogicalBlockMapping 映射層、GuiText 純資料模型、client-server payload 通道）皆建立；**`gradlew build` 通過、專用伺服器實測載入成功**（`millenaire:debug_wand` 已註冊、handshake 封包註冊無錯）。26.2 API 差異記於 [`docs/API-NOTES-26.2.md`](docs/API-NOTES-26.2.md)。
> **⚠️ 進度真實性聲明（2026-06-20，採納 Codex 審計）**：下方 ✅ 一律指「**vertical slice 已實測可運作**」，**不等於 docs 設計意圖的功能完成**。實作目前到「可編譯可跑的垂直切片」：能載入內容、解碼 schematic、生成村莊、spawn 村民。**尚未達 docs 意圖的部分**：資料驅動 goal 排程器（L4 用 vanilla goal）、`MillWorld`/Town Hall 聚合根與持久化/active 狀態機、逐塊可中斷資源驅動建造（含 `secondStep`/功能點/orientation/升級疊加）、完整內容 DSL（villagers/shops/quests/langs/traded_goods）、server 權威同步（只有 handshake）、完整 block 扁平化表。詳見 §7「剩餘工作」。

3. ~~**L1 內容載入器**~~ ✅ **切片（2026-06-20）**：4 個 DSL parser + 分層 PNG 解碼 + good 中介層 + blocklist→LogicalBlockMapping（**僅常見方塊扁平化種子表**）。**伺服器實測**：五大文化全載入、298 棟建築解碼 0 失敗、goods 312、blocklist 392（94 解析到 26.2 方塊）；自測 `armoury_A` 99% 解析。**未達**：villagers 目前只計數、shops/namelists/languages/quests/traded_goods 未解析；扁平化表不完整 → 未知方塊會被跳過（見 §8 silent-air 風險，已加載入期覆蓋率報告）。
4. **L1→世界橋接 + 測試框架** ✅（2026-06-20）：`BuildingPlacer` 把解碼的 schematic 寫進真實世界（`ServerLevel.setBlock`）；debug wand 右鍵建造（真實 `UseBlockCallback`）；**Fabric `FakePlayer` headless 測試**——假玩家真的手持法杖右鍵、走完整玩家流程、再讀回方塊（`MILLENAIRE_SELFTEST=1`，見 `docs/DEV.md`）。實測：假玩家建出 armoury_A 287 方塊、讀回 `oak_planks`/`cobblestone` 與 schematic 一致。
5. ~~**L2 世界生成（首切片）**~~ ✅（2026-06-20）：`VillageType` 解析（centre/start/core）+ `VillageGenerator` 在真實世界生成整個村莊（地面對齊、玩家附近 post-gen／耦合 B）。**假玩家實測**：右鍵 founded "Gros Bourg" → 5 棟建築（largefort/guardhouse×2/inn/carpenterhouse）1370 方塊、0 缺失、讀回確認。村莊選址 biome 規則/間距/道路為後續細化。
6. ~~**L4 活村民（首切片）**~~ ✅（2026-06-20）：`MillVillagerEntity`（PathfinderMob + 漫遊/看玩家/浮水 AI）註冊 + 屬性 + 命名；村莊生成時 spawn。**假玩家實測**：founded 村莊 spawn 3 村民（`Norman villager 1/2/3`）、讀回確認存活。client renderer + Millénaire 資料驅動 goal 系統（doc 01）為後續。
7. ~~**MillWorld + Town Hall 聚合根（採納 Codex P1，二輪深化）**~~ ✅（2026-06-20）：
   - `MillWorldData`（Codec `SavedData`）= 村莊**單一來源 + 持久化**；town-hall 清單**唯讀 view + mutation methods**（setDirty 不漏）。
   - **`TownHall` 聚合根**：stable UUID id、centre、culture/type、**子建築清單**（`BuildingRecord`：key/variant/role/origin/level/orientation）、**村民 UUID 清單**；生成時登記實際放置建築與村民 UUID。
   - **去重**：同地點不重複 founded（`MIN_VILLAGE_DISTANCE`，`findNear`）。
   - **active/inactive**：per-village 狀態 + `setActive` 轉移入口 + `MillWorld.onTransition` hook（供後續建造/NPC tick 掛入）。
   - **實測**：run#1 founded `Gros Bourg` id=7d0f69f6…[5 buildings,3 villagers]→落盤；run#2 `loaded 1 at startup`（持久化）+ `not founding a duplicate`（去重）。覆蓋率報告改顯示 0.77%。
   - **仍 TODO**：chunk forcing + active 重模擬實體（hook 已留）、聲望/控制權欄位、玩家 UUID 綁定。
8. ~~**L4 NPC goal 排程器（slice，issue #1）**~~ ✅（2026-06-20）：自訂「單一 `goalKey`、最高 `priority()` 勝、無狀態 singleton goal、per-villager 狀態持久化（`ValueOutput/Input`）」排程器，**非** vanilla 平行 `GoalSelector`；由 `MillWorld` active tick 驅動。4 個 goal（observe_construction/go_to_townhall/wander/idle）。**實測**：建造中選 observe_construction → 建完切 go_to_townhall↔wander 循環（41 次選擇，狀態驅動切換）。**村莊歸屬邊界（採納審計）**：scheduler 改用 **TownHall 的 villager UUID 成員清單**驅動（`getEntity(UUID)`），不再 AABB 掃描；找不到 entity 會記錄（repair/respawn 為後續）。**chunk forcing（#6 部分）**：active force-load + **啟動時釋放殘留 forced chunks**（runtime-only `active` 的洩漏修正，實測 `released leftover forced chunks`）。**未達**：完整作息/職業/生產/繁衍、villager 實體 reload 續存與 repair、generic goals。
9. **剩餘工作（全功能對等的長路）**：
   - **L3 建造系統**：村民逐塊蓋建築（非瞬間放置）、升級階 `_A→_B`、道路/朝向佈局。
   - **L4 完整 NPC**：資料驅動 goal 排程器（單一最高優先序湧現）、職業/作息/生產/繁衍、原生 pathfinding + per-goal 設定。
   - **L5 經濟/GUI**：貿易、denier 貨幣、`GuiText` 接 `extractRenderState`、村民/村莊面板。
   - **L6 玩家進度**：聲望許可制、quest（player-tag 鏈）、lore/卷軸、控制村莊。
   - **L7 全文化打磨**：五大文化全玩法、client 渲染/材質、效能、選址 biome/間距規則、metadata 扁平化表補完。
   - **跨切**：client 端實體 renderer；內容打包進 `resources`（目前 dev 從 `_reference` 讀）；授權 D1 釐清。

---

## 附錄：目錄規劃（建議）

```
mcMillenaire/
├─ PLAN.md                  # 本文件（開發計畫）
├─ docs/
│  ├─ INTENT.md             # 意圖分析書總綱
│  └─ intent/01–06*.md      # 各子系統意圖分析
├─ _reference/              # 參考用，不參與建置（建議 .gitignore）
│  ├─ kinniken-src/         # 原作者 1.12.2 源碼 + 內容
│  └─ public/               # GitLab Public 內容（含社群翻譯）
├─ src/main/java/...        # 新 Fabric Java 程式碼（待建）
├─ src/main/resources/
│  ├─ fabric.mod.json
│  ├─ assets/...            # 復用材質/模型
│  └─ content/...           # 復用原 DSL 內容（L1 解析）
└─ build.gradle / settings.gradle
```
