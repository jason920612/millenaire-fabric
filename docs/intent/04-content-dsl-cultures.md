# 04 — 內容 DSL、文化組成、經濟資料模型（意圖分析書）

> 子系統範圍：內容如何被組織成一個「文化（Culture）」、各 DSL 檔的欄位語義、村莊類型（VillageType）的組成意圖、經濟 goods 的資料模型、語言/在地化系統、config 可調項。
>
> **不含**（交給其他代理）：goal 排程行為（villager `goal=` 列表的執行語義）、schematic / building plan 的 PNG 編碼（`buildings/`、`blocklist.txt` 顏色映射的繪製規則）。本文只描述這些東西「如何被宣告與引用」，不描述其執行。
>
> 參考源碼（唯讀）：
> - 內容資料：`_reference/kinniken-src/millenaire/`
> - Java 解析器：`_reference/kinniken-src/mill/org/millenaire/common/`（`Culture.java`、`VillageType.java`、`VillagerType.java`、`item/Goods.java`、`core/MillCommonUtilities.java`）

---

## 1. 用途與玩家可見角色

Millénaire 是「自動生成的 NPC 村莊」mod。對玩家，內容系統決定：

- 玩家在世界中遇到哪幾種**文化**的村莊（諾曼、印度、日本、瑪雅、拜占庭）；每個文化外觀、建築、村民職業、語言、貿易品全然不同。
- 每個村莊的**類型**（農業村、軍事村、修道院村、小村 hameau、自治大鎮、玩家可控領地…），決定它蓋哪些建築、住哪些村民、賣什麼。
- 與村民**交易**：用 denier（中世紀貨幣）買賣 goods，價格由文化的 `traded_goods.txt` 與村莊類型的價格覆寫共同決定；外來商人有獨立價格。
- 村民有**名字、職業稱謂、招呼語、對話**，且全部可在地化，並支援「語言學習」機制（互動夠多才顯示翻譯）。
- 玩家可**接管/建造**自己的領地（`controlled` / `customcontrolled` 村莊類型），手動下訂單蓋建築。

設計核心意圖：**所有遊戲內容皆為純文字資料驅動（data-driven）**，使用者（甚至 modder）只改 `millenaire/` 下的 txt 即可新增文化、村莊、村民、商品、語言，不需改 Java。這對 Fabric 重寫是最重要的繼承點——應保留「內容即資料」原則，並評估轉成 datapack。

---

## 2. 文化組成模型（一個「文化」由哪些檔組成）

一個文化 = `millenaire/cultures/{key}/` 目錄，`key` 即目錄名（`norman`、`byzantines`、`hindi`、`japanese`、`mayan`）。載入入口在 `Culture.java`（`loadCultures` 掃 `cultures/` 下每個子目錄，目錄名即 `culture.key`）。

目錄結構（以 `norman/` 為例）：

| 路徑 | 內容 | 解析器 |
|---|---|---|
| `culture.txt` | 文化全域設定（極小） | `Culture.readConfig()` |
| `villages/*.txt` | 村莊類型定義（一檔一型） | `VillageType` 建構子 |
| `lonebuildings/*.txt` | 孤立建築類型（同 VillageType，`lonebuilding=true`） | `VillageType.loadLoneBuildings()` |
| `villagers/*.txt` | 村民類型定義（一檔一型） | `VillagerType.loadVillagerType()` |
| `shops/*.txt` | 商店買賣清單（一檔一店，店名=檔名） | `Culture.loadShop()` |
| `namelists/*.txt` | 姓名/村名候選清單（一行一名） | `Culture.loadNameLists()` |
| `traded_goods.txt` | 該文化的經濟資料模型（價格、庫存目標） | `Culture.loadGoods()` |
| `buildings/`、`custombuildings/` | 建築 schematic（**他人負責**） | （略） |

**全域（非文化專屬）檔**，在 `millenaire/` 根：

| 檔 | 意圖 |
|---|---|
| `goods.txt` | **全域物品註冊表**：`good 名稱 → Minecraft item/方塊 + meta`。所有文化的 `traded_goods.txt`、`shops`、villager `requiredGood` 等都用 good 名稱當 key，再經此表解析成實際 item。檔頭註明「auto-generated, don't edit」。 |
| `itemlist.txt` / `blocklist.txt` | item / block 的 key→id→meta 映射（blocklist 還含 schematic 顏色，那部分屬他人範圍）。`Goods.java` 從 `goods.txt`（base dir）與 `itemlist.txt`（loadDir）載入。 |
| `config.txt` / `config-base.txt` / `config-server.txt` | 玩家/伺服器可調設定。 |
| `languages/{en,fr}/` | 在地化字串（見 §6）。 |
| `quests/`、`languages/.../quests_*.txt`、`languages/.../parchments/` | 任務與羊皮紙文本（文本面屬在地化，邏輯面可能屬他人）。 |

**文化之間的差異維度**（重要：差異幾乎全在資料，不在程式碼）：

- **建築風格**：各文化自己的 `buildings/` schematic 與 `villages/*.txt` 組成。
- **村民**：各文化自己的 `villagers/*.txt`（職業、貼圖、性別、模型）。
- **語言/在地化**：各文化有 `{key}_strings/sentences/dialogues/reputation/buildings.txt`（§6）。
- **貨幣**：**所有文化共用 denier 體系**（denier / denierargent / denieror，見 §5）。貨幣不是文化差異維度。
- **culture.txt** 本身幾乎不定義文化身分；它只放兩個小設定（見 §4.1）。文化的「身分」是由其目錄下所有子檔的總和湧現出來的。

---

## 3. 各 DSL 檔欄位字典

### 3.1 村莊類型 `villages/*.txt`（解析器 `VillageType.java`）

格式：**冒號分號式**，`key:value`，一行一欄，`//` 為註解。同一 key 可重複（累加成清單）。檔名（去 `.txt`）即 `village.key`。`lonebuildings/*.txt` 用同一解析器，差別只是 `lonebuilding=true` 且 `spawnable` 預設為 false。

**身分與生成控制**

| key | 型別 | 意圖 |
|---|---|---|
| `name` | string | 顯示名（如「Village agricole」）。**必填**，缺則拋例外。 |
| `type` | string | 村莊子分類；特殊值 `hameau`（小村，半徑小、建築少）。 |
| `weight` | int | 生成時的加權隨機權重（0 = 不自然生成，但仍可作 hameau/手動）。 |
| `max` | int | 該型最多生成數（0=無限）。 |
| `radius` | int | 村莊半徑（預設取自 config `village_radius`）。 |
| `spawnable` | bool | 是否能自然生成。 |
| `generateOnServer` | bool | 伺服器端是否生成。 |
| `generateForPlayer` | bool | 是否為玩家專用生成。 |
| `minDistanceFromSpawn` | int | 離世界出生點最小距離（如 banditlair=600）。 |
| `carriesraid` | bool | 此村是否會發動/承載突襲（raid）。 |
| `playerControlled` | bool | **玩家可控領地**（`controlled.txt`、`customcontrolled.txt`）。控制此型須有對應 culture-control 解鎖 tag。 |
| `keyLoneBuilding` / `keyLoneBuildingGenerateTag` | bool/string | 標記為「關鍵孤立建築」（劇情/任務用）與其生成 tag。 |

**生物群系與地形限定**

| key | 意圖 |
|---|---|
| `biome` | 可生成的生物群系名（可多行；含 1.7 原版名與 extraBiomes mod 名）。 |
| `requiredtag` / `forbiddentag` | 生成需要/禁止的 world tag。 |

**地名修飾語（qualifier）— 在地化命名用**

`qualifier`（可多行，隨機選一）、`hillQualifier`、`mountainQualifier`、`desertQualifier`、`forestQualifier`、`lavaQualifier`、`lakeQualifier`、`oceanQualifier`。意圖：村名 = `基底名（取自 namelists/villages.txt）` + `culture.txt 的 qualifierSeparator` + 依所在地形選出的 qualifier（如「Asnières-les-collines」）。`nameList` 可覆寫取名清單（村莊預設 `villages`，孤立建築預設 `null`）。

**建築組成（成長路徑的核心意圖）**

引用的值是 building plan set 的 key（須存在於 culture 的 `buildings/`）。

| key | 意圖（成長階段） |
|---|---|
| `centre` | 村中心建築（如 manor / 玩家堡壘）。**必填**（或 `customcentre`）。 |
| `customcentre` | 以 custom building（玩家自訂藍圖）作中心。 |
| `start` | 建村時立即放置的初始建築（可多行，重複=多棟）。 |
| `core` | **優先**建造佇列（村莊成長先蓋這些）。 |
| `secondary` | core 蓋完後才蓋。 |
| `never` | 此型永不蓋的建築（排除清單）。 |
| `player` | 「玩家可請求/購買」的建築清單（新建築畫面用）。 |
| `customBuilding` | 玩家可控村可選的自訂建築型清單。 |
| `pathMaterial` | 村內道路可用的材料 good（可多行，升級路徑用）。 |

> 重複 key = list append 是這個 DSL 的核心慣用法：`core:pigfarm` 出現兩次代表「蓋兩座 pigfarm」。

**成長路徑意圖總結**：`centre` → 全部 `start` → 依序消化 `core` → 再 `secondary`；`never` 為硬排除；`player` 與 `customBuilding` 是 UI 下訂單來源；`weight`/`max`/`biome`/`tag` 控制這個型「出不出現、在哪出現、出現幾個」。`controlled`/`customcontrolled` 的 `core` 清單其實只是「新建築畫面的選單」（檔內註解明寫 `//just a list for the new building screen`），不是自動成長佇列。

**價格覆寫**

| key | 格式 | 意圖 |
|---|---|---|
| `sellingPrice` | `good,price` | **覆寫**該村賣價（村民賣給玩家）。 |
| `buyingPrice` | `good,price` | **覆寫**該村收價（村民向玩家買）。 |

price 支援斜線進位記法 `a/b`、`a/b/c`（見 §5），且只有 `price>0` 才寫入覆寫表。這層覆寫蓋過 `traded_goods.txt` 的全文化基準價（`Goods.getBasicSellingPrice/BuyingPrice` 先查村莊覆寫表，再回退基準）。

### 3.2 村民類型 `villagers/*.txt`（解析器 `VillagerType.java`）

格式：**等號行式**，`key=value`，一行一欄，`//` 註解。同一 key 多行 = 累加。檔名（去 `.txt`）即 `vtype.key`。**goal= 清單交給另一代理**；以下為其餘欄位完整字典。

**身分與顯示**

| key | 意圖 |
|---|---|
| `native_name` | 村民職業的「母語」名（如 Fermier / Forgeron），顯示用。 |
| `alt_native_name` / `alt_key` | 替代名/替代 key（同一職業的變體）。 |
| `gender` | `male` / `female`。 |
| `model` | 實體模型：`femaleasymmetrical` / `femalesymmetrical` / `zombie` / 預設 villager。 |
| `texture` | 貼圖路徑（可多行 → 隨機選一）。 |
| `baseheight` | 模型縮放（baseScale）。 |
| `clothes` | `clothname,texturePath`（可多行；同 clothname 累加成隨機池）。分層服裝貼圖。 |

**命名（連到 namelists）**

| key | 意圖 |
|---|---|
| `firstNameList` | 名字取自哪個 namelist（如 `men_names`）。 |
| `familyNameList` | 姓氏取自哪個 namelist（如 `family_names`）。 |
| `maleChild` / `femaleChild` | 此村民生小孩時，小孩用哪個 villager type。 |

**經濟/物資需求**

| key | 格式 | 意圖 |
|---|---|---|
| `requiredGood` | `good,qty` | 村民工作所需物資（如 farmer 需 `seeds,100`；smith 需 `iron,10`）。同時計入 requiredGoods 與 requiredFoodAndGoods。 |
| `requiredFood` | `good,qty` | 只計入 requiredFoodAndGoods（食物需求）。 |
| `startingInv` | `good,qty` | 出生時背包初始物資。 |
| `collectGood` | good | 村民會去採集的 good（多行）。 |
| `bringBackHomeGood` | good | 村民會搬回家的 good（多行）。 |
| `merchantstock` | `good,qty` | 外來商人出售的庫存（多行）。載入時會警告：若該 good 在文化 `traded_goods.txt` 沒有有效 `foreignMerchantPrice`。 |

**戰鬥/工具**

| key | 意圖 |
|---|---|
| `toolneeded` | 指定單一 good 作工具。 |
| `toolneededclass` | 工具類別：`meleeweapons`/`rangedweapons`/`armour`/`pickaxes`/`axes`/`shovels`/`hoes`（展開成該類所有 item）。**有 toolsNeeded 時自動補一個 `gettool` goal。** |
| `defaultweapon` | 出生時持有的武器 good。 |
| `baseAttackStrength` | 攻擊力（預設依 helpInAttacks：2 或 1）。 |
| `health` | 生命（預設依 helpInAttacks：40 或 30）。 |

**雜項**

| key | 意圖 |
|---|---|
| `experiencegiven` | 被殺時掉的經驗。 |
| `chanceWeight` | 該型村民在村中被生成的權重（WeightedChoice）。 |
| `hiringcost` | 雇用花費（deniers）。 |
| `tag` | 行為旗標（見下）。 |

**`tag=` 旗標字典**（`VillagerType` 載入後映射為 boolean）：
`child`、`religious`、`chief`、`heavydrinker`、`seller`、`meditates`、`performssacrifices`、`visitor`、`helpinattacks`、`localmerchant`、`foreignmerchant`、`gathersapples`、`hostile`、`noleafclearing`、`archer`、`raider`、`noteleport`、`hidename`、`showhealth`、`defensive`、`noresurrect`。
（如 `foreignmerchant`+`visitor` = 路過的外來商人 merchant_food；`helpInAttacks`、`chief`、`seller` 等控制 UI/戰鬥/交易行為。）

### 3.3 文化設定 `culture.txt`（解析器 `Culture.readConfig()`）

格式：等號行式。**只認兩個 key**：

| key | 意圖 |
|---|---|
| `qualifierSeparator` | 村名與地形 qualifier 之間的連接字元。諾曼/日本用 `-`（→「-les-pâtures」）；拜占庭/印度用空白 ` `。 |
| `knownCrop` | 該文化會種的作物（可多行）：norman 隱含小麥；hindi=rice,turmeric；mayan=maize；byzantines=vine；japanese=rice。影響農夫種什麼。 |

> 注意 `culture.txt` 極小：文化的真正內容散布在其他子檔。

### 3.4 商店 `shops/*.txt`（解析器 `Culture.loadShop()`）

格式：**等號式，value 為逗號分隔清單**。店名 = 檔名（去 `.txt`），對應某建築型。鍵：

| key | 意圖 |
|---|---|
| `sells` | 此店賣給玩家的 good 清單。 |
| `buys` | 此店向玩家收購的 good 清單。 |
| `buysoptional` | 「選擇性收購」清單（如各文化裝備武器，townhall 全收）。 |
| `deliverto` | 此店需要被「送貨」進來的 good（村內物流：如 bakery `deliverto=wheat`，需有人送小麥進來才能產麵包）。 |

所有 good 名都會對照 `goods` 表驗證，未知則報錯。

### 3.5 姓名清單 `namelists/*.txt`（解析器 `Culture.loadNameLists()`）

格式：**純清單**，一行一個候選字串，無 key。清單名 = 檔名（去 `.txt`）。`Culture.getRandomNameFromList(name)` 隨機取一行。

意圖：

- `men_names.txt` / `women_names.txt` / `family_names.txt` / `noble_family_names.txt`：村民取名池（由 villager 的 `firstNameList`/`familyNameList` 指名引用）。
- `villages.txt`：村莊基底名池（村莊 `nameList` 預設指這個）。
- 職業專屬池：`merchant_food_first_names.txt`、`alchemist_last.txt`、`loneinns.txt`、`loneabbeys.txt` 等——讓特定職業/孤立建築有自己風格的名字。

> 設計意圖：**姓名與身分解耦**。villager type 只說「我用哪個清單」，清單內容可獨立在地化/擴充。

---

## 4. 經濟與貨幣資料模型

### 4.1 全域物品註冊：`goods.txt`

格式：**分號式** `good_key;item_id;meta;label`。一行一物。label 僅供參考。意圖：把抽象的 good 名稱（`bread`、`iron`、`timberframe_cross`、`denier`）綁到實際 Minecraft / Millénaire item + meta。所有其他經濟檔皆以 good 名稱為通用貨幣。`Goods.goodsName: HashMap<String, InvItem>` 是此表的記憶體形式。

### 4.2 文化經濟表：`traded_goods.txt`（解析器 `Culture.loadGoods()`）

格式：**逗號式 CSV**，欄位順序固定（檔頭有註解列出）：

```
name, sellingPrice, buyingPrice, reservedQuantity, targetQuantity,
foreignMerchantPrice, autoGenerated, neededEquipment(deprecated),
minimumReputation, descCode
```

| 欄位 | 意圖 |
|---|---|
| `sellingPrice` | 村民賣給玩家的基準價（deniers）。 |
| `buyingPrice` | 村民向玩家收購的基準價。 |
| `reservedQuantity` | 村莊自留量（低於此不賣）。 |
| `targetQuantity` | 村莊想囤到的目標量（驅動村民生產/採購）。 |
| `foreignMerchantPrice` | 外來商人的價（與本村交易分離的價格軌）。 |
| `autoGenerated` | bool，是否程式自動產生此 good 條目。 |
| `neededEquipment` | 已棄用。 |
| `minimumReputation` | 須達此聲望才可交易（預設 `Integer.MIN_VALUE`=無限制）。 |
| `descCode` | 幫助畫面用的在地化 key（如 `tradehelp.normancider`）。 |

CSV 尾欄可省略（解析器逐欄做長度與空字串檢查，缺則用預設）。`Goods` 物件存兩張表：`goods`（by name）與 `goodsByItem`（by InvItem）。注意 §3.1 的村莊 `sellingPrice`/`buyingPrice` 會**覆寫**此處基準。

### 4.3 貨幣（denier）概念

- 貨幣物件：`denier`、`denierargent`（銀）、`denieror`（金），定義在 `goods.txt`，是普通 item。
- **進位制 = 64**（一組/一堆）。價格 DSL 支援斜線記法：`a/b` = `a*64 + b`，`a/b/c` = `a*64*64 + b*64 + c`。如 `normanBroadsword,1/0/10` = 1*4096 + 0*64 + 10 = 4106 deniers。對應三種幣值的兌換（金 64 = 銀的一級…）。解析在 `VillageType` 的 sellingPrice/buyingPrice 區段。
- **乘法記法 `a*b`**：`traded_goods.txt` 與部分價格用 `MillCommonUtilities.readInteger()`，它把 `*` 拆開連乘：`2*64` = 128、`2*64*64` = 8192。用來簡潔寫「N 堆」。
- 兩種記法不可混用於同欄：`/` 用於 VillageType 價格欄，`*` 用於 `readInteger`（traded_goods、village radius 等）。Parser 需分別實作。

### 4.4 商店與物流意圖

`shops/*.txt` 把 good 分成 sells/buys/buysoptional/deliverto。`deliverto` 體現村內供應鏈：產出建築（bakery）需要原料（wheat）被別的村民送進來。`townhall` 是「萬能店」，sells/buys 含大量基礎建材與工具，`buysoptional` 收購跨文化所有武器防具——意圖是讓玩家在任何村都能賣戰利品。

---

## 5. 在地化系統（languages/）

目錄：`millenaire/languages/{en,fr}/`（每個語言一資料夾，資料夾名即語言 key）。載入：`Culture.loadLanguages()` 載入 main、fallback、以及（若 `load_all_languages`）全部；`CultureLanguage` 內部類持有各 map。fallback 機制：找不到 `xx_YY` 時退回 `xx`（`language.split("_")[0]`）。

**每文化一組檔**（檔名前綴 = `culture.key`）：

| 檔 | 格式 | 內容/key 命名 |
|---|---|---|
| `{key}_strings.txt` | `key=value` | 通用字串。key 如 `culture.norman`、`villager.farmer`、`villager.smith`（村民職業顯示名）。 |
| `{key}_buildings.txt` | `key=value` | 建築顯示名。key = `{plan}_{variant}`（如 `bakery_A0`、`church_A2`）。 |
| `{key}_sentences.txt` | `key=value`（value 可雙語） | 村民招呼/巡邏台詞。key 如 `villager.greeting`、`knight.greeting`、`guard.greeting`、`*.patrol`。**同 key 多行 = 隨機台詞池**（`readSentenceFile` append）。 |
| `{key}_dialogues.txt` | **分號式區塊** | 多輪對話。`newchat;key:rain,tag:notraining,weigth:10` 起一段，後續 `v1;0;…` / `v2;30;…` 為發話者與延遲（tick）。載入後展開成 `villager.chat_{key}_{n}` 的 sentence。 |
| `{key}_reputation.txt` | 分號式 `threshold;title;text` | 聲望階級門檻（用 `4*64` 等記法）、稱號、招呼文。負值=惡名。 |

**全域語言檔**：`strings.txt`（UI/指令/啟動訊息，key 如 `ui.*`、`command.*`、`startup.*`、`tradehelp.*`）、`quests_*.txt`、`parchments/parchment_N.txt`（羊皮紙 lore；檔內 `version:` + 自由文本）、`help/`。

**雙語值慣用法**：sentences/dialogues 的 value 常寫成 `法文 / 英文`（如 `Bonjour, $name! / Hello, $name!`）。重點：**解析時整段（含 `/`）原樣存進 sentence list**，`/` 不是解析期分隔符；它是「語言學習」功能在顯示期才切分（母語 vs 翻譯）。Parser **不要**在 `/` 上切。

**佔位符**：`$name`（玩家名）、`<0>`/`<1>`…（位置參數）。

**語言學習機制**：config `language_learning=true` 時，翻譯只在玩家與該文化互動足夠後才顯示——這是把「雙語值」當功能而非冗餘的原因。

---

## 6. config 可調項意圖

三檔：`config.txt`（使用者實際生效）、`config-base.txt`（預設/範本）、`config-server.txt`（伺服器覆寫）。格式：等號行式，每項前有法/英雙語註解（`//法; …` `//英; …`）。分區（檔內以 `---` 標頭分隔）：

- **UI Settings（客戶端/玩家）**：`village_list_key`、`quest_list_key`、`escort_key`（快捷鍵）、`hd_textures`、`language_learning`、`load_all_languages`、`display_start`、`display_names`、`villagers_names_distance`、`villagers_sentence_in_chat_distance_sp/_client`、`fallback_language`、`language`。
- **World Generation（伺服器）**：`generate_villages`、`generate_lone_buildings`、`min_village_distance`、`min_village_lonebuilding_distance`、`min_lonebuilding_distance`、`spawn_protection_radius`。
- **Village Behaviour（伺服器）**：`keep_active_radius`、`village_radius`、`min_distance_between_buildings`、`village_paths`、`max_children_number`、`background_radius`（村際關係半徑）、`bandit_raid_radius`、`raiding_rate`。
- **Dev Tools**：`generate_translation_gap`、`generate_colour_chart`、`generate_building_res`、`generate_goods_list`（自動產生報表，含 §2 提到的 `goods.txt` auto-gen）。
- **Other**：`forbidden_blocks`（村民不在其上建造的 block id）、`language`。

意圖區分：UI/快捷鍵/顯示/語言 = **玩家客戶端**；生成距離/半徑/突襲/路徑/人口 = **伺服器世界規則**。Fabric 重寫應對應拆成 client config 與 server/world config。

---

## 7. DSL 格式盤點與 parser 建議

掃過全部內容檔，共 **四種**微格式，建議寫成可組合的小 parser：

| 格式 | 用在哪 | 分隔 | 重複 key | 註解 |
|---|---|---|---|---|
| **等號行式** `key=value` | villagers、culture.txt、shops、config、language strings/sentences/buildings | `=`（首個） | 累加成 list（villager texture/goal、sentence pool） | `//` 行首 |
| **冒號式** `key:value` | villages、lonebuildings | `:` | 累加成 list（core/start…） | `//` |
| **分號式** `a;b;c` | goods.txt、itemlist/blocklist、dialogues、reputation | `;` | n/a（固定欄位） | `//` |
| **逗號 CSV** `a,b,c,…` | traded_goods.txt；以及上述 value 內的子清單（shops 的 `sells=a,b,c`、價格 `good,price`） | `,` | 固定欄位、尾欄可省 | `//` |

**Parser 建議**：

1. 共通前處理：trim、跳空行、跳 `//` 開頭行。
2. 等號式與冒號式 parser 幾乎同構（差一個分隔字元）→ 寫成參數化 line parser，回傳 `multimap<key, value>`（key 大小寫不敏感：原碼大量 `equalsIgnoreCase`/`toLowerCase`）。good 名稱、building key 一律轉小寫。
3. 數值解析要支援兩種記法：`readInteger`（`*` 連乘）給 traded_goods/config；斜線進位（`a/b/c` → base-64）給村莊價格。**集中成兩個工具函式**，明確哪欄用哪種。
4. CSV（traded_goods）逐欄做「存在且非空」檢查再 fallback 預設，欄位數可變。
5. dialogues/reputation 的分號式需狀態機（`newchat` 起區塊，後續行屬同區塊）。
6. **雙語值不要在 `/` 切**——原樣保存，切分是顯示層的事。

**是否轉 datapack（評估意見）**：

- **適合轉 JSON/datapack 的**：`goods.txt`、`traded_goods.txt`、`shops`、`villages`、`villagers`、`culture.txt`——皆為結構化 key/value 或 CSV，語義清晰，轉 JSON 後可走 Fabric resource reload。建議用 registry-id（命名空間）取代裸字串 good 名。
- **語言檔**：sentences/dialogues 的「同 key 多值池」「雙語值」「語言學習」較特殊，原版 MC `lang` JSON 不支援一 key 多值；建議保留**自訂 datapack 格式**（JSON array of strings），或拆成 lang.json（UI 字串）+ 自訂對話資源。
- **namelists**：純清單，最適合轉 JSON array 或保留純文字。
- 整體建議：保留「內容即資料、檔名即 key、重複 key=累加」三原則，外殼換成 datapack JSON + Fabric `ResourceManager` reload。

---

## 8. 與其他子系統互動

- **Building / schematic（他人）**：村莊 `centre/start/core/...` 與村民 `clothes/texture` 引用 building plan set key 與貼圖路徑；good ↔ block 經 `goods.txt`/`blocklist.txt`。本子系統只「引用名稱」，不碰 PNG 編碼。
- **Goal 排程（他人）**：villager `goal=` 清單由 `VillagerType` 解析成 `Goal[]`，但**執行**屬他人。本文已列 villager 其餘所有欄位。注意載入期的耦合：有 `toolsNeeded` 會自動補 `gettool` goal、永遠補 `sleep` goal——重寫時 villager 載入器需知道這兩條隱式規則。
- **交易 UI / ContainerTrade**：消費 Goods 的 selling/buying/foreignMerchant 三價軌 + 村莊覆寫 + 聲望門檻。
- **聲望系統**：`traded_goods` 的 `minimumReputation` 與 `{key}_reputation.txt` 的階級門檻共同決定可否交易/招呼語。
- **Quest（部分他人）**：quests 與 parchments 文本走在地化系統。

---

## 9. 值得保留的微妙設計

1. **重複 key = list append**：`core:pigfarm` ×2 = 兩座；sentence 同 key 多行 = 隨機台詞池。簡潔且 modder 友善，務必保留語義。
2. **檔名即 key**：村莊/村民/商店的 key 來自檔名，不在檔內宣告——降低重複、避免不一致。
3. **good 名作通用中介層**：所有經濟引用走抽象 good 名，再經 `goods.txt` 綁 item。換 item id 不必改各文化資料。轉 Fabric 時可升級為命名空間 id，但保留中介層。
4. **三層價格**：traded_goods 基準 → 村莊類型覆寫 → 外來商人獨立軌。Building 的 getBuyingPrice 還會疊供需動態（屬交易子系統）。
5. **base-64 貨幣 + 兩種數值記法**：`a/b/c` 進位、`a*b` 連乘，讓資料人類可讀（「2 堆」「1 金 0 銀 10 銅」）。
6. **culture.txt 極簡**：文化身分是子檔總和的湧現，不是單一宣告檔。新增文化 = 複製目錄改內容。
7. **雙語值 + 語言學習**：把翻譯當「漸進解鎖的玩法」而非單純 i18n，影響資料格式（值內含 `/`）。
8. **fallback 語言鏈**：`xx_YY → xx → fallback_language`，逐層退化，缺字不崩。
9. **隱式 goal 注入**：villager 載入時自動補 `sleep` 與（有工具時）`gettool`——重寫易漏。
10. **`controlled` 的 core 清單只是 UI 選單**（非成長佇列），與普通村語義不同，檔內註解明示——重寫勿照搬成長邏輯。

---

## 10. 未決問題

1. **`goods.txt` 是 auto-generated** 且檔頭叫人別編輯：Fabric 版要保留「掃描已註冊 item 自動產生 good 註冊表」的流程，還是改成手寫 datapack？需與 building/item 子系統對齊命名空間策略。
2. **good 名 → 命名空間 id 遷移**：裸字串 good 名（`wool_white`、`timberframe_cross`）含 meta 概念；MC 26.2 早已無 metadata。需設計新的 good→item 映射（blockstate/component？），並決定舊資料如何轉換。
3. **語言學習 + 雙語值**在 datapack 化後如何表達？是否仍要 `法/英` 同字串，或拆成「原文 + 翻譯」兩欄。
4. **biome 名稱**用的是 1.7 與 extraBiomes 字串；MC 26.2 用 biome tag/registry id，村莊生成限定條件需整套重映射（生成屬他人，但 `biome:` 欄位是本子系統 DSL）。
5. **config 拆分**：client vs server/world 設定在 Fabric 應分檔（Cloth Config / 自家 loader？）——`config-server.txt` 覆寫語義要釐清。
6. **custom cultures / custom villages 覆寫鏈**：原碼支援 `custom cultures` 目錄疊加同名 culture（自訂villagers/shops/namelists 追加）。Fabric datapack 的 override/merge 行為需明確定義（後載蓋前載？list 累加？）。
7. **`alt_native_name`/`alt_key`、`model` 列舉值**（femaleasymmetrical 等）對應的實體模型清單，需與實體/渲染子系統確認完整集合。
