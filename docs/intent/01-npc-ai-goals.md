# 意圖分析書 01：NPC AI 與 Goal 系統（村民行為）

> 來源參考（唯讀）：`mill/org/millenaire/common/goal/**`、`MillVillager.java`、`VillagerType.java`、`VillagerRecord.java`，以及內容資料 `millenaire/cultures/*/villagers/*.txt`、`millenaire/goals/generic*/*.txt`。
>
> 本文捕捉**設計意圖**而非 1.12.2 實作細節。所有 Java 在 26.2 全部重寫；美術與內容資料（txt）盡量沿用。

---

## 1. 用途與玩家可見角色

Millénaire 的村民不是原版那種「站著交易」的被動實體，而是一群**有職業、有作息、會生產、會打仗的自治 agent**。玩家在村莊裡看到的行為，全部由 Goal 系統驅動：

- 農夫白天去田裡種田、收割，把作物搬回家，再交給村莊倉庫。
- 工匠在工坊把原料（麵粉、鐵）變成成品（麵包、工具），成品進入經濟系統供玩家購買 / 村莊建設。
- 伐木工砍樹、補種樹苗；礦工下礦挖礦。
- 守衛 / 騎士白天巡邏、獵殺怪物；村莊被攻擊時集結防守。
- 所有村民晚上回家睡覺；白天有空檔會去聊天、喝酒、祈禱、社交、休息（leisure 行為）。
- 小孩玩耍，長大後變成成年職業村民。
- 外來商人來村莊擺攤、逛旅店；本地商人坐鎮販售。

對玩家而言，Goal 系統製造的是「**這座村莊是活的**」的感覺：村民會說與當前 goal 相關的台詞（`speakSentence`），頭上頂著當前行為的標籤（gameName / labelKey），手上拿著對應的物品（heldItems，例如做箭時拿羽毛），並會因為缺工具而先跑去商店拿工具。

---

## 2. 核心概念與資料模型

### 2.1 Goal（行為單元）

`Goal` 是抽象基底類別。每個具體 goal 是一個**單例**（在 `Goal.initGoals()` 一次性建立並放進全域 `HashMap<String,Goal> goals`，key 為小寫字串如 `"sleep"`、`"choptrees"`）。村民本身**不持有 goal 物件**，只持有一個 `goalKey` 字串 + 一份 `GoalInformation`（目的地），執行時用 key 去 map 查回單例。

> 重要不變量：goal 是**無狀態單例**，所有 per-villager 狀態都存在 villager 上（`goalKey`、`goalInformation`、`goalStarted`、`lastGoalTime`）。重寫時必須維持「goal 邏輯無狀態、村民持狀態」這條界線，否則多村民共用單例會互相污染。

`Goal` 的關鍵可覆寫面（即「一個 goal 要描述的東西」）：

| 方法 | 意圖 |
|---|---|
| `priority(villager)` | 此 goal 此刻對此村民的優先序（**愈高愈優先**）。常加少量隨機抖動避免抖振。 |
| `isPossibleSpecific(villager)` | 此刻能不能做（資源夠嗎、時間對嗎、目的地存在嗎）。 |
| `getDestination(villager)` → `GoalInformation` | 目的地（一個 `Point` 或一個目標 `Entity`，外加所屬建築位置）。 |
| `performAction(villager)` | 抵達後執行一次「動作」的效果（產出物品、拿工具、放置方塊…）。回傳 `true` 表示**此 goal 完成**。 |
| `actionDuration(villager)` | 每次動作的耗時（ms），決定動畫節奏與生產速率。 |
| `isStillValidSpecific(villager)` | 進行中是否仍然有效（例如防守 goal：村莊還在被攻擊嗎）。 |
| `canBeDoneInDayTime` / `canBeDoneAtNight` | 日 / 夜閘門（睡覺只在夜間；多數生產只在白天）。 |
| `range(villager)` | 視為「已抵達目的地」的距離閾值（預設 3，睡覺 2，社交 5）。 |
| `getHeldItemsTravelling` / `getHeldItemsDestination` | 移動中 / 工作中手上顯示的物品。 |
| `swingArms`、`lookAtGoal`、`lookAtPlayer`、`shouldVillagerLieDown`、`stopMovingWhileWorking` | 純表現層（揮手、看向目標、躺下睡覺）。 |
| `nextGoal(villager)` | 完成後可直接指定下一個 goal（鏈式），否則回 null 重新挑選。 |
| `onAccept` / `onComplete` | 進入 / 完成的鉤子。 |
| `getPathingConfig()` | 此 goal 用哪種尋路參數（見第 6 節）。 |
| `isFightingGoal()` | 標記為戰鬥 goal（戰鬥時不會被清掉）。 |

### 2.2 Goal 基底層的通用閘門（`isPossible` final 模板）

`Goal.isPossible()` 是 **final 模板方法**，先跑一批通用條件，再呼叫 `isPossibleSpecific()`。通用條件即「跨所有 goal 的不變量」，**重寫時務必保留**：

1. **日夜閘門**：白天不能做夜間 goal，反之亦然。
2. **townhallLimit**：若村莊倉庫某物品數量已超過上限 → 不做（避免過度生產）。
3. **balanceOutput**：若 output1 的庫存少於 output2 → 不做（用來在兩種產物間做平衡，例如別一直做同一種而冷落另一種）。
4. **maxSimultaneousTotal**：同一 goal 全村同時做的人數上限。
5. **leisure 互斥**（在 `isStillValid` 內）：若村民還有任何**非 leisure** 的 goal 可做，則所有 leisure goal（聊天/喝酒/祈禱/社交/休息）一律無效。意圖：**先工作，閒下來才娛樂**。

`validateDest()` 另有 **buildingLimit**（單一建築物品上限）與 **maxSimultaneousInBuilding**（同建築同 goal 人數上限）兩道閘門，用於把工人分散到不同工坊、避免一窩蜂。

### 2.3 GoalInformation（目的地資料）

`GoalInformation` = `{ dest: Point, destBuildingPos: Point, targetEnt: Entity }`。即「去哪個座標 / 哪個目標實體，屬於哪棟建築」。村民的 `getGoalDestPoint()` / `getGoalDestEntity()` / `getGoalBuildingDest()` 都從這裡讀。

### 2.4 VillagerType（職業定義，資料驅動）

每個職業是一個 `.txt`（如 `guard.txt`、`farmer.txt`），由 `VillagerType.loadVillagerType()` 解析。**這是設計意圖的精華：職業=資料，不是程式碼。** 欄位語義：

| 欄位 | 意圖 |
|---|---|
| `goal=xxx`（可多行） | 此職業擁有的 goal 清單（按 key 從全域 map 取單例）。**順序不影響優先序**，優先序由 `priority()` 動態決定。 |
| `gender` | male / female，影響模型、名字清單、台詞。 |
| `requiredGood=item,n` / `requiredFood=item,n` | 此職業要存在 / 維生需要的物資（村莊經濟與人口維持用；驅動「採集 / 補給」類行為與村莊是否養得起此人）。 |
| `collectGood` | 走路時會自動撿起的地上物品（白天 update 撿落地物）。 |
| `bringBackHomeGood` | 屬於「該搬回家倉庫」的產物（`GoalBringBackResourcesHome` 用）。 |
| `startingInv` | 出生時的初始庫存。 |
| `toolneeded` / `toolneededclass`（meleeweapons / rangedweapons / armour / pickaxes / axes / shovels / hoes） | 此職業需要的工具類別。**只要有 toolsNeeded，系統會自動加上 `gettool` goal**，村民缺工具會先去商店拿。 |
| `defaultweapon` | 出生 / 戰鬥時的預設武器。 |
| `tag=xxx` | 行為旗標（見 2.5）。 |
| `baseAttackStrength`、`health`、`baseheight`、`hiringcost`、`experiencegiven`、`chanceweight` | 數值屬性；`experiencegiven` = 被殺時掉的經驗（敵對單位用）。`chanceweight` = 此職業在隨機生成時的權重。 |
| `texture` / `clothes` / `model`（villager / femalesymmetrical / femaleasymmetrical / zombie） | 外觀。 |
| `familyNameList` / `firstNameList` / `malechild` / `femalechild` | 命名與生育。 |

> 不變量：載入時系統**自動追加** `goal=sleep`（人人會睡），且若有工具需求自動追加 `gettool`。重寫須複製這兩條隱式規則。

### 2.5 Tag（行為旗標）→ 布林屬性

`tag` 在載入時被翻譯成 `VillagerType` 上的布林欄位，直接改變 AI 分支。重要 tag 與意圖：

- `child` → 不工作，只玩耍 + 長大（`goplay` / `becomeadult`）。
- `helpinattacks` → 村莊被攻擊時加入防守（→ `defendvillage`）；預設 health/attack 較高。
- `raider` → 是襲擊者（→ `raidvillage`，攻擊防守者）。
- `hostile` → 主動攻擊靠近的玩家（在非和平難度）。
- `defensive` → 只在自家防守範圍內作戰（`ATTACK_RANGE_DEFENSIVE`=20），不追遠。
- `archer` → 距離 >5 時用弓（`attackEntityBow`）。
- `heavydrinker` / `religious` / `meditates` / `performssacrifices` → 調整 leisure 行為與其優先序（例如喝酒對非信徒優先序較高）。
- `localmerchant` / `foreignmerchant` / `seller` / `visitor` → 商人 / 販售 / 訪客特殊更新邏輯。
- `noteleport`、`hidename`、`showhealth`、`noresurrect`、`noleafclearing`、`gathersapples` → 雜項表現 / 規則旗標。

### 2.6 內容資料目錄

- `millenaire/cultures/<culture>/villagers/*.txt`：各文化的職業定義。
- `millenaire/goals/genericcrafting|genericcooking|genericplanting|genericharvesting|genericslaughteranimal/*.txt`：**資料驅動的 generic goal**（見第 5 節）。每個 txt 就是一個 goal 實例（key=檔名）。

---

## 3. 行為規則與不變量

1. **單一活躍 goal**：村民同時只有一個 `goalKey`。沒有平行 goal、沒有 behaviour tree 的多分支同時跑。
2. **優先序最高者勝**：`setNextGoal()` 遍歷此職業所有 goal，過濾出 `isPossible()` 為真者，選 `priority()` 數值最高者。多數 `priority()` 含 `randomInt` 抖動，避免每 tick 結果跳動造成抽搐。
3. **抵達 → 動作 → 完成 → 重選**：詳見第 4 節生命週期。
4. **完成判定**：`performAction()` 回傳 `true` 即視為該 goal 完成；可透過 `nextGoal()` 鏈到下一個 goal，否則清空 goalKey、下一 tick 重新挑選。
5. **生產上限即停**：townhallLimit / buildingLimit / balanceOutput 任一觸發 → goal 變不可行 → 自動換做別的事。這是村莊不會無限堆積某物資的機制。
6. **leisure 讓位於工作**：只要有正事可做，娛樂 goal 全部無效。
7. **戰鬥搶佔一切**：村莊 `underAttack` 或有 `entityToAttack` 時，會**清掉**當前非戰鬥 goal 強制切到戰鬥 / 躲藏 goal。
8. **卡住處理**：goal 開始後超過 `stuckDelay`（預設 10s）未完成 → 呼叫 `stuckAction()`；尋路長期無進展 → `longDistanceStuck` 計數，達標時嘗試 `unreachableDestination()`（必要時直接把村民瞬移到可達點 = 防卡死保險）。
9. **台詞與表現綁定 goal**：選定 goal 時說 `<key>.chosen`，執行時說 `sentenceKey()`，標籤用 `labelKey()`（移動中用 `labelKeyWhileTravelling`）。

---

## 4. 生命週期與作息流程

### 4.1 每 tick 主迴圈（`onUpdate` → 約 line 2540+）

伺服器端每 tick 對每個村民執行（村莊 inactive 時跳過 = 效能：只跑玩家附近的村莊）：

1. 緩慢回血、detrample 踩壞的作物、預設允許隨機走動。
2. 若無 townhall / house → 走原版邏輯（未編制村民）。
3. 註冊角色（seller / builder 等寫回 townhall 引用）。
4. **戰鬥搶佔**：
   - 村莊 underAttack → 依身分設 `raidvillage` / `defendvillage` / `hide`，立即 `checkGoals()`。
   - 有攻擊目標 → 驗證目標仍有效（距離 / 存活 / 難度），設目的地為目標、手持武器、若當前 goal 非戰鬥則清掉。
   - hostile 村民 → 偵測附近玩家設為攻擊目標。
5. **平時**（無戰鬥）：
   - 白天：說問候語、撿 `collectGood` 落地物、跑商人特殊更新；若 `goalKey==null` 則 `setNextGoal()`，否則 `checkGoals()` 推進當前 goal。
   - 夜晚：重置 `hasPrayedToday` / `hasDrunkToday`（隔日重新可祈禱 / 喝酒），同樣 setNextGoal / checkGoals（夜間多半只有 sleep 可行）。
6. 尋路推進與卡住偵測。

### 4.2 `setNextGoal()`（挑選）

```
clearGoal()
for goal in vtype.goals:
    if goal.isPossible(this): 取 priority 最大者
若選中:
    說 "<key>.chosen"；goalKey = key；onAccept()；記錄 lastGoalTime
```

### 4.3 `checkGoals()`（推進當前 goal，約 line 749）

```
goal = goals.get(goalKey)
若目標實體已死 → 清目標
若還沒算出目的地 → goal.setVillagerDest()（getDestination）
若已抵達 (距離 < range):
    設定 stopMoving / shouldLieDown / 手持物品
    若距上次動作已過 actionDuration:
        if goal.performAction(): 完成 → clearGoal()、goalKey = goal.nextGoal()
若 goal 仍 isStillValid:
    超過 stuckDelay → stuckAction()
    設定隨機走動 / 手持物 / 揮手
否則:
    onComplete()、clearGoal()、goalKey = nextGoal()
```

### 4.4 一天的作息（湧現，而非腳本化）

沒有硬編碼的時間表。作息是**優先序 + 日夜閘門 + leisure 讓位**三者互動湧現的：

- **白天**：生產類 goal 可行且優先序高 → 村民工作。工作之間出現空檔（資源到上限、暫時無原料）時，正事 `isPossible` 變 false，leisure goal 解禁 → 村民去聊天 / 喝酒 / 祈禱 / 社交。喝酒 / 祈禱有「每日一次」旗標（`hasDrunkToday`），且喝酒只在 `worldTime%24000 >= 10000`（午後）才可行。
- **夜晚**：生產類多被 `canBeDoneInDayTime` 擋下，只剩 `sleep`（`canBeDoneAtNight`）可行 → 全村回家睡覺。睡覺時若 `performNightAction` 尚未做則做一次（夜間結算）。
- **小孩**：玩耍 + 到齡 becomeadult 替換成成年職業。

---

## 5. 生產鏈意圖（原料 → goods → 經濟）

這是 Millénaire 的核心循環，由幾類 goal 串成：

### 5.1 採集 / 種植 / 收割（資料驅動 generic goals）

`millenaire/goals/generic*/` 下每個 txt = 一個 goal。`GoalGeneric` 是它們的共同基底，欄位語義：

- `buildingtag`：在哪種建築做（缺省=村民自家）；`townhallgoal=true` 則在村莊大廳；`requiredtag` 進一步篩建築。
- `priority`、`duration`、`maxsimultaneousinbuilding`、`maxsimultaneoustotal`、`buildinglimit`、`townhalllimit`、`itemsbalance`：對應第 2.2 節的通用閘門。
- `helditems`、`sound`、`sentencekey`、`labelkey`：表現層。

子類型：
- **genericcrafting**：`input=item,n` + `output=item,n`。動作 = 消耗建築（含村民身上補進建築的）庫存的 inputs，產出 outputs，存進建築。例：`makearrow` = 1 羽毛 + 1 燧石 → 8 箭，buildinglimit=arrow,64。
- **genericcooking**：類似 crafting，烹飪類。
- **genericplanting** / **genericharvesting**：用 `soilname`（對應建材色票的地形類型）+ `croptype`；harvesting 有 `harvestitem=item,chance%`（多行=多次擲骰，可附帶灌溉加成 `irrigationbonuscrop`）。planting 把作物種回對應 soil。
- **genericslaughteranimal**：宰殺動物取得肉 / 皮。

> 設計意圖：**新增一種「原料→成品」配方完全不需要寫 Java**，只要丟一個 txt。重寫時這套「txt = goal 實例」的資料驅動機制應原樣保留，否則會喪失 Millénaire 內容可擴充的核心價值。硬編碼的 `GoalLumberman*`、`GoalMiner*`、`GoalIndian*` 等是 generic 系統無法表達的特殊行為（砍樹要砍整棵、挖礦要挖到特定礦脈、文化專屬流程）才用 Java 寫死。

### 5.2 搬運與交付（goods 的流動）

- **採集者 → 自家**：`GoalBringBackResourcesHome` — 身上 `bringBackHomeGood` 累積到一定量（>16）或超過延遲時間，就搬回自家倉庫的 sellingPos。優先序隨身上貨量上升（`10 + n*3`），貨愈多愈想回家。
- **自家 → 村莊倉庫 / 商店**：`GoalDeliverGoodsHousehold`、`GoalDeliverResourcesShop`、`GoalGetResourcesForShops`、`GoalGetGoodsForHousehold`、`GoalGatherGoods` — 在家戶、商店、村莊倉庫之間搬運，把分散的產物匯集到能賣 / 能用的地方。
- **建設用料**：`GoalGetResourcesForBuild` + `GoalConstructionStepByStep` — 建造者把建材搬到工地、逐方塊蓋房（與 building 子系統耦合）。
- **販售**：`GoalBeSeller`（坐鎮商店當賣家）、商人相關 goal（擺攤、逛旅店、拜訪建築）。

### 5.3 與其他子系統的關係

- **building**：goal 透過 `villager.getTownHall()` / `getHouse()` / `getBuildingsWithTag(tag)` 找工作地點；每棟建築的 `ResManager` 提供具名位置（craftingPos / sellingPos / sleepingPos / leasurePos / defendingPos）。goal 不直接知道座標，**一律向 building 問「我該站哪」**。
- **economy / shop**：goods 進入建築庫存後成為可交易物資；townhallLimit / buildinglimit 由 goal 讀取，反映「村莊還需不需要這個」。
- **inventory**：`countInv` / `takeFromInv` / `storeGoods` / `takeGoods` 是 goal 與物資系統的介面。

---

## 6. Pathfinding 的使用意圖（26.2 改用原生）

舊碼自帶一套 **A* / JPS（Jump Point Search）** 實作（`pathing.atomicstryker.*`：`AStarPathPlanner`、`AStarNode`、`AStarStatic`、`AStarConfig`），原因是 1.12.2 原版尋路對「跨大距離、跨村莊、多村民」表現不佳。

意圖層面需保留的概念（實作改用原生 `PathNavigation` / `Path`）：

- **每個 goal 可指定尋路參數**（`getPathingConfig()` → `AStarConfig`）。預設 `JPS_CONFIG_TIGHT`，另有 WIDE / BUILDING / CHOPLUMBER / SLAUGHTERSQUIDS 等，差異在「容許的搜尋半徑與寬度」。意圖：不同工作需要不同的尋路寬鬆度（蓋房要精準貼方塊；砍樹 / 趕動物要較大搜尋範圍）。重寫時對應到原生的 node-evaluator / 搜尋範圍上限設定。
- **目的地不可達的保險**：`unreachableDestination()` 會在卡死時把村民直接瞬移到最近可達點（jump destination）。這是**刻意的防卡死設計**，避免村民永久卡在地形裡。原生尋路同樣會有卡住情形，此保險機制建議保留（可用 teleport-to-nearest-reachable）。
- **非同步尋路**：舊碼 `jpsPathPlanner.isBusy()` 表示尋路在背景執行緒計算中，期間不重複請求。原生尋路在主執行緒同步，但仍應節流（不要每 tick 重算），可沿用「目的地未變則不重算」的判斷。
- **「已抵達」用 `range(villager)` 判定**，不是精確到達同一方塊。這個容差很重要（避免村民為了最後半格反覆抽搐）。

> 重新推導重點：把 `AStarConfig` 的語義（搜尋半徑、是否允許開門 / 破方塊、寬度）對映到 26.2 的 `PathNavigation` + 自訂 `NodeEvaluator`。`getPathingConfig()` 這個「per-goal 尋路設定」的抽象應保留為 goal 介面的一部分。

---

## 7. 戰鬥與防禦行為意圖

戰鬥**搶佔**一般作息（見 4.1 step 4），由 tag 決定角色：

- **觸發來源**：
  - 村莊 `underAttack`（由 raid 事件設定）→ 全村依身分轉戰鬥 goal。
  - `hostile` 村民偵測到附近玩家（`ATTACK_RANGE`=80，非和平難度）→ 主動設攻擊目標。
  - 被攻擊（`attackEntityFrom`）→ 反擊。
- **角色分工**（被 `underAttack` 觸發時）：
  - `raider` → `GoalRaidVillage`，目標選防守者（`targetDefender`）。
  - `helpinattacks` → `GoalDefendVillage`，目標選襲擊者（`targetRaider`），集結到 townhall 的 `defendingPos`，村莊不再被攻擊時 goal 失效（解散）。
  - 其餘（平民）→ `GoalHide` 躲藏。
- **攻擊執行**（`attackEntity`）：
  - `archer` 且距離 >5 且有弓 → `attackEntityBow`（生成 `EntityArrow`，依武器有速度 / 傷害加成）。
  - 否則近戰：`attackTime` 冷卻到、距離 <2、Y 軸重疊 → `attackEntityFrom(causeMobDamage, getAttackStrength())` + 揮武器。
- **約束**：
  - `defensive` 村民離自家 `defendingPos` 超過 `ATTACK_RANGE_DEFENSIVE`（20）就放棄目標（不追遠、守土）。
  - 和平難度下不攻擊玩家。
  - 戰鬥 goal 標記 `isFightingGoal()=true`，戰鬥期間不會被誤清。
- **死亡**：給玩家 `experiencegiven` 經驗；`noresurrect` 控制能否復活。

> 意圖：戰鬥是一個**全村協調的狀態切換**（underAttack 旗標 → 全員重新分派 goal），不是個別 AI 各自為政。重寫時應保留「村莊層級的攻擊狀態 → 驅動村民 goal 覆寫」這條由上而下的控制流。

---

## 8. 與其他子系統的互動（摘要）

| 子系統 | 介面 / 耦合點 | 意圖 |
|---|---|---|
| building | `getTownHall/getHouse/getBuildingsWithTag`、`ResManager` 具名位置 | goal 向建築問工作地點與站位 |
| economy / shop | building 庫存、townhallLimit/buildinglimit | 產物入庫成為可交易資源；上限決定是否續產 |
| inventory / goods | `countInv/takeFromInv/storeGoods/takeGoods`、`Goods.goodsName` | 物資進出；txt 用 goods 名稱引用物品 |
| culture | `getCulture().getSentences()`、villager txt 載入 | 台詞、職業定義、命名 |
| villager spawning / VillagerRecord | `chanceweight`、`requiredGood`、生育 | 哪些職業生成、村莊養不養得起 |
| raid / 村莊狀態 | `townHall.underAttack`、`closestPlayer`、`isActive` | 觸發戰鬥；inactive 村莊不跑 AI（效能） |
| network | villager packet、台詞同步 | 把 goalKey / 台詞 / 手持物同步到客戶端顯示 |

---

## 9. 與舊 MC API 耦合、需在 26.2 重新推導的部分

1. **實體基底**：舊碼 `MillVillager extends EntityCreature`（1.12.2）。26.2 改 `PathfinderMob` / 自訂 entity；`onUpdate` → `tick`/`customServerAiStep`，`attackEntityFrom` → `hurt`，`worldObj.isDaytime` → `level.isDay`，難度判斷 API 變更。
2. **尋路**：整套 `pathing.atomicstryker.*`（A*/JPS）→ 原生 `PathNavigation` + 自訂 `NodeEvaluator`；保留 per-goal 尋路設定與「不可達瞬移」保險（見第 6 節）。
3. **方塊 / 物品 API**：`Blocks.bed`、`BlockBed.isBlockHeadOfBed`、meta（damage value）→ 26.2 已無 metadata，改 BlockState / 資料元件。床的偵測、作物 soilname → 需用新的方塊狀態與標籤系統重新對映。`ItemStack(item, count, meta)` → 新建構方式。
4. **物品堆與 NBT**：villager 序列化（`goalKey`、destPoint 等存 NBT）→ 26.2 的 `CompoundTag` / data components。
5. **箭 / 弓**：`EntityArrow`、`ItemMillenaireBow` → 26.2 的 `AbstractArrow` / 自訂遠程武器。
6. **音效 / 動畫**：`playSoundAtEntity`、`swingItem`、`shouldLieDown`（睡覺躺下姿勢用手動 setPosition + 角度）→ 改用 26.2 動畫 / pose 系統。
7. **執行緒模型**：舊碼背景執行緒算尋路。26.2 建議全部主執行緒 + 節流，避免併發問題。
8. **客戶端同步**：自訂 villager packet → 改用 26.2 的 entity data / 自訂 payload。

---

## 10. 值得保留的微妙設計意圖

1. **goal 無狀態單例 + 村民持狀態**：乾淨、省記憶體、易序列化。務必維持此界線。
2. **優先序 + 隨機抖動**：`priority()` 普遍 `+randomInt(n)`，防止同分 goal 每 tick 跳動造成村民抽搐 / 在兩件事間反覆橫跳。
3. **leisure 讓位於工作**：用 `leasure` 旗標 + 「還有正事就禁娛樂」的規則，免去手寫時間表卻得到自然作息。
4. **生產上限三閘門**（townhallLimit / buildingLimit / balanceOutput）：村莊自我調節產量、平衡多種產物、把工人分散到不同工坊，全靠這幾個閘門湧現，而非中央排程器。
5. **缺工具自動補課**：有 `toolsNeeded` 就自動掛 `gettool`（priority=100 很高），村民會先拿工具再工作；`hasBetterTool` 避免拿了爛工具又去拿（已有更好的就不換）。
6. **「向建築問站位」而非寫死座標**：goal 完全不知道村莊長相，靠 `ResManager` 的具名位置，使同一 goal 能套用到任何 building 變體 / 任何村莊佈局。
7. **每日旗標**（hasDrunkToday / hasPrayedToday / nightActionPerformed）在夜 / 日邊界重置：低成本地實現「每日節律」。
8. **不可達瞬移保險**：寧可瞬移也不讓村民永久卡死，是維持「村莊一直在動」體感的關鍵兜底。
9. **戰鬥由村莊狀態由上而下驅動**：underAttack 旗標一翻，全村協調換 goal，比個別 AI 偵測敵人更可靠、表現更像「有組織的防衛」。
10. **資料驅動 generic goal**：新內容（配方、作物）= 一個 txt，零 Java。是 Millénaire 可被社群擴充的命脈。

---

## 11. 未決問題

1. **tick 預算與規模**：舊碼用「inactive 村莊不跑 + 背景尋路」控制成本。26.2 改原生同步尋路後，大村莊 / 多村莊的 per-tick 成本如何控制？是否需要把 goal 重選與尋路分攤到不同 tick（time-slicing）？
2. **原生尋路能否表達所有 AStarConfig 語義**：寬度、搜尋半徑、是否開門 / 破方塊、特殊（趕動物 / 砍整棵樹）這些參數在原生 NodeEvaluator 能否一一對映？哪些得自訂？
3. **方塊 meta → BlockState 的內容遷移**：villager txt / goal txt 大量用 `goodsName` 字串引用「item+meta」。26.2 無 meta，需要一層 goods 名稱 → BlockState/Item 的映射表，且要相容舊內容檔。
4. **床 / 睡覺**：舊碼手動掃描床方塊 + 手動 setPosition 躺下。是否改用 26.2 原生 `SleepGoal` / bed 佔用機制，還是維持自訂掃描（為了多人同屋分床的邏輯）？
5. **Goal 介面要不要原生化**：是把這套 Goal 系統整個保留為獨立排程器，還是改寫成原生 `Goal`/`GoalSelector`（原生用的是「同時多 goal + flags 互斥」模型，與本系統「單一最高優先序」模型不同）？建議**保留自訂排程器**以維持上述湧現行為，但需評估與原生 brain/GOAP 的取捨。
6. **leisure 與工作的邊界**：「還有正事就禁娛樂」在資源充足村莊可能讓村民很少娛樂；是否要引入「工作疲勞 / 配額」讓作息更生動？（屬設計可調，先照舊復刻。）
7. **多執行緒安全**：若未來要平行化 AI，goal 無狀態的設計有利，但 building/inventory 的讀寫需要併發保護策略。
