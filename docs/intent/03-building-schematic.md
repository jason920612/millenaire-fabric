# 03 - 建築與 Schematic 系統 意圖分析書

> 子系統範圍：`common/building/**`（Building、BuildingPlan、BuildingPlanSet、BuildingProject、BuildingLocation、BuildingBlock、BuildingResManager、BuildingCustomPlan、PointType…）+ 內容資料 `millenaire/cultures/<culture>/buildings/**`、`blocklist.txt`、`Colour Sheet.png`。
>
> 本文目的：捕捉「意圖、規則、資料格式契約」，讓 Fabric / MC 26.2 的工程師**不讀舊碼**即可重寫 parser 與建造邏輯，並能**直接復用既有的 `.txt` 計畫檔與分層 PNG schematic**。
>
> 慣例：行文中文，識別字／欄位名／檔名保留英文。所有與舊 1.12.2 MC API 耦合的部分另闢章節標註「需重新推導」。

---

## 1. 用途與玩家可見角色

Millénaire 的村莊是由一系列「建築」自動生長出來的。每棟建築對應：

- 一份**建築計畫（BuildingPlan）**：描述尺寸、定位規則、住戶、商店類型、升級階梯，以及逐 Y 層的方塊佈局（分層 PNG）。
- 玩家可見效果：村民會**一塊一塊地實際建造**這棟建築（不是瞬間 paste），施工進度顯示為百分比（`ui.construction`）。建築完工後住戶遷入、開店、種田、生產貨物。
- 建築會**隨村莊發展升級**（`_A0` → `_A1` → `_A2`…），外觀逐步加高/擴張。
- 村莊中心（town hall，例如 norman 的 `manor`）的告示牌上玩家可手動指定「下一個要蓋的建築」。
- 另有獨立於村莊的 **lone buildings**（廢墟、盜賊塔、地城等），以及玩家用「建築魔杖」放置的 **custom buildings**。

整個系統的設計意圖是：**內容（佈局）與引擎（建造邏輯）徹底分離**。佈局完全由美術用 PNG「畫」出來，再加一行純文字 config 描述語意。這正是我們要復用的資產。

---

## 2. 建築計畫 DSL 欄位字典

### 2.1 檔案組織與命名契約（最重要）

一個「BuildingPlanSet」由同目錄下一組檔案構成，key = 建築名（例 `armoury`）：

```
armoury_A.txt        ← variation A 的 config（純文字，多行）
armoury_A0.png       ← variation A, upgrade level 0 的分層 schematic
armoury_A1.png       ← variation A, upgrade level 1（升級階）
armoury_A2.png       ← ...
farm_B.txt           ← variation B（同一建築的另一種佈局，隨機選用）
farm_B0.png …
```

規則（來自 `BuildingPlanSet.loadPictPlans`）：

1. **Variation** 用字尾字母 `A`,`B`,`C`… 表示（`varChar`，從 `'A'` 起 `variation` index 0,1,2…）。載入器 `while (key_<var>.txt exists)` 逐一掃描。同一 key 的多個 variation 是**等價的隨機替代佈局**（`getRandomStartingPlan` 隨機取一個）。
2. **Upgrade level** 用 PNG 檔名尾數 `0,1,2…` 表示。載入器 `while (key_<var><level>.png exists)` 逐一掃描，level 0 必須存在。
3. **`<key>_<var>.txt` 的第 N 行（非空、非 `//` 註解行）對應 level N 的 config。** 即：txt 檔每一非空行餵給一個 PNG level。第一行 = level 0 的 config（也是整個 set 的「主 config」，含 `max`/`shop`/住戶等只在 level 0 出現的全域欄位）；後續行是各升級階的 override/增量 config（常見只有 `priority`、`pathlevel`、`french`(改名) 等）。
   - 範例 `church_A.txt`：第 1 行 `…french:Chapelle;…pathlevel:1`，第 2 行 `french:Eglise;pathlevel:2` → 升級後改名為 Eglise 並重鋪更高等級的路。
   - 範例 `abbey_A.txt`：第 1 行是主 config，後面數行只含 `startLevel:-10` / `startLevel:-1`，控制每一階的垂直起點。
4. config 繼承：建構 level>0 的 plan 時把 level 0 的 plan 當 `parent` 傳入，`initialiseConfig(parent)` 先複製 parent 的多數欄位，再用該 level 自己那行 config 覆寫（見 §5）。

> **新 parser 契約**：必須支援「一個 txt 多行、每行對一個 PNG level」「字母 variation / 數字 level」這套雙軸命名，且 level>0 繼承 level 0 的設定。

### 2.2 Config 行語法

- 一行 config = 以 `;` 分隔的多個 `key:value`（`line.split(";",-1)`）。
- 每段再 `split(":")`，**只有剛好兩段（length==2）才被當有效 key:value**，否則忽略。
  → 注意：value 內不能含 `:`（這是個硬限制，重寫時沿用或改良需謹慎，否則破壞既有檔）。
- key 大小寫不敏感（`equalsIgnoreCase`）。未知 key 會 log error（非致命）。
- 空白行被略過；`//` 開頭為註解。

### 2.3 欄位語義字典（權威來源：`BuildingPlan.readConfigLine`）

| key | 型別 | 內部欄位 | 意義／意圖 |
|---|---|---|---|
| `max` | int | `max` | 此建築在一個村莊中可存在的最大數量。`0` = 無上限或特殊（town hall、abbey、subbuilding 常為 0）。`subbuilding` 型別強制 `max=0`。 |
| `priority` | int | `priority` | 建造選擇權重（**必須 ≥1**，否則 log error）。村莊挑下一棟蓋什麼時的加權。每個 level 都可有自己的 priority。 |
| `moveinpriority` | int | `priorityMoveIn` | 住戶遷入的優先序（決定哪些建築先有人住）。 |
| `french` / `native` | string | `nativeName` | 該文化母語顯示名（norman→法文）。可在升級行覆寫以改名。 |
| `english` / `name_<lang>` | string | `names[key]` | 各語言在地化名稱（key 以 `name_` 前綴存入 `names` map）。 |
| `around` | int | `areaToClear` | 建築四周需整地/淨空的格數（向外擴張清理半徑）。 |
| `startLevel` | int（可負） | `startLevel` | **此 plan 的垂直起點偏移**（相對於定位錨點 Y）。負值表示建築從錨點**往下**延伸（地基、地下室、井）。詳見 §5.2。 |
| `orientation` | int 0–3 | `buildingOrientation` | 建築的基準朝向（schematic 作者繪製時的朝向）。與放置時實際 orientation 合成。見 §6.1。 |
| `pathlevel` | int | `pathLevel` + `rebuildPath=true` | 連到此建築的道路等級；設定即觸發重鋪路。 |
| `rebuildpath` | bool | `rebuildPath` | 是否重建道路。 |
| `pathwidth` | int | `pathWidth` | 道路寬度。 |
| `isgift` | bool | `isgift` | 是否為「贈與型」建築。 |
| `reputation` | int | `reputation` | 蓋此建築所需/影響的玩家聲望。 |
| `price` | int | `price` | 購買/解鎖價格（denier）。 |
| `length` | int | `length` | 建築在 Z 方向的深度（= PNG 高度，見 §3）。 |
| `width` | int | `width` | 建築在 X 方向的寬度（= 每個 Y 層的像素寬，見 §3）。 |
| `male` | string（可重複） | `maleResident[]` | 男性住戶 villager type（須在 `culture.villagerTypes` 中）。可多次出現累加。 |
| `female` | string（可重複） | `femaleResident[]` | 女性住戶 type，同上。 |
| `exploretag` | string | `exploreTag` | 探索/解鎖標籤。 |
| `requiredtag` | string | `requiredTag` | 需要村莊已具備某 tag 才能蓋。 |
| `irrigation` | int | `irrigation` | 提供的灌溉值（井、噴泉用，例 `well_A` level0=5、level1=15）。 |
| `shop` | string | `shop` | 商店類型（須存在於 culture 的 shopBuys/shopSells/shopBuysOptional）。 |
| `minDistance` | float（值/100） | `minDistance` | 與村莊中心的最小距離比例（輸入 `50` → 0.5）。 |
| `maxDistance` | float（值/100） | `maxDistance` | 與村莊中心的最大距離比例。 |
| `signs` | int CSV | `signOrder[]` | town hall 告示牌上各建築項目的顯示順序（索引陣列）。 |
| `tag` | string（可重複） | `tags[]` | 語意標籤（小寫累加），如 `armoury`、`leasure`、`cattle`、`chicken`、`pigs`、`sheeps`、`Praying`。被住戶 AI / 動物生成驗證使用（例：有 `cowspawn` 像素就必須有 `cattle` tag）。 |
| `subbuilding` | string（可重複） | `subBuildings[]` | 附屬子建築 key（隨主建築一併規劃）。 |
| `startingsubbuilding` | string（可重複） | `startingSubBuildings[]` | 建造當下立刻附帶的子建築。 |
| `startinggood` | CSV×4 | `startingGoods[]` | 初始貨物：`good,probability,min,max`（good 名須在 `Goods.goodsName`）。lone buildings 大量使用（廢墟裡的戰利品）。 |
| `type` | string | `type` | 建築型別；`subbuilding` 為特例（強制 max=0）。 |
| `showtownhallsigns` | bool | `showTownHallSigns` | 是否在 town hall 告示牌顯示。 |

> 上面是**完整**的 header DSL。逐 block 的「建造指令」並不在 txt 內，而是**全部編碼在 PNG 像素**裡（見 §3、§4）——這是本系統與一般「文字 schematic DSL」最大的不同。

### 2.4 Custom building config（`custombuildings/custom_*.txt`）

由 `BuildingCustomPlan` 解析，格式同為 `;` 分隔 `key:value`，但語意不同（描述「玩家自建建築的功能標記」，佈局來自玩家現場用魔杖框選）。觀察到的 key：`native`、`gameNameKey`（綁定到某個既有 plan 名如 `farm_A0`）、`male`/`female`、`moveinpriority`、`cropType`、`chest:1-5`（箱子數量範圍）、`sign:1`、`field:10-30`（田地大小範圍）。新實作可視為一套較簡化的功能宣告。

---

## 3. 分層 PNG schematic 編碼規則（格式契約核心）

> 權威來源：`BuildingPlan` 的 PNG constructor（約 line 1676–1820）。**這是整個復用計畫最關鍵的契約，務必逐字實作。**

### 3.1 三軸映射

對單一 `<key>_<var><level>.png`：

- **圖片高度 height == `length`**（建築 Z 深度）。載入時若 `height != length` 直接拋例外。圖片的 **row j（由上到下，0..length-1）對應建築的 Z 軸（length 方向）**。
- 一張 PNG **水平並排了該 level 的所有 Y 樓層（floors）**，樓層之間有 **1 像素分隔欄**。樓層數由寬度推回：

  ```
  nbfloors = (imgWidth + 1) / (width + 1)
  ```

  必須整除，否則拋例外。即每個 floor 佔 `width` 像素寬，floor 之間夾 1px 間隔。floor index `i`（0..nbfloors-1）對應 **Y 高度層（由下到上）**。
- 寬度方向（X，width）讀取時做**水平鏡像**：取像素 X 座標為
  ```
  px = i*width + i + (width - k - 1)   // k = 0..width-1
  ```
  其中 `i*width + i` 是第 i 個 floor 的起始欄（含分隔），`(width-k-1)` 是該 floor 內反向取像素。→ **PNG 內 X 是左右翻轉存放的**；重寫時必須照樣翻，否則所有建築左右顛倒。

> 實證（System.Drawing 量測）：
> - `armoury_A0.png` = 125×7，width=13 length=7 → floors = (125+1)/(13+1)=**9**。
> - `farm_A0.png` = 143×14，width=17 length=14（注意 farm_A width 在 _A 與 _B 不同：17 vs 16）。
> - `abbey_A0.png` = 252×17 與 `abbey_A11.png` = 275×17：同建築不同升級階**樓層數不同**（越升越高/越寬），但 height(length)=17 不變。

### 3.2 像素 → PointType

- 取得 24-bit RGB（`getRGB(px,j) & 0x00FFFFFF`），在 `colourPoints`（HashMap<int colour, PointType>，由 `blocklist.txt` 載入）查表。
- **Alpha 處理**：若圖片有 alpha channel（TYPE_4BYTE_ABGR），**非完全不透明（alpha != 0xFF）的像素一律當「空（empty / 白 0xFFFFFF）」丟棄**。這是「畫圖時用透明來表示這一層此格不放東西」的機制。
- 查不到的顏色 → log error 並當白色（empty）略過。**白色 `255/255/255` = `empty` = 此格不動**（保留世界原狀）。

### 3.3 偏移與錨點

```
lengthOffset = floor(length * 0.5)
widthOffset  = floor(width  * 0.5)
```

建築的世界座標由錨點 `(x,y,z)` 加上 `adjustForOrientation(x, y+i+startLevel, z, j-lengthOffset, k-widthOffset, orientation)` 算出。意即：

- **建築水平中心**對齊錨點（中央那格 = 錨點），floor `i` 的世界 Y = `y + i + startLevel`。
- `startLevel` 直接平移整個 plan 的 Y（負值下沉）。

### 3.4 特殊像素（非實體方塊的語意標記）

`blocklist.txt` 第一段定義一批「special tags」(`params[1]` 為空者，只有 name 無 block)，在 PNG 裡用特定顏色畫，於建造/引用階段轉成功能點而非方塊：

| name | 顏色 RGB | 意圖 |
|---|---|---|
| `empty` | 255/255/255 | 空——此格不放任何東西（升級時尤指「透明，不覆蓋既有」）。 |
| `preserveground` | 0/200/0 | 保留/接續地表（地形整理）。 |
| `allbuttrees` | 150/255/150 | 清除除樹以外。 |
| `grass` | 0/128/0 | 鋪草地。 |
| `mainchest` | 0/0/255 | 主箱（建築核心庫存）。**僅 level 0 允許**，升級檔若出現會被移除並 log。 |
| `lockedchest` | 135/30/0 | 上鎖箱。 |
| `sleepingPos` | 0/128/255 | 住戶睡覺點。**有住戶(level0)卻無此點會 error。** |
| `sellingPos` | 200/0/0 | 商店販售站位。 |
| `craftingPos` | 200/125/0 | 製作站位。 |
| `defendingPos` | 200/150/0 | 防禦站位。 |
| `shelterPos` | 200/175/0 | 避難站位。 |
| `pathStartPos` | 200/250/0 | 道路起點（連接村莊路網）。 |
| `leasurePos` | 50/50/250 | 休閒站位。 |
| `*soil`（soil/ricesoil/turmericsoil/maizesoil/carrotsoil/potatosoil/sugarcanesoil/netherwartsoil/vinesoil…） | 64/128/xx 等 | 各種作物田土，`referenceBuildingPoints` 轉成對應 CROP 的耕地點。 |
| `*spawn`（cowspawn/pigspawn/sheepspawn/chickenspawn/squidspawn/wolfspawn） | 1xx/10/10 等 | 動物生成點。**須與對應 tag 一致**，且家畜生成點數量應為偶數（否則 warn）。 |
| `*spawn`（oakspawn/pinespawn/…/darkoakspawn） | 樹苗生成 | 種樹點。 |
| `spawner*`（skeleton/zombie/spider/creeper/blaze…） | 80/0/12x | 怪物 spawner（lone 地城用）。 |
| `*source` / `free*`（stonesource/sandsource/claysource/freestone…） | — | 採集資源點（村民取材處）。 |
| `fishingspot`/`healingspot`/`brewingstand`/`furnace`/`stall`/`tapestry`/`*statue`/`byzantineicon*` | — | 功能性錨點。 |
| `*Guess`（woodstairsOakGuess/stonestairGuess/ladderGuess/signwallGuess/plainSignGuess） | — | 「方向自動推測」佔位（見 §3.5）。 |

> **新實作必須把「特殊顏色 → 功能點」與「一般顏色 → 方塊」當兩條分流處理。** 功能點不一定生成可見方塊，而是登記到 BuildingResManager（睡點、田、生成點、招牌清單等）。

### 3.5 方向性方塊與「Guess」/二步建造

許多方塊有方向（樓梯、門、床、招牌、原木軸向、藤蔓…）。blocklist 為每個方向各定一個 name+顏色（如 `woodstairsOakTop/Bottom/Left/Right`、`doorTop/Bottom/Left/Right`），PNG 直接畫出明確方向。另有 `*Guess` 系列（如 `woodstairsOakGuess`），建造時依鄰格自動推導朝向（`mapIsOpaqueBlock`/`mapIsStairBlock` 判斷）——這是給美術省事的「自動定向」。實作時 `orientation` 旋轉會把 Top/Bottom/Left/Right 互換（line 1180+ 的大型 switch 即在做朝向旋轉重映射）。

部分方塊標 `secondStep=true`（blocklist 第 4 欄），代表**需要支撐、必須在主結構後再放**（火把、柵欄、水、門、藤蔓、招牌、地毯、雪…）。建造分兩遍：第一遍放所有非 secondStep 方塊，第二遍才放 secondStep（見 §7）。

---

## 4. blocklist.txt 顏色映射規則

> 權威來源：`PointType.readColourPoint`。

### 4.1 行格式

```
name;blockRef;meta;secondStep;R/G/B
```

5 欄，以 `;` 分隔（`split(";",-1)` 必須剛好 5 段，否則拋例外）：

| 欄 | 名稱 | 說明 |
|---|---|---|
| 0 | `name` | 點名稱（special tag 名，或一般方塊的指示性名稱）。 |
| 1 | `blockRef` | 方塊參照。**空字串 = special tag（只有 name，無方塊）**。非空時為 Minecraft 方塊：可為數字 ID（舊式，如 `17`）或命名空間 ID（如 `minecraft:stone`、`millenaire:tile.ml_earth_deco`）。 |
| 2 | `meta` | 方塊 metadata / damage value（int）。 |
| 3 | `secondStep` | bool；true = 第二遍建造（需支撐的方塊）。 |
| 4 | `R/G/B` | 顏色，`split("/")` 必須 3 值，組成 `colour = (R<<16)|(G<<8)|B`。 |

組合規則（`PointType`）：
- 若欄 1 為空 → `new PointType(colour, name)`（special tag，無 block）。
- 否則 → `new PointType(colour, blockRef, meta, secondStep)`，透過 `Block.getBlockFromName` 解析。

### 4.2 載入與衝突

- `colourPoints` 以 **colour(int) 為 key**。載入時若同色已存在會 log（顏色必須唯一）。
- `0x00FFFFFF`（白）固定代表 empty。
- blocklist 另有「//special tags」「//simple tags」分區註解，僅供人閱讀，不影響解析（解析只看 5 欄結構，跳過 `//` 與空白行）。

### 4.3 與舊版的耦合（需重新推導）

- 數字方塊 ID（`17`=log）、`Blocks.xxx`、`minecraft:log2`+meta 這套 1.12.2 方塊模型，在 26.2 已改為 blockstate / 命名空間 ID，**meta 概念消失**。重寫時要把 blocklist 的 `(blockRef, meta)` 對映表整體**重新建一張 1.12.2→26.2 對照**（見 §9）。建議保留 blocklist 的「顏色 → 邏輯方塊名」這一層，另建「邏輯方塊名 → 26.2 blockstate」一層，讓既有 PNG 完全不用改色。

---

## 5. 升級系統意圖

### 5.1 升級階梯

- 一個建築有多個 PNG level（`_A0`,`_A1`,…），代表同一棟建築在村莊發展過程中的逐步增建。
- 升級條件（`Building`/`BuildingProject`）：村莊發展度足夠、`upgradesAllowed`、玩家負擔得起資源（`canAffordBuildAfterGoal`），則把目標 level 提升一級，villager 再逐塊建造**新增的部分**。
- **升級是增量疊加**，不是整棟重建：`getConsolidatedPlan(variation, level)` 把 level 0..current 全部合併成一個 3D plan：
  - 對升級層（lid>0），**白色 empty 像素視為「透明、不覆蓋」**（`if (!isType(empty) || lid==0)`）——所以升級 PNG 只需畫「相對前一階的新增/變更方塊」，其餘留白。
  - level 0 的所有像素（含 empty）都生效。

### 5.2 startLevel（垂直對齊）

- 每個 level 可有自己的 `startLevel`（負值下沉）。`getMinLevel`/`getMaxLevel` 掃描所有已含 level 求出整棟建築的 Y 範圍，`ioffset = plan.startLevel - minLevel` 把各階的 floor 疊到正確高度。
- 意圖：地基（深層）可能只在某些階出現；升級時往上加樓層；井/噴泉用很負的 startLevel（`well_A` 為 -11）把結構整個埋到地下。
- `mainchest` 只能在 level 0；升級層不可重複放主箱。

### 5.3 variation 與升級的關係

- variation（A/B/C）是**平行替代佈局**，在建造當下隨機決定一個並固定（`location.getVariation()`），之後所有升級都沿用同一 variation 的 PNG 階梯。

---

## 6. 建築放置規則意圖

### 6.1 朝向（orientation）

- `orientation ∈ {0,1,2,3}`，`adjustForOrientation` 對 `(xoffset,zoffset)` 做 90° 旋轉：
  - 0: `(x+xo, z+zo)`；1: `(x+zo, z-xo)`；2: `(x-xo, z-zo)`；3: `(x-zo, z+xo)`。
- plan header 的 `orientation` 是作者繪圖朝向；放置時的實際 orientation 來自 `BuildingLocation`（依地形/道路選最佳朝向）。方向性方塊（門/樓梯/床/招牌）的 meta 會依 `(direction - orientation) % 4` 重新計算（`getDoorMeta`、`getFenceGateMeta` 等），確保旋轉後仍正確面向。

### 6.2 選位

- `findBuildingLocation(winfo, pathing, townHallPos, radius, …)` 在村莊半徑內找合法落點，考量：
  - `minDistance`/`maxDistance`（與中心的距離比例）。
  - `around`（四周需淨空格數）。
  - 地形可建性：`isBuildable` 只允許在 air/leaves/log/蘑菇/花 上整地；`preserveground`/`allbuttrees` 控制整地策略。
  - 與既有建築不重疊、能連到路網（`pathStartPos`）。
- `priority` / `moveinpriority`：村莊用加權隨機決定「下一棟蓋什麼」與「誰先入住」。`max` 限制數量。`requiredTag`/`exploreTag` 作為前置條件。

### 6.3 子建築

- `subBuildings` / `startingSubBuildings`：主建築規劃時連帶規劃附屬結構（例 `fort_A` 帶 `lefttower`/`righttower`，`largefort_A` 帶 `armoury`/`barrack`）。檔名以 `主名_子名_A.txt` 形式存在於同目錄。

---

## 7. 建造流程（construction progression）

> 權威：`BuildingPlan.getBuildingPoints` 產生有序 `BuildingBlock[]`；`Building` 用 `bblocksPos` 游標逐塊施工。

1. **展開為 BuildingBlock 序列**：對 consolidated plan 逐 floor、逐格呼叫 `adjustForOrientation` 得世界座標，產生 `BuildingBlock(point, block, meta, special)`。順序即建造順序：
   - 先地形處理：`CLEARGROUND`/`CLEARTREE`/`PRESERVEGROUNDDEPTH`/`PRESERVEGROUNDSURFACE`（special byte 常數定義在 `BuildingBlock`）。
   - **第一遍**：所有 `block != air && !secondStep` 的實體方塊（由低樓層到高樓層）。
   - **第二遍**：所有 `secondStep` 方塊（火把/門/床/水/招牌/雪…需要支撐者）。
   - 特殊功能點（tapestry/statue/icon/spawner 等）以各自 special 常數插入。
2. **去重/優化**：建構時用 `bbmap` 比對世界現況，若該格已是目標方塊則 `toDelete`（跳過），避免重複放置；`CLEARTREE`/`CLEARGROUND`/`PRESERVEGROUND*` 也依現況決定是否需要動作。
3. **逐塊施工**：`bblocksPos` 從 0 遞增，村民每個建造 tick 放一塊（`getBblocks()[bblocksPos]` → setBlock → `bblocksPos++`）。進度 = `bblocksPos*100/length`。完工後 `bblocksPos` 重置、清空 goal。
4. **資源耦合**：`computeCost()` 預先統計整棟的 `resCost`（InvItem→數量）。`canAffordBuild` 檢查村莊主箱是否有足夠材料；不足則 `buildingGoalIssue = ui.lackingresources`，停工等資源（村民去採集/購買補足）。
5. **引用階段**：`referenceBuildingPoints` 把田土/睡點/生成點/招牌/各種 Pos 登記進 `BuildingResManager`，供村民 AI 後續使用（種田、睡覺、開店、生怪）。

> 意圖：建造是**有狀態、可中斷、資源驅動**的長流程，而非一次性 paste。重寫時要保留「序列化游標 + 資源 gating + 兩遍式 secondStep」三個要素，否則建築會塌（先放火把後放牆）或無限缺料。

---

## 8. core / extra / lone / custom 角色差異

| 類別 | 目錄 | 角色 | 關鍵差異 |
|---|---|---|---|
| **core** | `buildings/core/` | 村莊主體建築（住宅、農場、商店、town hall、教堂…）。 | 受 village type 的 `core:` / `start:` / `centre:` 清單調度（見 `villages/*.txt`）；有住戶、商店、升級階梯；參與村莊規劃與道路網。 |
| **extra** | `buildings/extra/` | 點綴/基礎設施（well、fountain、largefountain）。 | 通常無住戶，提供功能值（如 irrigation）；隨村莊散佈，常用很負的 startLevel。 |
| **lone** | `buildings/lone/` | 與村莊無關的獨立結構（廢墟、盜賊塔/巢、地城、anomaly、castle…）。 | 世界生成時獨立散佈；大量 `startingGood`（戰利品）、`spawner*` 怪物點；多無升級、無住戶。 |
| **custom** | `custombuildings/custom_*.txt` | 玩家用建築魔杖自製/匯入的建築。 | 由 `BuildingCustomPlan` 處理；佈局來自玩家現場框選的世界方塊（匯出成 plan），config 只宣告功能（cropType/chest/field/sign），`gameNameKey` 綁定既有 plan 名。 |

村莊型別檔（`cultures/<c>/villages/<type>.txt`）用 `centre:`、`start:`、`core:`、`player:` 等 key 決定哪些 building key 進入該型別的建造池，並含 `weight`、`biome:`、`pathmaterial:` 等。**這份檔屬於「村莊/聚落子系統」，但與建築系統共用 building key 命名空間**——重寫時需協調。

---

## 9. 與其他子系統互動

- **Culture / Villager**：`male`/`female` 須對應 `culture.villagerTypes`；`shop` 須對應 culture 的買賣表。住戶 AI 依 schematic 的 Pos 點工作。
- **Goods / 經濟**：`computeCost`/`resCost`、`startinggood`、商店 → 與貨物/庫存系統綁定。
- **Village / 聚落生成**：village type 決定建築池；town hall 告示牌 UI 觸發 `buildingGoal`。
- **Pathing / 道路**：`pathlevel`/`pathwidth`/`pathStartPos` → 道路子系統重鋪路網。
- **BuildingResManager**：建造完成後所有功能點（田、睡點、箱、招牌、生成點）的登記中樞，是建築與 AI 的橋樑。
- **World gen**：lone buildings 由世界生成散佈。

---

## 10. 與舊 MC API 耦合、需重新推導的部分

1. **方塊模型（最大）**：blocklist 與 `PointType` 全面使用 1.12.2 的 `Block`+`int meta`（含數字 ID `17`、`Blocks.xxx`、`minecraft:log2`+meta）。MC 26.2 改用 `BlockState`/命名空間 ID、無 meta。需建「邏輯方塊名 ↔ 26.2 blockstate」對照層，保留 PNG 與顏色不變。
2. **方向性方塊的 meta 計算**（門/樓梯/床/柵欄門/招牌/原木軸向/藤蔓）：`getDoorMeta`、`getFenceGateMeta`、line 1180+ 的旋轉 switch，全建立在舊 meta 數值上 → 改以 blockstate property（facing/half/axis/shape）重寫。
3. **特殊方塊**：`millenaire:tile.ml_*`（colombages、thatch、whitewashedbricks、paperwall、tapestry、statue、byzantine icon/tiles…）是 mod 自有方塊，需在新 mod 重新註冊並對映。
4. **TileEntity / 招牌文字**（`TileEntitySign`、signOrder、export building）。
5. **ImageIO / BufferedImage** 型別偵測（TYPE_3BYTE_BGR / TYPE_4BYTE_ABGR / alpha 判斷）——可沿用標準 Java ImageIO，但要確認 26.2 端載入路徑（資源 vs 設定目錄）。
6. **動物/怪物 spawn**、spawner、CROP 種類常數（`Mill.CROP_WHEAT` 等）對應 26.2 entity/實體。
7. **世界讀寫**（`setBlockAndMetadata`、`getBlock`、NBT `bblocksPos` 序列化）。

---

## 11. 值得保留的微妙設計

1. **內容/引擎分離**：佈局純由 PNG 表達、語意由一行 config 描述。美術不寫程式即可新增建築。**強烈建議完整保留此契約並直接復用既有 PNG。**
2. **alpha = 不覆蓋**：用透明像素表達「此格留白/不動」，讓升級 PNG 只畫差異，極簡潔。
3. **水平並排樓層 + 1px 分隔**：用單一 PNG 表達整棟 3D 結構，便於美術在 2D 編輯器中編排。**務必照樣解析（含 X 鏡像、`(w+1)` 整除檢查）。**
4. **兩遍式建造（secondStep）**：保證需支撐方塊在主結構之後放置，避免掉落。
5. **`*Guess` 自動定向**：依鄰格推導樓梯/招牌/梯子朝向，降低美術負擔（可選擇在新版保留或全部改為顯式方向）。
6. **資源驅動的可中斷建造游標**：`bblocksPos` + `resCost` gating，使「逐塊蓋、缺料停工」成為玩法核心。
7. **去重優化**：建造前比對世界現況跳過已正確的格，省工且支援升級的增量特性。
8. **每 level 一行 config 的緊湊繼承模型**：升級只需一行 override，配合 PNG 差異圖。

---

## 12. 未決問題（待新版決策）

1. **meta → blockstate 對照表**：需逐條盤點 blocklist 全部 ~447 行，建立 1.12.2→26.2 映射；部分舊方塊在 26.2 已不存在或拆分（snow_layer meta、stonebrick variants、log/log2…）。
2. **`*Guess` 自動定向**：保留鄰格推導，還是要求美術改畫顯式方向？影響既有 PNG 是否需重畫。
3. **value 不能含 `:` 的限制**：是否在新 parser 放寬（改用 `split(":",2)`）？需確保不破壞既有檔。
4. **數字方塊 ID（如 `17`）**：blocklist 仍有舊數字 ID 行，是否一律改為命名空間 ID。
5. **alpha vs 純白**：兩者都代表 empty，但語意上 alpha 用於升級差異、白用於 level0；新版是否統一。
6. **custom building 匯入格式**：`BuildingCustomPlan` 的世界→plan 匯出依賴舊 world API，需整套重寫；是否沿用 `gameNameKey` 綁定機制。
7. **村莊 building 池與 building 系統的命名空間協調**：village type 的 `core:`/`start:`/`centre:` 與本系統共用 key，跨子系統介面需明確化。
8. **farm_A(17) vs farm_B(16) 寬度不同**：同 set 內各 variation 寬高可不同（loader 只校驗同一 variation 內各 level 寬高一致），新版需確認 BuildingLocation 選位對不同 variation 尺寸的處理。

---

### 附：關鍵原始檔路徑（唯讀參考）

- `_reference/kinniken-src/mill/org/millenaire/common/building/BuildingPlan.java`（DSL 解析 `readConfigLine` ~L3476；PNG 解碼 constructor ~L1676；`adjustForOrientation` L187；charPoints/colourPoints L1032+）
- `.../building/BuildingPlanSet.java`（variation/level 載入 `loadPictPlans` L148；`getConsolidatedPlan` L47）
- `.../building/PointType.java`（`readColourPoint` blocklist 行解析）
- `.../building/Building.java`（建造游標 `bblocksPos`、goal/upgrade 流程）
- `.../building/BuildingBlock.java`（special 常數 CLEARGROUND/CLEARTREE/PRESERVEGROUND*）
- `.../building/BuildingCustomPlan.java`（custom building）
- 內容：`millenaire/blocklist.txt`、`millenaire/Colour Sheet.png`、`millenaire/cultures/<c>/buildings/{core,extra,lone}/`、`.../custombuildings/`、`.../villages/`
