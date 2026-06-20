# 06 - 自訂方塊/物品/實體、交易、貨幣、網路（機制意圖分析書）

> 子系統範圍：`common/block/**`、`common/item/**`、`common/entity/**`、`common/network/**`、`common/core/**`，以及交易/貨幣程式邏輯（`ContainerTrade`、`Building` 價格、`MillCommonUtilities` 貨幣換算）。
> 內容資料層（goods/shops 的數值、村莊類型定價表來源）由另一代理負責；本文聚焦 **程式機制與註冊模型**。
> 原始碼為 1.12.2 之前的 Forge（實際 import 為 `cpw.mods.fml`，MC 1.7/1.6 等級 API：`IIcon`、`getBlock(i,j,k)`、`onItemUseFirst`、`EntityRegistry.registerModEntity`）。重寫到 Fabric / MC 26.2 時，所有與 API 耦合處需重新推導。

---

## 1. 用途與玩家可見角色

Millénaire 為玩家提供的可見元素分為幾類，本子系統涵蓋其「程式機制」骨架：

- **貨幣**：三種面額的「denier」硬幣（銅/銀/金），以 64 進位互相換算，是與村民商店交易的唯一媒介。`purse`（錢袋）把零散硬幣壓縮成 NBT 數字。
- **商店交易**：玩家走近商店建築 / 與流浪商人互動 → 開啟自訂交易 GUI（`ContainerTrade`）→ 用 denier 買村莊產物、或把資源賣給村莊換 denier。價格由村莊類型 / Goods 設定，並受聲望門檻限制。
- **特殊互動物品**：召喚法杖（建立/匯入村莊與建築）、否定法杖（管理/匯出建築）、各文化的 parchment（圖鑑/說明書）、村莊卷軸書、各式護身符（amulet，主動效果）、掛毯/雕像/聖像（放置成裝飾實體）、磚模（brickmould，用土+沙就地造泥磚）。
- **自訂方塊**：上鎖箱（村莊倉庫）、告示牌面板（panel，村莊資訊 GUI）、各種裝飾/建材方塊（木構、泥磚、石磚、拜占庭磁磚、路面）、自訂作物（稻/薑黃/玉米/葡萄藤）。
- **自訂實體**：三種泛型村民類別 + 殭屍村民、裝飾實體（掛毯/雕像），以及三種「導向型」敵對怪（Mayan 金字塔任務的圍城怪）。
- **食物/工具/盔甲**：大量文化主題的食物（含藥水效果）、工具、武器、盔甲套裝。

主註冊入口：`common/forge/Mill.java`（`@Mod` 主類）。`initBlockItems()`（行 343-571）建立所有物件並設定屬性；`registerBlocksItemsEntities()`（行 658-748）做 Forge 註冊與配方。

---

## 2. 註冊物件盤點

> 來源：`Mill.java` 的 `initBlockItems()` / `registerBlocksItemsEntities()`。物件以 public static 欄位持有，物品註冊大量靠**反射掃描 `Mill` class 的所有 `Item` 欄位**（行 729-736）一次性註冊——重寫時這個「反射批次註冊」要改成顯式 registry。

### 2.1 Block（方塊）

| 欄位 | 類別 | 意圖 | 備註 |
|---|---|---|---|
| `lockedChest` | `BlockMillChest`（extends `BlockChest`） | 村莊上鎖倉庫；右鍵走網路請求而非直接開箱 | 不可玩家破壞掉落（`quantityDropped=0`），雙箱合併邏輯複製自原版 |
| `panel` | `BlockPanel`（extends `BlockSign`） | 村莊資訊告示牌；右鍵依 `panelType` 開不同 GUI（村圖/戶籍/工程/軍事/交易…） | TileEntity = `TileEntityPanel` |
| `wood_decoration` / `earth_decoration` / `stone_decoration` | `BlockDecorative` | 多子型材建材（木構、泥磚、石磚等），靠 metadata + `registerTexture` 切換貼圖 | `ItemDecorative` 為其 ItemBlock |
| `path` / `pathSlab` | `BlockDecorativeSlab` | 村莊道路（全塊/半塊），6 種材質 metadata | shovel 採集 |
| `byzantine_tiles` / `byzantine_tile_slab` / `byzantine_stone_tiles` | `BlockOrientedBrick` / `BlockOrientedSlab` | 拜占庭文化的帶方向磁磚建材 | 有合成配方（見下） |
| `paperWall` | `BlockMLNPane`（窗格類） | 日式紙牆 | cloth 材質、易燃 |
| `cropRice` / `cropTurmeric` / `cropMaize` / `cropVine` | `BlockMillCrops`（extends `BlockCrops`） | 自訂作物，8 階段成長；`requireIrrigation`（稻需下方含水 metadata）與 `slowGrowth`（玉米）旗標控制成長速率 | 種子 = 對應 `ItemMillSeeds` |

### 2.2 Item（物品）— 機制相關的類別

| 類別（`Goods.java` 內部類別與其他） | 意圖 | 重點機制 |
|---|---|---|
| `ItemText` | 純貼圖物品基底（denier、silk、obsidianFlake 等多數簡單物品） | 只設 creative tab + texture |
| `denier` / `denier_argent` / `denier_or` | 銅/銀/金幣（`ItemText`） | 1 金 = 64 銀 = 64×64 銅；金幣是 creative tab 圖示 |
| `ItemPurse`（`purse`） | 錢袋：右鍵在「收納全部硬幣↔倒出硬幣」間切換 | 把 denier 數量存進 NBT（`ml_Purse_denier/argent/or`），顯示名動態組字串 |
| `ItemFoodMultiple` | 通用食物：可同時提供「飲料回復」「食物回復」「藥水效果」 | 大量文化食物（cider/calva/curry/sake/souvlaki…）以參數實例化 |
| `ItemMillSeeds` | 自訂作物種子，綁定 `BlockMillCrops` | |
| `ItemParchment` | 圖鑑/說明書類；以 `textsId` int 陣列指向哪幾段文字 | `villageBook` 子型開啟村莊書 GUI；其餘子型開 parchment GUI；每文化各有 villagers/buildings/items/complete 四份 + sadhu |
| `ItemSummoningWand`（`summoningWand`） | 召喚法杖：核心建村工具 | 右鍵（client 端）依目標：站立告示牌→匯入建築；上鎖箱→不動作；其他→`ClientSender.summoningWandUse`（建村/造建築）|
| `ItemNegationWand`（`negationWand`） | 否定法杖：管理/匯出工具 | 右鍵告示牌（client）→匯出建築藍圖；否則 server 端找 30 格內 townhall→開否定法杖 GUI（解散/管理）|
| `ItemBrickMould`（`brickmould`） | 磚模：消耗 1 土 + 1 沙，就地放出泥磚方塊 | 可損耗工具（maxDamage 128）|
| `ItemTapestry` | 掛毯/雕像/聖像：右鍵牆面生成 `EntityMillDecoration` 裝飾實體 | norman tapestry / indian/mayan statue / byzantine icon 三尺寸 |
| `ItemClothes`（`clothes`） | 拜占庭布料，有子型（wool/silk）與優先級 | `getClothPriority` 供村民著裝邏輯用 |
| `ItemAmuletVishnu/Yddrasil/Alchemist` | 神祇護身符（被動，由其他子系統判讀） | 本身只是註冊 + 貼圖 |
| `ItemAmuletSkollHati`（`skoll_hati_amulet`） | 主動護身符：右鍵切換日/夜（推進世界時間），每次損耗 1 耐久（除非 `MLN.infiniteAmulet`）| 唯一有主動世界效果的護身符 |
| `ItemMayanQuestCrown` | Mayan 任務獎勵頭盔，**自動附魔**（呼吸/水下挖掘/保護） | `IItemInitialEnchantmens`，在 onItemUse/onUpdate 反覆套用附魔 |
| `ItemMillenaireSword/Axe/Pickaxe/Shovel/Hoe` | 文化工具武器（norman/mayan/tachi/byzantine…），自訂 ToolMaterial | sword 可帶 `knockback` 自動附魔（`IItemInitialEnchantmens`）|
| `ItemMillenaireBow`（`yumiBow`） | 自訂弓：`speedFactor`（箭更快）、`damageBonus`（額外傷害）、多階拉弓貼圖 | onPlayerStoppedUsing 整段複製自原版 ItemBow |
| `ItemMillenaireArmour` | 各文化盔甲套裝（norman/japanese guard/warrior blue+red/byzantine） | 用 `ArmorMaterial` 自訂，`getArmorTexture` 大量 if 對應貼圖 |

自訂材質：`TOOLS_norman`、`TOOLS_obsidian`（工具），`ARMOUR_norman/japaneseWarrior/japaneseGuard/byzantine`（盔甲），皆以 `EnumHelper.addToolMaterial/addArmorMaterial` 動態建立——26.2 改用 `ToolMaterial` / `ArmorMaterial` 資料定義。

### 2.3 Entity（實體）

註冊於 `registerBlocksItemsEntities()`（行 660-679），用 `EntityRegistry.registerModEntity`（追蹤距離 64、更新頻率 3）：

| 實體類別 | 名稱 | 意圖 |
|---|---|---|
| `MillVillager.MLEntityGenericMale` | `GENERIC_VILLAGER` | 泛型男性村民外觀基底 |
| `MillVillager.MLEntityGenericAsymmFemale` / `MLEntityGenericSymmFemale` | 女性村民（不對稱/對稱貼圖） | 對應不同貼圖配置 |
| `MillVillager.MLEntityGenericZombie` | `GENERIC_ZOMBIE` | 村民死亡/被襲後的殭屍村民 |
| `EntityMillDecoration` | `ml_Tapestry` | 掛毯/雕像/聖像裝飾實體（類 painting）；更新頻率極低（100000）|
| `EntityTargetedGhast` | `MillGhast` | 導向型惡魂（見 §5）|
| `EntityTargetedBlaze` | `MillBlaze` | 導向型烈焰人 |
| `EntityTargetedWitherSkeleton` | `MillWitherSkeleton` | 導向型凋零骷髏 |

> 真正的村民邏輯（職業、AI、商人庫存 `merchantSells`）在 `MillVillager` 與 `common/goal/**`，屬其他子系統；本子系統只記其**外觀類別與註冊**。

### 2.4 BlockEntity（TileEntity）

- `TileEntityMillChest`（含內部 `InventoryMillLargeChest`）：上鎖箱倉庫；村莊資源存量實際存放處。
- `TileEntityPanel`：告示牌面板資料（`panelType` 1-13：村圖/戶籍/構造/工程/受控工程/房屋/資源/檔案/村圖/軍事/交易品/旅店訪客/市場商人/受控軍事），存 `buildingPos` 指回所屬建築。
- `EntityMillDecoration`：嚴格說是 Entity 不是 TileEntity，但行為類似 painting。

---

## 3. 特殊互動物品意圖（重寫時注意互動流向）

關鍵設計：**法杖的右鍵動作刻意在 client 端起手**（`onItemUseFirst` 在 client 判斷後再用自訂封包送 server），因為要先讀 client 端的告示牌文字/座標。

- **Summoning Wand（召喚法杖）= 建村核心**
  - 對「站立告示牌」→ `ClientSender.importBuilding`（匯入單棟建築藍圖）。
  - 對上鎖箱 → 不動作（避免誤觸）。
  - 對其他方塊 → `ClientSender.summoningWandUse(pos)` → server `GUIACTION_SUMMONINGWANDUSE` → 開「建新村 / 造新建築」流程。
- **Negation Wand（否定法杖）= 管理/匯出**
  - 對站立告示牌（**client 端**）→ `BuildingPlan.exportBuilding`（把現場結構匯出成藍圖，含 import 權限檢查）。
  - 否則 server 端：掃 30 格內所有 townhall，若箱已鎖→提示已鎖；否則開 `displayNegationWandGUI`（解散村莊/拆除建築）。
- **Parchment（羊皮卷圖鑑）**：每文化四份（villagers/buildings/items/complete）+ sadhu + 通用三份；`villageBook` 子型用 itemDamage 當村莊索引，開「村莊書」GUI（會檢查 chunk 是否載入、townhall 是否 active）。重寫要保留「文字內容資料驅動」設計（`MLN.getParchment(id)`）。
- **村莊卷軸（`parchmentVillageScroll`）**：村長給玩家的村莊記事。
- **Skoll-Hati 護身符**：唯一主動改世界狀態的護身符（切換日夜 + 損耗耐久）。
- **Mayan Quest Crown**：戴上自動附魔，屬任務獎勵；重寫要保留「onUpdate 反覆套用附魔」以防玩家用鐵砧洗掉。
- **Brick Mould**：低階建材生產工具（土+沙→泥磚），是早期玩家自助蓋村的入門物。
- **Tapestry / Statue / Icon**：右鍵牆面生成裝飾實體，`onValidSurface` 檢查同 painting。

> Debug/Dev 工具：`DevModUtilities`（`toggleAutoMove`、`testPaths`）透過 `PACKET_DEVCOMMAND` 觸發；非玩家正式物品，但網路層有對應封包，重寫時可選擇性保留。

---

## 4. 貨幣與交易機制意圖（最重要的子系統之一）

### 4.1 Denier 貨幣模型

- 三種物品幣：`denier`（銅）、`denier_argent`（銀）、`denier_or`（金）。
- **64 進位換算**：`1 銀 = 64 銅`、`1 金 = 64 銀`。整數「總額」與三幣面額互轉公式見 `ItemPurse.setDeniers` 與 `MillCommonUtilities.changeMoney`：
  ```
  denier        = total % 64
  denier_argent = (total - denier) / 64 % 64
  denier_or     = (total - denier - denier_argent*64) / (64*64)
  ```
- **Purse（錢袋）**：把硬幣壓成 NBT 上的三個整數，避免背包被硬幣塞滿；右鍵在「吸入全部硬幣」與「倒出硬幣到背包」之間切換。
- `MillCommonUtilities.countMoney(inventory)` 把背包內硬幣加總為單一 int；`changeMoney(inventory, delta, player)` 做加減並**自動找零/補幣**。
  - 若背包含 purse：金額變動直接寫進 purse NBT（優先用 purse 當錢包），可為負溢位處理。
  - 否則：重算三幣應有數量，多退少補實體硬幣。
  - 副作用：拿到金幣會給成就 `cresus`。
  - 注意 `changeMoneyObsolete` 為舊版殘留，重寫應只移植 `changeMoney`。
- **client/server `rand` 旗標**：purse NBT 寫入 `ML_PURSE_RAND = isRemote?0:1`，用來強制 client 重繪/同步。重寫到 26.2 的 component 化 NBT 時可去掉這個 hack。

### 4.2 商店交易流程（`ContainerTrade.java`）

兩種交易對象：
1. **固定商店建築**（`ContainerTrade(player, Building)`）：分「賣給玩家（selling）」與「向玩家買（buying）」兩排槽位。
2. **流浪商人 `MillVillager`**（`ContainerTrade(player, merchant)`）：只賣，庫存取自其房屋 `countGoods`。

槽位是**虛擬槽**（`TradeSlot` / `MerchantSlot`，繼承 `Slot` 但 stackLimit=0、不能真的放東西）；`getStack()` 即時回傳「庫存數量（上限 99）」當顯示。

**價格來源（無動態供需曲線）**：
- 每個 `Goods` 帶 `sellingPrice` / `buyingPrice`（基礎價）。
- `getBasicSellingPrice(shop)` / `getBasicBuyingPrice(shop)`：若該村莊類型（`villageType.sellingPrices` / `buyingPrices`）對此物有覆寫價，用覆寫值；否則用 Goods 基礎價。
- `Building.computeShopGoods(player)` 在開店時把每個 good 與其「basic price」放進 `shopSells` / `shopBuys`（依玩家名分別存）。
- `getCalculatedSellingPrice/BuyingPrice` 最終就是查這兩張表。
- **結論：價格是靜態設定值（村莊類型可覆寫），沒有依庫存波動的供需算法，也沒有依聲望打折。** 聲望只當「能否購買」的門檻，不影響單價。

**交易執行（`slotClick`，server 權威）**：
- 點擊量：預設 1，右鍵 8，shift 64。
- 玩家買（selling slot）：
  - `isProblem()` 把關：缺庫存 / 缺 requiredTag 裝備 / **聲望 < `good.minReputation`** / 錢不夠。
  - 依玩家現金與庫存夾擠實際成交量 → `putItemsInChest`（給物品）→ `changeMoney(-price*qty)`（扣錢）→ 非 autoGenerate 則 `building.takeGoods`（扣村莊庫存）。
  - **副作用：`adjustReputation(player, price*qty)`（花錢買 = 增聲望）+ `adjustLanguage`（增該文化語言熟練度）。**
- 玩家賣（buying slot）：
  - 把背包物品 `storeGoods` 進村莊 → `getItemsFromChest`（扣背包）→ `changeMoney(+price*qty)`（給錢）→ `adjustReputation` + `adjustLanguage`。
- 流浪商人：同上但庫存取自 merchant 房屋，且只增 language（不增聲望）。

**交易上限/庫存**：
- 顯示上限 99/槽；單次成交量受「玩家現金」「玩家背包數量」「村莊庫存」三者夾擠。
- `MIN_REPUTATION_FOR_TRADE = -16*64 = -1024`：聲望低於此值，村莊根本不派商人 / 不開放交易（`checkSeller`）。
- `autoGenerate` 物品（如某些基礎貨）不受村莊庫存限制（無限供應）。
- `requiredTag`：某些賣品需建築具備特定 tag（如有對應工坊）才賣。

> 重寫建議：把「基礎價 + 村莊類型覆寫價」「聲望門檻」「language 熟練度副作用」「庫存夾擠」四件事明確切出；虛擬槽位 UI 在 26.2 應改為自訂 `ScreenHandler` + payload，不要沿用 stackLimit=0 的 hack。

---

## 5. 自訂實體意圖

### 5.1 村民與裝飾（外觀殼層）
`MillVillager` 的四個外觀子類別（男/不對稱女/對稱女/殭屍）只是貼圖與註冊差異，行為共用。`EntityMillDecoration` 是掛毯/雕像/聖像的 painting 類實體。皆 `registerModEntity` 註冊。

### 5.2 導向型敵對怪（Mayan 金字塔任務圍城）
`EntityTargetedGhast` / `EntityTargetedBlaze` / `EntityTargetedWitherSkeleton` 三者皆繼承原版怪，加上一個 `target`（`Point`）欄位（存讀 NBT `targetPoint`）：
- **共同意圖**：被 `SpecialQuestActions`（Mayan 金字塔「圍城」任務，行 329-364）成批生成，朝指定目標點（金字塔頂）飛/移動，製造圍攻壓力。
- `canDespawn()` 一律 `false`（任務怪不可自然消失）。
- Ghast/Blaze 覆寫 `updateEntityActionState`：距目標 > 20 格時把 waypoint/motion 導向 target；Blaze 還做 `isCourseTraversable` 碰撞檢查。
- Blaze `isWet()=false`（雨中不受傷，確保圍城持續）。
- Wither Skeleton 較單純（只加 target NBT 與不可 despawn）。

> 重寫：26.2 用 `Goal`/`Brain` 系統，這三者可改成「帶 target 的自訂 NavigationGoal」；保留「不 despawn + 朝點移動 + 雨中免傷」三個意圖即可。沒有「守衛」「盜匪」的獨立實體類別——守衛/襲擊者其實是 `MillVillager`（`isRaider` 旗標 + 軍事 goal），不在本子系統。

---

## 6. 網路封包模型意圖

單一自訂 channel：`"millenaire"`（`ServerReceiver.PACKET_CHANNEL`），用 Forge `FMLEventChannel` + Netty `ByteBuf` 手寫序列化。封包第 1 byte = packet type，其餘自訂讀寫（`StreamReadWrite` 提供 nullable Point / string list 等 helper）。

### 6.1 Client → Server（`ServerReceiver.onPacketData`，行 96-142）
高層只有少數幾種，其中 `PACKET_GUIACTION` 再用第二 byte 細分動作：

| 封包 | 意圖 |
|---|---|
| `PACKET_GUIACTION`(200) | 萬用 GUI 動作分派器，內含 ~25 種 `GUIACTION_*`：村長造建築/選作物/文化控制/外交/卷軸、任務完成/拒絕、建新村、否定法杖、召喚法杖、匯入建築、開上鎖箱、神廟附魔、新工程、受控工程開關/遺忘、雇用/續雇/解雇村民、軍事外交/突襲/取消突襲 |
| `PACKET_VILLAGELIST_REQUEST`(201) | 請求村莊清單 |
| `PACKET_DECLARERELEASENUMBER`(202) | client 回報版本號 |
| `PACKET_MAPINFO_REQUEST`(203) | 請求某 townhall 的地圖資訊 |
| `PACKET_VILLAGERINTERACT_REQUEST`(204) | 右鍵村民的特殊互動 |
| `PACKET_AVAILABLECONTENT`(205) | client 回報已有的內容包/語言，server 補差異（內容同步協商）|
| `PACKET_DEVCOMMAND`(206) | 開發者指令（auto-move / test-path）|

### 6.2 Server → Client（`ServerSender` 寫、`ClientReceiver` 收，行 73-101）

| 封包 | 同步內容 |
|---|---|
| `PACKET_BUILDING`(2) | 建築完整狀態（含 `shopSells`/`shopBuys` 價格表，見 `Building` 行 5219-5236）|
| `PACKET_SHOP`(11) | 商店交易品/價格更新 |
| `PACKET_VILLAGER`(3) | 村民狀態 |
| `PACKET_MILLCHEST`(5) | 上鎖箱內容更新 |
| `PACKET_MAPINFO`(7) | 村莊地圖 |
| `PACKET_VILLAGELIST`(9) | 村莊清單 |
| `PACKET_SERVER_CONTENT`(10) | 缺漏的內容包/語言字串下發 |
| `PACKET_PROFILE`(101) | 玩家檔案（聲望、tag、language 熟練度…）|
| `PACKET_QUESTINSTANCE`(102) / `...DELETE`(103) | 任務實例同步/刪除 |
| `PACKET_OPENGUI`(104) | 通知 client 開哪個 GUI（交易/雇用/面板/村長/任務/村莊書/否定法杖/新村…）|
| `PACKET_PANELUPDATE`(106) | 告示牌面板文字內容 |
| `PACKET_ANIMALBREED`(107) | 動物繁殖效果 |
| `PACKET_VILLAGER_SENTENCE`(108) / `PACKET_TRANSLATED_CHAT`(100) | 村民台詞 / 翻譯聊天訊息 |

**同步模型意圖**：server 為唯一權威（價格、庫存、聲望、交易結算都在 server 算）；client 只負責顯示與「起手互動 + 送意圖封包」。`PACKET_OPENGUI` 把「該開什麼 GUI」也交給 server 決定，client 不自行決定開窗。內容包（建築藍圖、村民/村莊定義、多語字串）走「client 宣告已有 → server 補差異」的協商式同步（`PACKET_AVAILABLECONTENT` ↔ `PACKET_SERVER_CONTENT`），這對單機/多人共用同一套資料很關鍵。

> 26.2 重寫：每個 packet type 對應一個 `CustomPayload` record + `PayloadTypeRegistry` 註冊 + `ClientPlayNetworking/ServerPlayNetworking` handler。`PACKET_GUIACTION` 的「第二 byte 子分派」建議拆成多個獨立 payload 類型，別再用 magic int。手寫 `ByteBuf` 序列化改用 `PacketCodec`。

---

## 7. 與原版方塊/物品的相依（意圖）

- **建材替換**：`MLN.stopDefaultVillages` 時把 `MapGenVillage.villageSpawnBiomes` 清空，停掉原版村莊（避免與 Millénaire 村莊衝突）。
- **配方/熔煉相依**（`registerBlocksItemsEntities` 行 708-725）：
  - 生羊肉→熟羊肉熔煉。
  - `stone_decoration` meta1（生泥磚）→ meta0（熟磚）熔煉。
  - 咖哩 = 稻+薑黃（+雞）；basic wine = 6 葡萄；masa/wah = 玉米(+雞)；拜占庭磁磚半磚/混石磚配方（用 `Blocks.stone`）；path → pathSlab（16 種 meta）。
- **freeGoods 清單**（`Goods.java` 行 805-831）：村民可無償取用的原版方塊（土、水、樹苗、花、草、黏土、釀造台、樹葉、蛋糕）+ Millénaire 的 path/earth_decoration——建村時不算進資源成本。
- **磚模**消耗原版 `Blocks.dirt` + `Blocks.sand`。
- **作物**繼承 `BlockCrops`、種子實作 `IPlantable`、drop 原版風格。
- **弓**消耗原版 `Items.arrow`、套用原版附魔（power/punch/flame/infinity）。
- **盔甲/工具**用原版 `ArmorMaterial.DIAMOND`（Mayan crown）、`ToolMaterial.IRON`（tachi/byzantine mace）。
- 商店把原版物品（如雞、各種方塊）都能當交易 good（goods 由 `itemlist.txt` 對應 item registry name → `InvItem`）。

---

## 8. 與舊 MC API 耦合、需在 26.2 重新推導的部分

- **整個註冊模型**：`@Mod(cpw.mods.fml)`、`GameRegistry.registerBlock/registerItem`、`EntityRegistry.registerModEntity`、`AchievementPage` → 改為 Fabric `Registry.register` + `Registries.BLOCK/ITEM/ENTITY_TYPE`，成就改 Advancements。
- **反射批次註冊物品**（`Mill` 欄位掃描）必須改成顯式註冊清單。
- **方塊座標 API**：`world.getBlock(i,j,k)` / `getBlockMetadata` / `setBlockMetadata` / `onItemUseFirst(... i,j,k,l ...)` → `BlockPos` + `BlockState`，metadata 子型改為 `BlockState` properties 或拆成多方塊。
- **貼圖**：`IIcon` / `registerIcons` / `IIconRegister` → JSON model + texture atlas；`getArmorTexture` 一堆 if → armor material 貼圖定義。
- **方塊基底類別**：`BlockChest`/`BlockSign`/`BlockCrops`/`BlockSlab` 的 1.7 內部方法（`func_149951_m`、`func_149953_o`、`func_149865_P`）需對應 26.2 等價物或自繪。
- **TileEntity → BlockEntity**：`TileEntityMillChest`/`TileEntityPanel` 改 `BlockEntity` + 自訂 `ScreenHandler`。
- **實體**：`updateEntityActionState`、`waypointX/Y/Z`、`courseChangeCooldown`（Ghast 內部）→ 改 Goal/Brain；`registerModEntity` → `EntityType.Builder`。
- **NBT**：`stackTagCompound`、`NBTTagCompound` → `NbtCompound` / Data Components（26.2 物品 NBT 已大幅 component 化，purse 與 denier 計數應改 component）。
- **貨幣/食物效果**：`Potion.xxx.id`（int potion id）→ `StatusEffect` registry entry；`ItemFood`/`setAlwaysEdible`/`setPotionEffect` → `FoodComponent`。
- **網路**：`FMLEventChannel` + `S3F/C17PacketCustomPayload` + 手寫 `ByteBuf` → `CustomPayload` + `PacketCodec` + Fabric networking API。
- **區塊強制載入**：`ForgeChunkManager.requestTicket/forceChunk`（`BuildingChunkLoader`）→ Fabric `ServerWorld.setChunkForced` / chunk ticket API。
- **配方**：`GameRegistry.addRecipe/addShapelessRecipe/addSmelting` → JSON recipes 或 datagen。
- **村莊 worldgen**：`MapGenVillage.villageSpawnBiomes` 清空 → 26.2 用 structure/biome tag 機制處理。

---

## 9. 值得保留的微妙設計（重寫時別丟）

1. **三幣 64 進位 + 錢袋壓縮**：denier↔總額換算與 purse NBT 壓縮是貨幣體驗核心；`changeMoney` 的「優先用 purse、找零補幣」邏輯要原樣保留。
2. **聲望是門檻不是折扣**：交易單價恆定，聲望只決定「能不能買某物（`minReputation`）」與「會不會被服務（`MIN_REPUTATION_FOR_TRADE`）」。這是刻意的簡單經濟設計，別誤加供需波動。
3. **交易副作用 = 聲望 + 語言熟練度**：每次成交都 `adjustReputation` 與 `adjustLanguage`，把「跟村莊做生意」綁進長期進度系統。
4. **autoGenerate / requiredTag / 村莊類型覆寫價**：三個讓內容資料能微調每村經濟的旋鈕。
5. **server 權威 + OPENGUI 由 server 決定**：防作弊與一致性的關鍵；client 端法杖只「起手讀本地告示牌座標」再送意圖。
6. **內容包協商同步**（available↔server content）：讓自訂村莊/語言包在多人環境自動補齊。
7. **導向怪三旗標**（不 despawn / 朝點移動 / 雨中免傷）：金字塔圍城任務的體驗要點。
8. **磚模就地造磚**：低成本建材入門路徑，鼓勵玩家自助蓋村。
9. **freeGoods 清單**：建村資源計算時排除「自然可得方塊」，避免經濟系統被泥土卡住。

---

## 10. 未決問題

1. **價格表的實際數值與村莊類型覆寫**來自內容資料（`itemlist.txt`、村莊類型定義），需與「goods/shops 內容代理」對齊：`sellingPrice/buyingPrice/foreignMerchantPrice/minReputation/reservedQuantity/targetQuantity` 各欄語意與來源檔。
2. **`reservedQuantity` / `targetQuantity`** 在本子系統未見直接用於交易夾擠（交易只用 `countGoods` 即時庫存）；推測供村民生產/採購 AI 用，需確認是否影響玩家可購量。
3. **`adjustLanguage` 的具體成長公式**在 `Profile`（其他子系統），需確認其與交易量的關係以決定是否屬本子系統。
4. **流浪商人 `merchantSells` 的庫存與補貨**機制（房屋 `countGoods`）細節未在本子系統展開。
5. **`EntityMillDecoration` 的型別常數**（NORMAN_TAPESTRY / INDIAN_STATUE / MAYAN_STATUE / BYZANTINE_ICON_*）對應的渲染尺寸與貼圖，需從 `EntityMillDecoration`（其他子系統/渲染）取得。
6. **purse 的 `ML_PURSE_RAND` client/server 旗標**是否仍需要——26.2 component 同步機制下可能可移除，待驗證。
7. **`changeMoneyObsolete`** 確認可丟棄（疑似舊版相容殘留）。
8. **Achievements**（`MillAchievements`，如 `cresus`）在 26.2 對應 Advancements 的設計，未在本文展開。
