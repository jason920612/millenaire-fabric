# 子系統意圖分析書 02：核心領域模型與世界模擬骨幹

> 範圍：village 作為被模擬實體的生成、追蹤、tick、持久化與消長；世界層級資料結構與不變量。
> 不含：goal/AI 細節（代理 03）、building schematic/方塊放置細節（代理 04）。本文涉及 Building 時只談「village 容器」面向，不談蓋房子的演算法。
>
> 來源（唯讀參考，1.12.2 Forge 實作）：
> `mill/org/millenaire/common/` 之 `MillWorld`、`MillWorldInfo`、`Point`、`MillMapInfo`、`VillageType`、`WorldGenVillage`、`UserProfile`、`building/Building`（村莊容器面）、`core/MillCommonUtilities`、`forge/ServerTickHandler`、`forge/MillEventController`。

---

## 1. 用途與玩家可見角色

Millénaire 在世界中**程序化生成自治的 NPC 村莊**（與原版村莊無關）。每個村莊：

- 屬於某個**文化（Culture）**（諾曼、印度、日本、瑪雅…），決定建築風格、村民類型、商品與語言。
- 有一個**中心建築（Town Hall，市政廳/村中心）**作為村莊的「實體錨點」與管理者。
- 會**隨時間自主成長**：村民決定下一棟要蓋的建築，逐步擴張；人口繁衍；商品生產與貿易。
- 與**鄰近村莊有外交關係**（友好/敵對），可能發動或承受**突襲（raid）**。
- 與**玩家有聲望（reputation）關係**；玩家可解鎖建築、購買升級、最終取得**玩家控制村莊（player-controlled village）**。

玩家可見行為（本子系統負責的部分）：

- 進入村莊半徑時收到「發現新村莊」訊息（含名稱、文化、方位、距離）。
- `/village` 類指令列出已知村莊與其載入狀態（active / frozen / inactive）。
- 村莊有名字與限定詞（qualifier，如「山上的」「沙漠的」），由地形決定。
- 地圖（map）上顯示村莊建築佈局。
- 村莊在玩家離開後「凍結」、回來後「解凍」並繼續成長——但**世界時間照常前進**，回來時看到成長結果。

**核心意圖**：村莊是一個**長壽、持久、半自治的模擬實體**，其活躍度與玩家鄰近度綁定（效能考量），但其存在與身分是永久的（不會自然消亡）。

---

## 2. 核心概念與資料模型

### 2.1 全域管理者：`MillWorld`

每個維度世界對應一個 `MillWorld` 實例（伺服端在 `Mill.serverWorlds` 串列；客戶端單一 `Mill.clientWorld`）。職責：

- **建築註冊表**：`HashMap<Point, Building> buildings`——以中心座標為鍵，持有所有已載入的 Building 實體（含村莊內子建築與獨立建築）。
- **村莊位置清單**：`VillageList villagesList` 與 `loneBuildingsList`（獨立建築）。這是**輕量索引**（座標、名稱、type、culture、generatedFor），即使村莊未載入也常駐記憶體，用於選址距離檢查、村莊列表 UI、地圖。
- **村民全域索引**：`HashMap<Long, MillVillager> villagers`（以 villager_id 為鍵）。
- **玩家檔案**：`HashMap<String, UserProfile> profiles`。
- **全域標籤**：`List<String> globalTags`——世界級的劇情/進度旗標（影響哪些 village type 可生成）。
- **存檔目錄**：`millenaireDir`（`<saveDir>/millenaire/`）。

`MillWorld` 是**世界層 single source of truth 的擁有者**。它區分兩種資料粒度：
1. **常駐輕量索引**（VillageList、profiles、tags）——永遠在記憶體，永遠可查。
2. **重量級實體**（Building + 村民 + 建築進度）——按需載入/凍結。

### 2.2 村莊實體：`Building`（村莊容器面向）

`Building` 一身二用：既是「單一建築」也是「村莊」。當 `isTownhall == true` 時，這個 Building 就是**整個村莊的代表與管理者**：

- `buildings: List<Point>`——本村所有子建築的中心座標。
- `villagers: List<MillVillager>` 與 `vrecords: List<VillagerRecord>`——當前村民實體與**村民檔案記錄**（記錄即使村民實體未載入也存在，是村民身分/家族/職業的權威來源）。
- `buildingProjects: Map<EnumProjects, List<BuildingProject>>`——可興建/可升級的建築計畫，分階層（CENTRE/START/PLAYER/CORE/SECONDARY/EXTRA/CUSTOMBUILDINGS）。
- `buildingGoal*`——當前正在蓋的目標（key/level/variation/location）。
- `relations: HashMap<Point, Integer>`——與其他村莊的外交關係值（-100..100）。
- `controlledBy / controlledByName`——若為玩家控制村莊，指向 UserProfile key。
- `winfo: MillWorldInfo`——本村對周遭地形的認知（見 2.4）。
- raid 狀態：`raidTarget`、`raidsPerformed`、`raidsSuffered`、`underAttack` 等。
- `villageType: VillageType`、`culture: Culture`。

非市政廳的子建築也是 `Building`，但透過 `townHallPos` 指回市政廳；它們的 tick 只在市政廳 active 時才執行。

**意圖**：村莊狀態集中由市政廳 Building 擁有並持久化（見第 3、4 節「資料擁有權」）。

### 2.3 村莊藍圖：`VillageType`

從 `millenaire/cultures/<culture>/villages/*.txt` 與 `lonebuildings/*.txt` 載入的**靜態定義**（非每村莊一份，而是「村莊種類」）。關鍵欄位：

- `centreBuilding` / `customCentre`——中心建築計畫集。
- `startBuildings`（生成時必備）、`coreBuildings`、`secondaryBuildings`、`extraBuildings`、`playerBuildings`、`customBuildings`——成長時可選的建築池，分優先層。
- `biomes: List<String>`——允許生成的生物群系（白名單）。
- `weight`、`max`——加權隨機選擇權重；全世界該 type 數量上限。
- `radius`——村莊佔地半徑（預設 `MLN.VillageRadius`）。
- `minDistanceFromSpawn`、`requiredTags`/`forbiddenTags`（全域標籤門檻）。
- `playerControlled`、`spawnable`、`generateOnServer`、`carriesRaid`、`keyLonebuilding`、`generatedForPlayer`。
- `hamlets: List<String>`——主村生成後在外圍嘗試生成的附屬小村。
- 地形限定詞：`hillQualifier`/`mountainQualifier`/`desert`/`forest`/`lava`/`lake`/`oceanQualifier`。

**意圖**：village type 是「物種定義」；每個生成的村莊是該定義的一個實例。生成規則（biome、距離、上限、weight、tag 門檻）全部由 `VillageType.isValidForGeneration()` + `getChoiceWeight()` 表達。

### 2.4 地形認知：`MillWorldInfo`

伺服端物件，是「村莊對周遭土地的認知快照」。以村中心為原點，覆蓋 `radius + margin` 的方形區域，存多張 2D 陣列：`topGround`、`constructionHeight`、`spaceAbove`、`canBuild`、`buildingForbidden`、`water`、`tree`、`danger`、`buildingLoc`、`path` 等。座標以 `mapStartX/mapStartZ`（區域左下角方塊座標）為基準偏移。

用途：
1. **選址時**（生成）——判斷一塊地是否「合適」（`isAppropriateArea`：可建方塊比例 > 70%）、找建築落點。
2. **運行時**——尋路、村民工作地點計算、地圖顯示來源。
- `UPDATE_FREQUENCY = 1000`：定期刷新（地形可能被玩家改變）。

**注意**：`MillWorldInfo` 是**衍生快取**，不持久化（從世界方塊重算）。但 `mapStartX/Z`、`width`、`length` 定義了「村莊區域邊界」這個概念模型，被 `isVillageChunksLoaded()`、chunk loader、pathing 共用。

### 2.5 座標模型：`Point`

自訂 3D 座標類（double x/y/z），是整個 mod 的座標通貨。提供：整數存取（`getiX/Y/Z`）、距離（`distanceTo`、`distanceToSquared`、`horizontalDistanceTo`）、方位字串（`directionTo`）、chunk 換算（`getChunkX/Z`）、NBT 讀寫（`write`）、檔名字串（`getPathString`，用於建築存檔檔名）、`sameBlock`/`equals`（以整數方塊比較）。

**意圖**：`Point` 是 hashmap 鍵與序列化單位。`equals/hashCode` 必須以方塊整數座標為準（村莊以中心方塊座標唯一識別）。

### 2.6 地圖快照：`MillMapInfo`

客戶端專用，從 `MillWorldInfo` 抽取的精簡地圖資料（供 UI 繪製村莊佈局）。

### 2.7 玩家關係：`UserProfile`

每玩家一份（以玩家名/key 為鍵），存於 `profiles/<key>/`。持有：

- `villageReputations: HashMap<Point, Integer>`——對各村莊的聲望（以村中心 Point 為鍵）。
- `cultureReputations: HashMap<String, Integer>`——對各文化的整體聲望。
- `villageDiplomacy`、`cultureLanguages`（語言熟練度）。
- `questInstances`、`villagersInQuests`、`actionData`、個人 tags、`panelsSent`/`buildingsSent`（網路同步去重時間戳）。

**意圖**：玩家↔村莊關係（聲望）的擁有權在 **UserProfile（玩家側）**，不在 village 側。村莊控制權（`controlledBy`）則存在 Building 側並指回 profile key——雙向引用，需在重做時注意一致性。

---

## 3. 行為規則與不變量

### 3.1 選址規則（village 生成）

`WorldGenVillage.generateVillageAtPoint` 是入口（掛在 Forge `IWorldGenerator`，每個新區塊生成時以該區塊座標為候選點觸發，僅維度 0 主世界）。規則：

1. **只在伺服端、僅主世界（dimensionId == 0）**生成。
2. **區塊載入檢查**：候選點周圍 ±5 區塊需已生成；否則嘗試鄰近已生成區塊。
3. **離出生點保護**：`< MLN.spawnProtectionRadius` 不生成。
4. **村莊間最小距離**：
   - 村↔村：`MLN.minDistanceBetweenVillages`
   - 村↔獨立建築：`MLN.minDistanceBetweenVillagesAndLoneBuildings`
   - 獨立建築↔獨立建築：`MLN.minDistanceBetweenLoneBuildings`
   - 距離檢查對所有已知 VillageList 座標做（即使該村未載入）。
5. **文化/類型分配**：對所有 Culture 的所有 VillageType，篩選 `isValidForGeneration()`（biome 白名單、全域 tag 門檻、未達 max 上限、離 spawn 距離），再以 `weight` 做加權隨機（`getWeightedChoice`，會把最近玩家納入考量，例如 key lone building 權重拉到 10000）。
6. **地形合適度**：建立 `MillWorldInfo`，要求可建方塊比例 > 70%（`MINIMUM_USABLE_BLOCK_PERC`）。
7. **能放下關鍵建築**：中心建築 + 所有 `startBuildings` 都必須找到合法落點，否則放棄（連通性以 A* `createConnectionsTable` 驗證）。
8. **二次距離複查**（找到落點後座標可能微調，重查距離）。
9. 若 village 不成，**降級嘗試獨立建築（lone building）**——更小的距離門檻、自己的 type 池。
10. 成功後可生成 **hamlets（附屬小村）**，在主村外圍 130~200 半徑環狀嘗試。

不變量：
- 同一 Point 不會註冊兩次（`registerVillageLocation` 先查 `found`）。
- 生成迴圈防遞迴（檢查 stack trace 避免 worldgen 內再觸發 worldgen）。
- player-controlled 村莊生成時會放一張 `parchmentVillageScroll`（村莊卷軸）作為控制憑證。

### 3.2 成長規則（無自然消亡）

- 村莊成長 = 不斷選下一個 `buildingProject` 來蓋（`findBuildingProject`）：先窮盡「新建築」層級，再開放「升級」層級；以 weight 隨機選 project。
- `noProjectsLeft` 為真時停止成長（藍圖池耗盡）。
- 非 player-controlled 村莊蓋付費/禮物建築前需玩家先「購買」（`buildingsBought` 含該 key）。
- **村莊不會自然衰亡/消失**：沒有人口歸零即刪村的邏輯；村民會繁衍與替補（`addAdult`、`repairVillagerList`）。村莊只會因玩家主動拆除或被某些劇情移除（`removeVillageOrLoneBuilding`）。
- 人口：`vrecords`（村民記錄）是人口的權威；`initialiseVillage` 依「是否還有成年男性」決定是否上鎖箱子（noMenLeft → 解鎖，象徵村莊荒廢可被掠奪）。

### 3.3 活躍狀態機（active / frozen / inactive）

每個市政廳 Building 在 tick 中（`updateBuildingServer`）維護三態：

- **active**：村莊區塊全部載入（`isVillageChunksLoaded()`）→ 完整模擬（村民 AI、建築、貿易、raid）。
- **frozen / inactive**：玩家離開、區塊卸載 → 停止模擬。
- 狀態轉換規則：
  - 玩家在 `MLN.KeepActiveRadius`（預設 200）內 → `loadChunks()`（透過 `BuildingChunkLoader` 強制載入村莊區塊）。
  - 玩家離 `KeepActiveRadius + 32` 外 → `unloadChunks()`。
  - `isAreaLoaded = isVillageChunksLoaded()`；active↔inactive 切換時發建築封包給所有玩家、並在轉 inactive 時存檔（`saveTownHall("becoming inactive")`）。

不變量：**子建築只在其市政廳 active 時 tick**（`if (getTownHall() == null || !getTownHall().isActive) return;`）。

### 3.4 外交關係不變量

- 關係值域 `[-100, 100]`，命名門檻常數見 `Building.RELATION_*`。
- 初始化（`initialiseRelations`）：對 `BackgroundRadius`（預設 2000）內已存在村莊建立初始關係——同 hamlet/parent → MAX；同玩家控制 → MAX；同文化 → GOOD；異文化 → BAD。
- 夜間（`thNightActionPerformed` 一晚一次）：以機率調整關係（依現有關係決定改善/惡化機率），跨過門檻時對範圍內玩家廣播。
- player-controlled 與 lone building **不參與**自主外交演化。

### 3.5 聲望不變量

- 聲望存在 UserProfile，分**村莊級**（villageReputations）與**文化級**（cultureReputations），`getReputation(building)` = 村莊聲望 + 該文化聲望。
- 村莊夜間對附近每位玩家給外交點數（`adjustDiplomacyPoint`），這是「玩家可逐步累積信任」的機制。

---

## 4. 生命週期與 tick 流程

### 4.1 世界生命週期（`MillEventController`）

- **World Load**：建立 `MillWorld`。伺服世界加入 `Mill.serverWorlds` 並呼叫 `loadData()`（**載入順序固定**，見 4.4）。客戶世界建立 `Mill.clientWorld`。僅主世界（vanillaDimension == 0）參與存讀檔。
- **World Save**：`mw.saveEverything()`（存 tags、村莊清單、設定、所有 active 市政廳）。
- **World Unload**：清理。

### 4.2 Server tick（`ServerTickHandler.tickStart`）

每個 server tick：`for (mw : Mill.serverWorlds) mw.updateWorldServer();`

`MillWorld.updateWorldServer()` 每 tick 做：
1. 對**所有已載入 Building** 呼叫 `updateBuildingServer()`（活躍狀態機 + 若 active 則完整村莊更新）與 `updateBackgroundVillage()`（raid 規劃，僅需在 BackgroundRadius 內）。
2. `checkConnections()`——玩家連線狀態維護。
3. 對所有 `connected` 的 UserProfile 呼叫 `updateProfile()`。
4. 對每位玩家跑 `SpecialQuestActions.onTick`。
5. 處理改名佇列、`forcePreload`（可選的區塊預載）。

**分攤策略（重點）**：本身**沒有把村莊輪流分散到不同 tick**的調度器；而是用**多層門檻**控制每 tick 真正的工作量：
- 大多數村莊處於 inactive（玩家不在附近），`updateBuildingServer` 早早 return。
- active 村莊內部再以 `worldObj.getWorldTime() % N == 0` 把不同工作分散到不同 tick（戰鬥狀態 %10、成就 %200、外交/雜務 %1000、地圖刷新 1000、簽名/路徑有各自延遲時間戳如 `lastVillagerRecordsRepair`、`PATHING_REBUILD_DELAY`、`lastSaved > 1000` 才存檔）。
- raid 與夜間動作用「一晚一次」布林旗標（`thNightActionPerformed`、`nightBackgroundActionPerformed`）避免重複。

**效能模型意圖**：活躍村莊數受 `KeepActiveRadius` 物理限制（玩家附近才活）；單村內工作以時間取模 + 時間戳節流分攤；存檔在背景執行緒（`SaveWorker` extends Thread）避免阻塞 tick。

### 4.3 active 村莊單 tick 工作（`updateBuildingServer` → `updateTownHall`）

- `updateWorldInfo()`（有村民時刷新地形認知）
- `completeConstruction()` / `findBuildingProject()` / `findBuildingConstruction()`——成長推進。
- `checkSeller()` / `checkWorkers()`——崗位指派。
- `checkBattleStatus()`（%10）、`killMobs()`、夜間外交、人口記錄修復（`repairVillagerList`，每 100 tick）、成就（%200）、路徑自動建造。
- 子建築依 tag 跑各自更新（grove/kiln/pens/inn/market/signs…屬代理 04 的方塊面，但**觸發節流**屬本子系統）。

### 4.4 持久化與載入順序（`MillWorld.loadData`）

**固定載入順序**（有依賴關係）：
1. `loadWorldConfig()`——`config.txt`（generateVillages 開關、改名指令）。
2. `loadVillageList()`——`villages.txt` + `lonebuildings.txt`（純文字，每行 `name;x/y/z;type;culture;generatedFor`）。**必須先於 buildings**，因為 building/relations 解析依賴村莊清單存在。
3. `loadGlobalTags()`——`tags.txt`。
4. `loadBuildings()`——掃 `buildings/*.gz`（壓縮 NBT），每檔含一個市政廳及其所有子建築（`new Building(mw, nbt)` 自行 `mw.addBuilding`）。
5. `loadProfiles()`——`profiles/<key>/`。

**存檔粒度與擁有權**：
- 村莊/獨立建築輕量索引 → `villages.txt` / `lonebuildings.txt`（純文字）。
- 每個**市政廳**負責序列化**整個村莊**：`SaveWorker` 把 `buildings` 串列中每個子 Building 的 `writeToNBT` 收進一個 NBT list，寫成 `<posPathString>.gz`（先寫 `_temp.gz` 再 rename，原子寫入）。
- 重型衍生資料分檔旁存：`_bblocks.bin`（建築方塊快取）、`_paths.bin`、`_pathstoclear.bin`。
- `writeToNBT` 內含的村莊狀態：pos、location、name/qualifier、culture、villageType、controlledBy、townHallPos、buildings 清單、buildingProjects 的 location、buildingGoal*、**vrecords（村民記錄）**、visitorsList、buildingsBought、raid 狀態、relations、imported/exported、pujas、resManager 資源。
- 聲望/外交（玩家側）→ 各 UserProfile 自存（`saveProfileConfig` 等）。
- 全域 tags → `tags.txt`。

**必須存檔的狀態清單**（重做時不可遺漏）：village 身分（pos/culture/type/name）、子建築清單與各自 location、建築進度（projects + current goal + bblocks）、**村民記錄與家族關係**（vrecords，含家族姓氏/職業/性別/婚姻）、村莊間 relations、玩家對村莊/文化的 reputation、raid 歷史、控制權（controlledBy）、全域與玩家 tags、貿易帳（imported/exported/shop buys/sells）。

存檔時機：active 市政廳在 `saveNeeded` 或距上次存檔 > 1000 tick 時存；轉 inactive 時存；world save 時存所有 active 市政廳。

---

## 5. 與其他子系統的互動

- **goal / 村民 AI（代理 03）**：本子系統提供村民的「家」「崗位座標」「村莊 active 與否」「pathing 區域」；村民實體（`MillVillager`）的生命由村莊容器管理（`addAdult`、`repairVillagerList`、`villagers` 清單），但其行為決策不在此。`vrecords` 是兩個子系統的交界資料。
- **building schematic / 方塊放置（代理 04）**：本子系統決定「何時、蓋哪個 project、在哪個 location」（`findBuildingProject`、`buildingGoal`）；實際逐方塊建造、`BuildingPlan`/`BuildingBlock`/`resManager` 屬代理 04。`MillWorldInfo` 的地形認知是雙方共用基礎。
- **網路同步**：`MillWorld.sendVillageListPacket`、`Building.sendBuildingPacket`、`UserProfile.sendProfilePacket`——把伺服端權威狀態推給客戶端（客戶端村莊唯讀，用於 UI/地圖）。客戶端 `MillWorld` 與 `MillMapInfo` 是純展示鏡像。
- **quest / 劇情**：透過 globalTags 與 UserProfile tags 影響哪些 village type 可生成、key lone building 觸發。

---

## 6. 與舊 MC API 耦合、需在 26.2 重新推導的部分

1. **世界生成掛點**：舊碼用 Forge `IWorldGenerator.generate(每區塊)`。26.2 需改用 Fabric 的 worldgen（`StructureFeature`/`StructurePlacement` 或自訂的 chunk-generation 事件/`ServerChunkEvents`）。「每新區塊嘗試選址 + 距離/biome/tag 過濾 + 加權隨機」的**意圖**需重建，但實作機制完全不同（且 26.2 結構生成在 chunk 階段、限制更嚴）。
2. **Tick 驅動**：`TickEvent.ServerTickEvent` → Fabric `ServerTickEvents.END_SERVER_TICK` 或 per-world tick。注意：26.2 應考慮把村莊輪詢分散，而非每 tick 掃全表（舊碼已靠狀態機自然分攤，但現代版可更明確分片）。
3. **區塊強制載入**：`BuildingChunkLoader`/`world.getChunkProvider().loadChunk` → 26.2 的 `ForcedChunk`/`ChunkTicketManager`（`ServerWorld.setChunkForced` 或 ticket API）。「玩家在 KeepActiveRadius 內 → 強制載入村莊區塊」的意圖保留，API 全換。
4. **World save 目錄與 NBT 工具**：`CompressedStreamTools`、`getWorldSaveDir`、自訂 `.gz`/`.txt`/`.bin` 旁檔 → 26.2 建議改用 `PersistentState`（SavedData）以世界存檔為依歸，或維持自訂檔但用新 NBT API（`NbtIo`）。原子寫入（temp + rename）意圖保留。
5. **生物群系名稱比對**：舊碼用 `biomeName.toLowerCase()` 字串白名單。26.2 應改用 biome `RegistryKey`/`TagKey`（biome tag），不要靠英文名字串。
6. **`Point` vs `BlockPos`**：舊 `Point` 是 double 座標自訂類。重做建議以 `BlockPos`（村莊鍵）+ 必要時 `Vec3d`（村民位置）取代，但保留「以中心方塊座標唯一識別村莊、可序列化、可當 map key」的語意。
7. **客戶端/伺服端 world 區分**：`world.isRemote`、`WorldServer`/`WorldServerMulti` 判斷 → 26.2 的 `World.isClient` / `ServerWorld`。多維度處理（目前硬限主世界 dim 0）需用 `World.getRegistryKey()` 重判。
8. **存檔執行緒**：`SaveWorker extends Thread` 直接開緒寫檔。26.2 應審視執行緒安全（off-thread 存 NBT），可能改用伺服器 IO 執行緒池或主執行緒快照 + 背景序列化。
9. **EntityPlayer / 文字組件**：`getDisplayName()`、`EnumChatFormatting`、`sendTranslatedSentence` → 新的 `Text`/`Style`/profile（UUID 而非顯示名！見第 7 節）。

---

## 7. 值得保留的微妙設計意圖

1. **雙層資料粒度**：輕量常駐索引（VillageList，純文字、永不卸載）與重量級實體（Building NBT，按需載入）分離。這讓「即使村莊未載入也能做距離/列表/地圖查詢」與「節省記憶體」並存。**強烈建議保留此分層**。
2. **村莊狀態以市政廳為單一擁有者並整批存檔**：一個 `.gz` = 一村全部子建築。避免子建築各存各的造成一致性裂縫。
3. **活躍狀態與玩家鄰近度綁定 + 強制載入區塊**：村莊「跟著玩家活」，但世界時間照走、回來看到成長結果——這是 Millénaire 的核心體感。`KeepActiveRadius`(200) 決定模擬範圍，`BackgroundRadius`(2000) 決定「能感知 raid/收到訊息」範圍。兩個半徑的雙層概念要保留。
4. **生成的二階段檢查**（先 canAttempt 距離，再實際找落點後二次複查距離）：因為找落點會微調座標，需重查，避免並發生成過近。
5. **加權隨機 + max 上限 + biome 白名單 + tag 門檻**四件套構成可資料驅動的選址規則——全部從 txt 設定來，不寫死。重做時應同樣**資料驅動**。
6. **降級生成**：village 放不下 → 試 lone building；保證世界不至於空曠。
7. **原子存檔**（temp + rename）與**背景存檔**避免 tick 阻塞與半寫檔損毀。
8. **noMenLeft → 解鎖箱子**：村莊「人口耗盡」的可見後果（可被掠奪）而非直接刪村——微妙但有玩法意義。
9. **時間取模 + 時間戳節流**的內建分攤——即使沒有正式排程器，也避免每 tick 做重活。

---

## 8. 未決問題

1. **玩家識別鍵**：舊碼大量以 `getDisplayName()`（顯示名字串）作 UserProfile key 與 generatedFor。26.2 應改用 **UUID**，但要設計舊存檔遷移（舊碼甚至有 `OLD_PROFILE_SINGLE_PLAYER` 遷移分支可參考其意圖）。
2. **多維度**：舊碼硬限主世界 dim 0。是否在 26.2 支援其他維度生成村莊？若否，用哪個 dimension key 判定。
3. **worldgen 時機衝突**：26.2 結構生成在 chunk 生成早期、且強調 deterministic、不可讀取鄰近未生成區塊。舊碼「檢查 ±5 區塊已載入、找鄰近已載區塊」的 lazy 作法與現代 worldgen 哲學衝突——需決定改為延後生成（chunk 載入後的「post-gen」步驟，類似一個 ticking task）還是真結構。建議走「玩家附近後生成」而非純 worldgen，以保留依賴鄰近地形的選址邏輯。
4. **存檔格式**：沿用自訂 `.gz`/`.txt`/`.bin` 還是全面改 `SavedData`？影響遷移與多人存檔可攜性。
5. **存檔執行緒安全**：`SaveWorker` 在背景緒讀 Building 可變狀態（tick 同時可能改），舊碼是否有資料競爭？26.2 重做需明確快照策略。
6. **村莊上限/世界規模**：沒有全域村莊總數上限（只有 per-type max）。大世界長期遊玩村莊數會否拖垮輕量索引的線性距離掃描（`getClosestVillage` 等是 O(n)）？是否需空間索引（grid/quadtree）。
7. **relations 的雙向一致性**：A 對 B 的關係與 B 對 A 的關係是否保證對稱、由誰維護？重做需釐清。
8. **村莊消亡**：設計上村莊不死。是否要在 26.2 加入廢棄/重生機制？目前意圖是「永久」，需確認是否保留。
9. **MillWorldInfo 不持久化、靠重算**：重算依賴區塊已載入。村莊剛解凍時 winfo 為空，`isVillageChunksLoaded` 用 winfo 邊界判斷——存在「先有蛋還是先有雞」的初始化順序，重做需確認邊界資訊（mapStartX/width/length）是否該持久化以打破循環。

---

### 關鍵檔案索引（唯讀參考）

| 概念 | 檔案 |
|---|---|
| 全域管理者 / 註冊 / 存讀檔 | `common/MillWorld.java` |
| 選址 / 生成流程 | `common/WorldGenVillage.java` |
| 村莊容器 / tick / NBT / 活躍狀態機 | `common/building/Building.java`（村莊面） |
| 村莊種類定義 / 生成規則 | `common/VillageType.java` |
| 地形認知 / 區域邊界 | `common/MillWorldInfo.java` |
| 座標模型 | `common/Point.java` |
| 玩家聲望 / 控制 / quest 進度 | `common/UserProfile.java` |
| 輕量村莊索引結構 | `common/core/MillCommonUtilities.java`（`VillageList`） |
| tick 驅動 | `common/forge/ServerTickHandler.java` |
| 世界 load/save/unload 事件 | `common/forge/MillEventController.java` |
| 客戶端地圖快照 | `common/MillMapInfo.java` |
