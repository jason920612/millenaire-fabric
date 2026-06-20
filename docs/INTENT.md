# Millénaire 意圖分析書（總綱）

> 目的：在不保留 1.12.2 實作的前提下，捕捉 Millénaire（千年村莊）的**設計意圖、規則、資料契約與玩家體驗**，作為 Fabric / Minecraft 26.2 clean-room 重寫的權威依據。
> 編成：由 6 個並行子分析（`docs/intent/01–06`）綜合而成。本檔是跨子系統綱要；細節見各子檔。
> 狀態：草案 v1 · 2026-06-20 · 來源 `_reference/kinniken-src/`（原作者 Kinniken/Millenaire，163 檔 / 61.6k 行 + 內容 1128 txt / 959 png）

---

## 0. 子文件索引

| # | 子系統 | 檔案 |
|---|---|---|
| 01 | NPC AI 與 Goal（村民行為、作息、生產、戰鬥） | [`intent/01-npc-ai-goals.md`](intent/01-npc-ai-goals.md) |
| 02 | 核心領域與世界模擬（village 生命週期、tick、持久化） | [`intent/02-core-domain-world.md`](intent/02-core-domain-world.md) |
| 03 | 建築與 Schematic（plan DSL、分層 PNG、升級、放置） | [`intent/03-building-schematic.md`](intent/03-building-schematic.md) |
| 04 | 內容 DSL 與文化（文化組成、欄位字典、經濟資料、在地化） | [`intent/04-content-dsl-cultures.md`](intent/04-content-dsl-cultures.md) |
| 05 | GUI／任務／玩家進度（聲望、quest、lore、控制村莊、同步） | [`intent/05-gui-quests-progression.md`](intent/05-gui-quests-progression.md) |
| 06 | 物品／方塊／交易／網路（註冊物件、貨幣、封包、強制載入） | [`intent/06-items-blocks-trade-net.md`](intent/06-items-blocks-trade-net.md) |

---

## 1. 執行摘要：Millénaire 是什麼

Millénaire 在原版空蕩的世界裡，放進**會自己活下去的歷史村莊**。每個村莊以一座**市政廳（Town Hall）**為核心，依某個**文化**（諾曼/印度/日本/馬雅/拜占庭…）的藍圖，由村民**一塊塊蓋出建築**、生產與交易物資、結婚生子、世代延續；玩家透過**交易累積聲望**，逐步解鎖互動，接**任務**、讀**傳說**，最終能**控制甚至親手建立**村莊。

它的工程靈魂只有一句話：**引擎在 Java，世界在資料。** 文化、建築、村民、經濟、任務、傳說、語言全部寫在 txt/png 內容檔裡；Java 只是讀資料並讓它活起來的引擎。**這條 code/content 界線是整個重寫的最高指導原則。**

---

## 2. 跨子系統的核心架構支柱（重寫必須保留的承重牆）

這些是橫跨多個子系統、決定整體手感的根本設計。改動它們等於改變 Millénaire 的本質。

1. **資料驅動（Data-driven）— 最高原則。**
   新增一個文化 = 複製一個目錄；新增建築 = 一個 `.txt` + 分層 `.png`；新增配方/作物/交易品 = 改 txt，**零 Java**。硬編碼 Java 只負責「資料表達不了的特殊行為」。重寫若把內容硬編進 Java，就摧毀了這個 mod。（見 01 §generic goals、03、04）

2. **Town Hall Building＝村莊聚合根（單一擁有者）。**
   `isTownhall` 的 Building 持有：子建築清單、村民記錄(vrecords)、建築進度、村↔村外交、玩家控制權，並整批序列化成一個 `<pos>.gz`（原子寫入）。村莊的一切歸它擁有。（見 02）

3. **雙層資料粒度 + 鄰近度活躍狀態機。**
   `MillWorld` 常駐一份**輕量文字索引**（`villages.txt`，即使村莊未載入也在，供距離/列表/地圖），重實體（Building NBT）按需載入。玩家在 `KeepActiveRadius`(200) 內 → 強制載入區塊＋完整模擬；離開 → 凍結，**但世界時間照走，回來看到成長結果**——這正是 Millénaire 的核心體感。`BackgroundRadius`(2000) 是「能收 raid/訊息」的第二層。（見 02、06 強制載入）

4. **邏輯無狀態、實體持狀態（goal 系統的地基）。**
   goal 是全域**無狀態單例**，村民只持有 `goalKey` 字串 + per-villager 狀態。`setNextGoal()` 從所有 `isPossible()` 的 goal 選 `priority()` 最高者，**同時只跑一個 goal**。（見 01）

5. **湧現式行為，而非腳本化時間表。**
   一天作息、生產量調節、多人分工、戰鬥協調——全靠**閘門 + 優先序**湧現，沒有中央排程器：日夜閘門、leisure 讓位於工作、三道生產上限閘門、戰鬥時由 `townHall.underAttack` 由上而下翻全村行為。重寫別引入硬編碼時刻表。（見 01）

6. **good 名＝經濟的通用中介層。**
   所有經濟引用（生產、交易、建材、價格）都走**抽象 good 名**，再由全域 `goods.txt` 綁到具體 item(+meta)。換 item 不必改各文化資料。（見 04、06）

7. **server 權威 + 樂觀本地預測。**
   server 是唯一真相，連「該開哪個 GUI」都由 server 用 `PACKET_OPENGUI` 決定；client 送出意圖後**先在本地跑一次**求即時回饋，再被 server 權威封包覆寫。（見 05、06）

8. **聲望＝許可制漸進揭露，而非數值等級。**
   聲望不給屬性加成，而是**逐步點亮同一畫面上更多按鈕**；階梯由內容檔 `<culture>_reputation.txt` 定義。這種「關係愈好、能做的事愈多」的漸進感是進度主軸。（見 05）

---

## 3. 玩家體驗循環（端到端）

```
發現村莊 ──> 與村民交易（花錢買=↑聲望 ↑語言熟練度）
                   │
                   ▼
        聲望階梯解鎖更多互動（許可制揭露）
                   │
        ┌──────────┼───────────┐
        ▼          ▼           ▼
   接任務(quest)  讀傳說/招牌   雇傭/祭祀
   ├ 世界任務線靠 player tag 串接（探索下潛/登頂/鑽探 → 解 tag）
   └ 變數展開 $name/$村莊方位$ 讓村民「對你本人說話並指路」
                   │
                   ▼
   控制既有村莊（當領主下高層指令：升級/外交/突襲/建毀）
                   │
                   ▼
   用召喚法杖親手建立新村莊
```

並行的村莊自身循環：**選址生成 → 村民蓋 core 建築 → 升級階(_A→_B) → 生產搬運入庫 → 開店交易 → 繁衍續存 → 受攻擊時全村切戰鬥**。（見 02、03、04、05）

---

## 4. 必須照搬的硬格式契約（byte-level，不可即興）

這些是內容檔（1128 txt + 959 png）依賴的精確契約。**parser 必須完全相容，否則 2000+ 內容檔全壞。** 細節在子檔，這裡列清單：

**分層 PNG schematic（03）**
- 單檔水平並排所有 Y 樓層，欄間 1px 分隔：`nbfloors = (imgWidth+1)/(width+1)` 須整除；`height == length`(Z)；floor index = Y。
- 像素 X **左右鏡像**存放（`px = i*width + i + (width-k-1)`）；不翻會整棟左右顛倒。
- `alpha != 0xFF` 或純白 = empty（不覆蓋）；升級 PNG 靠透明只畫差異。

**blocklist.txt（03）**：每行 5 欄 `name;blockRef;meta;secondStep;R/G/B`；顏色 `(R<<16)|(G<<8)|B` 為唯一 key；`blockRef` 空 = special tag（功能點，非方塊）；`secondStep=true` 的方塊（門/床/火把/水/招牌）第二遍才放。

**建築 plan DSL（03）**：`;` 分隔、`key:value`（value 不可含 `:`）；檔名雙軸 `<key>_<變體字母><升級數字>`；`.txt` 第 N 非空行 = level N config，升級行只寫增量並繼承 level 0；`startLevel`(可負) = 整體 Y 平移。

**內容微 DSL（04）**：四種格式並存（`key=value` 行式 / `key:value` 冒號式 / `a;b;c` 分號式 / 逗號 CSV），需四個 parser；key 大小寫不敏感、good 名轉小寫；**重複 key = list append**（核心慣用法）；雙語值寫 `法文 / 英文` 整段原存；base-64 貨幣兩種記法 `a/b/c`(進位) 與 `a*b`(連乘) 不可混用。

**quest DSL（05）**：quest 標頭(minreputation/chanceperhour/maxsimultaneous/tag 門檻) + `definevillager` 角色(空間關係 samehouse/nearbyvillage…) + 有序 step(required/rewardgood/reputation/tag set·clear)；世界任務線靠 `requiredplayertag` ← 前一步 set 的 tag 串接；描述變數 `$name`/`$key_villagename$`/`$key_direction$`/`$key_distance$`。

---

## 5. 26.2 遷移的跨系統重大耦合（重新推導清單）

按影響面排序。逐項在子檔有對映建議。

| # | 1.12.2 耦合點 | 影響子系統 | 26.2 重新推導方向 |
|---|---|---|---|
| **A** | **方塊 metadata（`Block + int meta`）** | 03 建築、04 經濟、06 物品/作物 | **最大風險。** 保留「顏色/good名 → 邏輯方塊名」層不動，**另建「邏輯方塊名 → 26.2 BlockState」對照層**，使既有 PNG/blocklist/goods 完全免改 |
| **B** | worldgen：每區塊 lazy 依鄰近地形選址 | 02 | 與現代 deterministic worldgen 哲學衝突 → 改「玩家附近 post-gen」放置 |
| **C** | `ForgeChunkManager` 強制載入 | 02、06 | `ServerLevel.setChunkForced` / ticket API（保留「離開後續存模擬」語義） |
| **D** | NBT 手動存檔、玩家以**顯示名**識別 | 02、05 | `PersistentState`；玩家識別改 **UUID**（含舊存檔遷移） |
| **E** | `EntityCreature` 基底 + 自帶 A*(atomicstryker) | 01、06 | `PathfinderMob` + 原生 `PathNavigation`／自訂 `NodeEvaluator`；**保留 per-goal 尋路設定抽象 + 不可達瞬移防卡死** |
| **F** | 手寫 ByteBuf、單一 `"millenaire"` channel | 05、06 | Fabric `CustomPayload` API（**保留封包語意**：PACKET_BUILDING 重、PACKET_GUIACTION 萬用分派、內容協商同步） |
| **G** | GL11 手繪 GUI + 反射取私有方法 + 舊 Container/Slot | 05 | 現代 `Screen`/widget／`ScreenHandler`；**保留 `GuiText` 多頁多行模型 + `<color>` tag DSL** |
| **H** | Forge 反射掃描 `Item` 欄位註冊 | 06 | 顯式 `Registry` 註冊 |
| **I** | biome 以字串名比對；`MapGenVillage` 停用原版村莊 | 02、06 | biome tag；現代 worldgen 移除/structure set 處理 |
| **J** | vanilla 方塊當觸發器（金磚開建村 GUI、黑曜石生成村莊、TileEntitySign 招牌） | 05、06 | 自訂方塊/互動 + BlockEntity 重建 |

> **A 是頭號工作項**：一個乾淨的「邏輯方塊名 ↔ BlockState」映射層能讓 building/content/economy 三大子系統的既有資料一次性免改，極大降低遷移風險。建議在 L0/L1 就先立此層。

---

## 6. 重寫指導原則：保留 / 重寫 / 替換 / 丟棄

| 處理 | 對象 |
|---|---|
| **原樣保留（資料）** | 所有 txt/png 內容檔、材質、語言檔、社群翻譯（配合 A 映射層 + 新 parser） |
| **保留語義、重寫機制** | goal 排程器（單一最高優先序湧現）、封包語意、GuiText 模型、貨幣 64 進位、聲望許可制、三層價格、雙層粒度/活躍狀態機、Town Hall 聚合根、good 中介層 |
| **替換為原生** | A* 尋路 → 原生 pathfinding；NBT 手存 → PersistentState；強制載入 → ticket API；反射註冊 → 顯式 registry |
| **丟棄** | 所有 `*/forge/*`、反射 hack、metadata 假設、顯示名識別玩家、GL11 直繪 |

**開放抉擇（已在子檔標記，建議保留自訂方案）**：
- goal 排程器 → 自管「單一最高優先序」 vs 原生 `GoalSelector`（多 goal+flag 互斥）。**建議自管**以維持湧現作息。
- 內容格式 → 沿用原 DSL + 新 parser（hybrid，PLAN.md §6）vs 全轉 datapack。**建議 hybrid**：bulk 沿用、新內容走 datapack；雙語值/重複 key 池使原版 lang.json 不夠用，在地化建議自訂 datapack 格式。

---

## 7. 風險與未決問題彙總

**高風險**
- **授權**：Kinniken/Millenaire repo 無 LICENSE；復用美術/內容/設計、發布前必須釐清（見 PLAN.md §8）。
- **metadata 映射層（耦合 A）**：設計品質決定整個遷移成敗。
- **worldgen 哲學衝突（耦合 B）**：lazy 選址 vs deterministic，需早定方案。

**待後續補讀 / 設計時釐清**（散見各子檔未決問題）
- goal：是否保留自訂排程器；床/睡覺手動掃描是否改原生。
- 世界模擬：active/inactive 切換的繁衍與時間推進精確語義；雙向引用（profile↔building 控制權）一致性。
- 經濟：base-64 貨幣兩記法的完整邊界；外來商人價軌。
- GUI/任務：diplomacy points 產生規則、三層 tag 多人隔離、quest 逾時檢查頻率、PACKET_BUILDING 是否拆包、pujas 附魔目標資料格式。
- 在地化：fallback 鏈與雙語顯示層（language_learning）的呈現規則。

---

## 8. 對開發分層的回饋（對映 PLAN.md L0–L7）

- **L0 骨架**：除 Gradle/Loom/genSources 外，**先立「邏輯方塊名 ↔ BlockState」映射骨架（耦合 A）**與 GuiText 文字框架雛形——它們是後續所有層的地基。
- **L1 內容載入器**：實作四種微 DSL parser + 分層 PNG 解碼 + blocklist + good 中介層；這是最核心、最易低估的一層（03、04 已給出完整欄位字典與編碼規則，可直接照做）。
- **L2–L7**：依 02/01/03/05/06 的意圖逐層重建；每層的「值得保留的微妙設計」清單即驗收重點。

---

*本書與 6 份子分析構成重寫的設計依據。實作每一層前，先讀對應子檔的「值得保留的微妙設計」與「未決問題」兩節。*
