# 意圖分析書 05：GUI／客戶端、任務、傳說、玩家進度循環

> 子系統範圍：`client/gui/**`（17 檔）、`client/**`、`common/network/**`、`common/Quest.java`、`common/GuiActions.java`、`common/TileEntityPanel.java`、`common/UserProfile.java`（聲望/任務狀態）、內容資料 `millenaire/quests/**`、`millenaire/cultures/*/`、`millenaire/languages/*/*_reputation.txt`。
>
> 本書描述**設計意圖**，不是程式碼說明。所有 Java 在 Fabric/MC 26.2 全部重寫；美術與內容資料（quests DSL、reputation 文本）復用。識別字保留英文。

---

## 1. 用途與玩家可見角色

Millénaire 把原版「靜態 NPC 村莊」改造成**活的、會自行成長、有文化與政治的村莊生態**。本子系統是玩家與該生態的**全部互動介面**：玩家看不到村莊的內部模擬（建造佇列、資源流、pathing），只透過一組 GUI 與招牌（panels）讀取「人類可理解的狀態摘要」，並透過有限的按鈕對村莊下達指令。

玩家在本子系統中的可見角色，依進度遞進：

1. **陌生人（Stranger）**：發現村莊、右鍵村民看到名字/職業（母語＋遊戲語言）、讀招牌了解村莊。
2. **訪客/貿易者**：透過貿易與完成小任務累積**聲望（reputation）**。
3. **村莊之友**：聲望解鎖購買建築、學習作物、買捲軸、雇傭村民、外交斡旋。
4. **文化領袖（Natural leader）**：在某文化達到最高聲望階並「取得控制權（culture control）」後，可用召喚法杖（summoning wand）在金磚上**建立自己控制的村莊**。
5. **村莊統治者**：對受控村莊（controlled village）下達建築、升級、外交、突襲（raid）指令。

貫穿全程還有一條獨立的**世界任務線（world quest）**——三條長篇敘事任務（sadhu / alchemist / fallenking），是傳說（lore）的主要載體。

設計核心意圖：**進度由「聲望」這條數值主軸驅動，所有 GUI 都圍繞「我現在的聲望能做什麼」來組織資訊。**

---

## 2. 玩家進度循環地圖

```
[發現村莊]
   │  右鍵村民 → 名字/職業；讀招牌(panels) → 村莊總覽/人口/建築/軍事
   ▼
[互動：貿易 + 任務]
   │  GuiTrade 買賣貨物（價格隨供需浮動）→ 賺 denier、間接送物資
   │  村民頭上出現任務 → GuiQuest 接受/完成 → 獎勵含 reputation
   ▼
[累積聲望 reputation]  （每文化、每村莊各自一條；存在 UserProfile）
   │  聲望階梯（norman 範例）：
   │   0 Stranger → 4*64 Known Face → 16*64 Regular → 64*64 Favourite Trader
   │   → 2*64*64 Friend → 8*64*64 One of us → 32*64*64 Natural leader
   │  負向：-64 Nuisance → -4*64 Unpleasant → -16*64 Public enemy
   ▼
[聲望解鎖能力]（與村長對話 GuiVillageHead）
   │  買建築(各 plan 有 reputation 門檻) / 買 village scroll(2*64*64)
   │  學作物 crop(2*64*64) / 外交 praise·slander(需 diplomacy points)
   │  雇傭村民 GuiHire(64*64) / 取得文化控制權(32*64*64)
   ▼
[取得文化控制權 culture control]  →  設 player tag: CULTURE_CONTROL+culture
   ▼
[建立/控制村莊]
   │  summoning wand 對金磚 → GuiNewVillage 選 playerControlled 村型
   │  受控村莊：GuiControlledProjects(建築升級管理) + GuiControlledMilitary(外交/突襲)
   │  summoning wand 對受控村莊空地 → GuiNewBuildingProject 下新建築
   ▼
[世界任務線 world quest]（與上面平行，獨立敘事）
       sadhu(hindi 15步) / alchemist(norman 13步) / fallenking(mayan 10步)
       透過 player tag 鏈接每一步；完成 → 成就 forbiddenknwoledge
```

關鍵設計取捨：**聲望是「許可制」而非「等級制」**——它不給玩家屬性加成，而是逐步開放 GuiVillageHead 上的按鈕。重做時要保留「同一個 GUI 隨聲望長出更多選項」這種漸進揭露感。

---

## 3. GUI 畫面清單與資訊架構

所有文字型 GUI 繼承抽象基底 `GuiText`（重做時應對應一個共用的「分頁文字頁面 + 行內按鈕/圖示/輸入框」widget 框架）。容器型 GUI（貿易、祭祀）繼承 MC 的 GuiContainer（有 slot）。

### 3.1 GuiText 框架（基礎設施，務必重做）
- **資料模型**：`List<List<Line>>` = 多頁，每頁多行。`Line` 可承載：純文字、1–3 個並排按鈕、一個文字輸入框、或一排物品圖示（含 tooltip 額外文字）。
- **自動排版**：`adjustText()` 自動斷行（依像素寬）並分頁（依頁高），尊重 `canCutAfter` 不在區塊中間切頁。
- **顏色標籤 DSL**：行內用 `<darkgreen>`、`<lightred>`、`<shadow>` 等 tag，`interpretTags()` 轉成 MC `§` 格式碼。重做時這套 tag 語言要保留，因為內容文本與聲望描述都靠它上色。
- **底部翻頁**：左下/右下點擊區換頁，顯示 `n/總頁`。
- 共用按鈕：`HELPBUTTON`、`CHUNKBUTTON`、`CONFIGBUTTON`（特殊 sentinel 字串行轉成按鈕）。

### 3.2 玩家進度核心畫面

| Screen | 觸發 | 資訊架構（呈現什麼） | 可下達的指令 |
|---|---|---|---|
| **GuiVillageHead**（與村長對話）| 右鍵 townhall 村長 | 3 頁。第1頁：村長名/職業、村莊名、**你的聲望階標籤＋描述**（依聲望上色）、可購建築清單（各帶狀態：已建/已請求/聲望不足/差多少錢/可購買）、village scroll、可學作物、文化控制權。第2頁：本村與其他村的**關係列表**＋外交點數。第3頁：關係系統說明。| 買建築、買捲軸、學作物、取得控制權、praise/slander 他村 |
| **GuiTrade**（貿易）| 右鍵商店建築或流動商人 | GuiContainer。左：村莊「我們賣」＋「我們買」兩區，可上下捲動多列；右：玩家背包。每個 slot hover 顯示**動態價格**（顏色依划算度）與「問題」原因（如供應不足）。| 拖曳物品買賣；? 按鈕開貿易說明 |
| **GuiQuest**（任務對話）| 右鍵身上有任務的村民 | 1 頁。村民名/職業、本步驟描述文字（含 `$name`/方向/距離變數展開）、若條件不足顯示缺什麼。| 接受/拒絕（首步）、繼續/關閉（後續步）、completeStep |
| **GuiHire**（雇傭）| 右鍵可雇村民 | 村民名、雇傭狀態/剩餘時間、生命/攻擊/費用。需聲望 64*64＋足夠錢。| hire / extend(+24000 tick) / release |
| **GuiPujas**（祭祀/獻祭）| 右鍵神廟 | GuiContainer。獻祭物 slot＋金錢 slot＋工具 slot；進度條（puja 進度、獻祭進度）；右側一排「附魔目標」按鈕（hindi=puja 直列、mayan=sacrifice 3列網格，材質不同）。| 放入獻祭物、選擇要附魔的目標 |

### 3.3 招牌（panels）與地圖類

| Screen | 用途 |
|---|---|
| **GuiPanelParchment** | 雙用：(a) 顯示招牌/羊皮紙文字頁（panel vs parchment 兩種背景材質）；(b) 渲染**村莊地圖**（村內建築/villager 著色點陣＋hover 詳情）或**chunk 地圖**（已載入區塊/村莊/強制載入 chunk）。 |
| **TileEntityPanel**（資料產生器，非 GUI）| 村莊內實體招牌的內容來源。`generateXXX()` 系列產生各類文字頁：etatCivil(人口戶籍)、constructions(已建建築)、projects(建築計畫)、house(住戶)、resources(資源/建材進度)、archives(個人檔案：父母配偶/現職)、villageMap(地圖圖例)、military(攻防/戰士/突襲史)、tradeGoods(進出口)、innVisitors(旅店訪客紀錄)、marketMerchants(市集商人)、**generateSummary**(村莊總覽：人口分布/當前建造目標/建材需求進度)。 |

### 3.4 控制村莊管理面板

| Screen | 資訊架構 | 指令 |
|---|---|---|
| **GuiControlledProjects** | 受控村莊全部建築計畫列表：名稱(變體 A/B)、目前等級/最高等級、距離方向、住戶數。| 允許/禁止自動升級（per building）、取消建築（僅限無人居住者，townhall 不可） |
| **GuiControlledMilitary** | 與已知各村的關係列表＋顏色；後接 military panel 各頁（攻防力、戰士、突襲史）。| 設關係(good/neutral/bad)、發起突襲(raid)、取消突襲 |
| **GuiNewVillage** | 選擇要建立的村型；先列出玩家在各文化的領袖狀態（leader/not leader）；只列出 `spawnableVillages`（含 playerControlled 標記）。| 選村型 → 建立（自訂中心者轉 GuiCustomBuilding 確認）|
| **GuiNewBuildingProject** | 在受控村莊空地選新建築（標準 plan 或 custom building）；顯示住戶數。| 下達新建築專案 |
| **GuiCustomBuilding** | 確認/編輯自訂建築（半徑、招牌數）；用於 custom 中心村莊與既有自訂建築。| 確認建立 / 更新資源登記 |
| **GuiNegationWand** | 摧毀村莊的二次確認對話。| 確認/取消摧毀（成就 scipio）|

### 3.5 其他
- **GuiMillChest**：上鎖的村莊箱子；玩家聲望不足時鎖定（不可點擊、不可操作，只能關閉），等同 GuiChest 加權限門。
- **GuiHelp**：分頁說明書（`help.tab_*`）。
- **GuiConfig**：mod 設定頁。
- **DisplayActions**：所有 GUI 的客戶端開啟入口（statics）；伺服器透過 `PACKET_OPENGUI` 指示客戶端開哪個。重做時這層是「server 要求 client 開 GUI X」的派發點。

---

## 4. 聲望系統（reputation）

### 意圖
聲望是進度主軸：一個整數，**每個玩家對每個村莊各自一條**，存在 `UserProfile`（綁玩家，非綁世界）。同時聚合成「每文化聲望」（`getCultureReputation`）供領袖判定。它不是經驗值，而是**信任度/許可額度**。

### 數值與階梯
- 上限 `MAX_REPUTATION = 8*64*64 = 32768`。負值代表敵意。
- 階梯由內容資料定義：`millenaire/languages/<lang>/<culture>_reputation.txt`，每行 `level;Label;Description`。level 用 `a*64*64` 等運算式（重做需保留這個算式解析）。描述文本含 `$name` 變數與顏色 tag。
- 階梯查找：取「不超過目前聲望的最高一階」。GUI 顯示時另有硬編碼顏色分界（VillageHead 用 8*64*64=綠、64*64=藍、<0=紅）。

### 累積與影響
- **累積**：完成任務步驟 `rewardReputation`（並按 rep/32 給少量經驗，上限 16）；某些建築/獻祭行為。**流失**：任務逾時/拒絕的 `penaltyReputation`、突襲。
- **影響（門檻常數，見 GuiActions）**：
  - 貿易：`MIN_REPUTATION_FOR_TRADE` 才開放上鎖箱貿易。
  - 買建築：每個 `BuildingPlan.reputation` 各自門檻。
  - village scroll：`VILLAGE_SCROLL_REPUTATION = 2*64*64`。
  - 學作物：`CROP_REPUTATION = 2*64*64`。
  - 雇傭：`64*64`。
  - 文化控制權：`CULTURE_CONTROL_REPUTATION = 32*64*64`（最高階）。
- **外交點數（diplomacy points）**：與聲望分離的另一資源（`UserProfile.getDiplomacyPoints(townhall)`），praise/slander 各消耗 1 點，效果隨聲望以對數加權放大（見 `villageChiefPerformDiplomacy`）。

### 與村莊關係（relation）區別
村莊「之間」另有 relation 值（`RELATION_DECENT=30/VERYGOOD=70/MAX=100/MIN`）。玩家透過 praise/slander 或受控村莊的軍事面板調整 relation。**聲望=玩家↔村莊；relation=村莊↔村莊。** 重做時兩者勿混。

---

## 5. 任務系統（quest）

### 整體意圖
任務是「聲望的產生器」與「lore 的載體」。分兩類：(a) **隨機小任務**（送貨/採集，可重複，文化基礎包）；(b) **世界任務線**（world quest，三條長篇線性敘事，靠 player tag 嚴格串接）。

### 資料模型（`Quest` / `QuestStep` / `QuestVillager` / `QuestInstance`）
- **Quest**（一個 .txt = 一個 quest 模板）：`key`、`minreputation`、`chanceperhour`（每小時觸發機率）、`maxsimultaneous`、required/forbidden global tag、required/forbidden player tag、一組 `definevillager` 角色、有序 `steps`。
- **QuestVillager**（角色定義）：`key`、可接受的 villager `type`（culture/type，可多個）、`relatedto`+`relation`（與另一角色的空間關係：`samevillage`/`samehouse`/`nearbyvillage`(<2000)/`anyvillage`）、required/forbidden quest tag。`testVillager` 排除已在任務中的村民。
- **QuestStep**：指定執行的 `villager`、`duration`（tick 上限，逾時=失敗）、required/reward goods、`rewardMoney`、`rewardReputation`、`penaltyReputation`、以及大量 tag 操作（成功/失敗各一組：villager tag / global tag / player tag 的 set/clear）、`bedrockbuilding`（成功時生成基岩孤建築）、`setactiondatasuccess`、`showrequiredgoods`。描述/標籤文字（label/description/description_success/description_refuse/description_timeup/listing）走語言檔。
- **QuestInstance**（執行中實例，存在 UserProfile）：綁定 quest 模板＋實際選中的 villagers（`QuestInstanceVillager` = townhall pos + villager id）＋currentStep＋計時。可序列化成單行字串存檔。

### 觸發
伺服器定時對每個 UserProfile 跑 `testQuest`：機率擲骰 → 檢查 maxsimultaneous 與 global/player tag 門檻 → 在聲望≥minreputation 的村莊中找符合的起始村民 → 再依 relation 規則補齊其餘角色 → 隨機選一組組合，建立 instance 並把每個 villager 註冊進 `profile.villagersInQuests`（villager id → instance）。村民頭上的任務標記即來自此 map。

### 目標／完成／獎勵
- 玩家右鍵任務村民 → `displayQuestGUI`（僅當該 villager 在 `villagersInQuests`）。
- `lackingConditions` 即時檢查：required goods 是否在背包（特例 `ANYENCHANTED`/`ENCHANTEDSWORD` 計數附魔物）、required/forbidden tag 是否滿足。不足則 GUI 禁用「完成」按鈕。
- `completeStep`：扣 required goods（轉給村民）、發 reward goods（塞背包，滿則丟地上並套用初始附魔）、發錢、發聲望＋經驗、加語言熟練度（QUEST_LANGUAGE_BONUS=50）、套用成功 tag、生成 bedrock building。然後 `currentStep++`；若到末步 → 給成就、`destroySelf`；否則重設計時並推送 instance 封包。
- `refuseQuest`/逾時：套用失敗 tag、扣 penaltyReputation、`destroySelf`。

### quest DSL 格式概要（重做需原樣解析這些 .txt）
```
# 檔頭（quest 級）
minreputation:3*64          # 支援 a*b*c 算式
chanceperhour:0.1
maxsimultaneous:1
requiredplayertag:sadhu_9_underwater     # 串接世界任務的關鍵
forbiddenplayertag:sadhu_10_underwaterglass
requiredglobaltag:... / forbiddenglobaltag:...

# 角色定義（quest 級，順序重要，第一個=起始村民）
definevillager:key=startvillager,type=norman/abbot
definevillager:key=loneabbot,type=norman/loneabbot,relatedto=startvillager,relation=nearbyvillage

# 步驟（依出現順序）
step:new
villager:startvillager          # 本步驟由誰執行
duration:48                     # tick 上限
requiredgood:book,2             # 需交付（good key 來自 goods.txt）
rewardgood:paper,1
rewardmoney:2*64
rewardreputation:2*64
penaltyreputation:2*64
showrequiredgoods:false         # 隱藏需求（神秘任務）
setplayertagsuccess:action_underwater_glass
steprequiredplayertag:action_underwater_glass_complete
settagsuccess:villagerKey,tagname        # villager-scoped tag
setglobaltagsuccess:pujas
bedrockbuilding:culture,villagetype
setactiondatasuccess:sadhuqueststatus,12
```
- 語言/描述文字鍵：`<questkey>_<stepindex>_<field>`（如 `abbotbooksdelivery_0_description`），由 `MLN.questString` 取。
- 註解 `//` 開頭。每行 `key:value`，value 內逗號分隔子欄位。

### 與聲望/lore 的關係
- 任務是聲望的主要正向來源；世界任務 step 也常給聲望。
- 世界任務線完全靠 **player tag 鏈** 線性推進（前一步 set 的 tag 是後一步的 requiredplayertag），並用 `setactiondatasuccess` 記錄進度數字供 lore/UI 顯示。`SpecialQuestActions` 定義一批「世界互動」tag（下潛取水樣 underwater_glass、到世界頂/底、鑽探到基岩 borehole、瑪雅圍城 mayansiege 等），由玩家在世界中的行為觸發 `_complete` tag，再讓任務步驟解鎖——這是 lore 與世界探索綁定的機制。

---

## 6. lore 與 panels（傳說/招牌呈現）

### 招牌（panels）意圖
村莊內建築旁的實體招牌（`TileEntityPanel` 繼承 TileEntitySign）是**村莊狀態的唯讀儀表板**。玩家右鍵招牌 → 伺服器產生對應 `generateXXX` 文字頁 → 推 `PACKET_PANELUPDATE` → 客戶端用 GuiPanelParchment 顯示。每種招牌 `panelType` 對應一類資訊（見 §3.3 表）。設計意圖是讓玩家「不打開任何選單」就能巡視村莊：戶籍、人口統計、建築進度、建材缺口、軍事攻防、貿易進出口、旅店/市集訪客紀錄、個人家譜檔案。

### 羊皮紙/卷軸（parchment）意圖
- **village scroll**（`Mill.parchmentVillageScroll`）：向村長購買，記錄某村座標索引，是玩家的「村莊筆記/導航」。
- GuiPanelParchment 以 `isParchment` 切換背景（招牌木紋 vs 羊皮紙），承載地圖（村莊地圖點陣＋圖例頁、chunk 地圖）。地圖著色語意（建築/水/危險/路徑/不可達/可達性紅藍）是重要 UX，重做需保留圖例對照。

### lore 載體
真正的「傳說敘事」主要不在固定文本檔，而是**藏在世界任務的 description 文字 + 對話（Dialogue）系統 + reputation 描述**裡，透過變數展開（`$name`、`$key_villagename$`、`$key_direction$`、`$key_distance$` 等）動態填入玩家名與村莊方位，營造「村民真的在跟你這個人講話、指引你去某個方位的村莊」的沉浸感。重做時 §5 的變數替換規則（`handleString`）是 lore 體驗的關鍵，必須完整移植。

---

## 7. 控制村莊（controlled village）的玩家權力

文化控制權（player tag `CULTURE_CONTROL+<culture>`）解鎖後，玩家可建立 `playerControlled` 村型。對受控村莊，玩家權力（皆經 GuiActions/網路）：

- **建築（GuiControlledProjects）**：逐建築允許/禁止自動升級；取消無人居住的建築（townhall 與有住戶者不可取消）。
- **新建築（summoning wand + GuiNewBuildingProject）**：在村莊半徑內空地下達新建築專案（標準或自訂）。伺服器 `testSpot` 驗證位置（可能回 CONSTRUCTION_FORBIDEN/LOCATION_CLASH/OUTSIDE_RADIUS/WRONG_ALTITUDE/DANGER/NOT_REACHABLE 並提示方向），通過則放置帶招牌的計畫。受控村莊只暴露 CENTRE/START/CORE 類建築供管理（見 generateProjects）。
- **外交與軍事（GuiControlledMilitary）**：對其他村莊設定 relation（good/neutral/bad）、發起突襲（raid，把 relation 拉到 -100 並排程）、取消尚未開始的突襲。一次只能有一個 raidTarget。
- **作物**：向村長學作物後設 `CROP_PLANTING+<crop>` tag，村莊才會種該作物。
- **建立/摧毀**：summoning wand 對金磚開 GuiNewVillage 建村；negation wand 摧毀村莊（GuiNegationWand 二次確認）。

設計意圖：玩家是「領主/施政者」，**下達高層指令，不直接擺方塊**；村莊模擬自行執行。受控村莊刻意比 AI 村莊權限受限（只管核心建築），保留村莊自主感。

---

## 8. client↔server 同步意圖

架構：自訂 channel `"millenaire"`，單一 byte 前綴分派封包類型（`ServerReceiver` 常數表）。**權威在伺服器**，客戶端持有夠用的鏡像供 UI 顯示。

### Server → Client（推送供 UI 顯示）
- `PACKET_BUILDING`（2）：建築完整狀態——這是最重的同步，承載 GUI 所需的幾乎一切：buildingProjects 樹、vrecords（村民紀錄）、relations、raid 狀態、imported/exported、visitorsList、raidsPerformed/Suffered、mapInfo 等。所有 panel/控制 GUI 直接讀客戶端側 Building。
- `PACKET_VILLAGER`（3）：村民實體狀態（名字、職業、性別、hiredBy、goalKey 等）。
- `PACKET_PROFILE`（101）：玩家側 UserProfile——**reputation（每村）、diplomacy points、player tags、語言熟練度**。聲望 UI 全靠這包。
- `PACKET_QUESTINSTANCE`（102）/`PACKET_QUESTINSTANCEDELETE`（103）：任務實例的建立/更新/刪除，含 `villagersInQuests` 映射，讓客戶端知道哪個村民有任務、目前第幾步、描述。
- `PACKET_OPENGUI`（104）：伺服器命令客戶端開特定 GUI（搭配 DisplayActions）。
- `PACKET_PANELUPDATE`（106）：招牌文字頁內容（已 server 端產生好的 String[][]）。
- `PACKET_MAPINFO`（7）：村莊地圖點陣資料（GuiPanelParchment 村莊地圖用，需先 request）。
- `PACKET_VILLAGELIST`（9）、`PACKET_SHOP`（11）、`PACKET_SERVER_CONTENT`（10）、`PACKET_VILLAGER_SENTENCE`（108）等。

### Client → Server（玩家指令）
- `PACKET_GUIACTION`（200）+ 一個 `GUIACTION_*` 子碼：所有按鈕動作（CHIEF_BUILDING/CROP/CONTROL/DIPLOMACY/SCROLL、QUEST_COMPLETESTEP/REFUSE、NEWVILLAGE、HIRE_*、NEGATION_WAND、NEW_BUILDING_PROJECT、PUJAS_CHANGE_ENCHANTMENT、CONTROLLEDBUILDING_*、MILITARY_*、SUMMONINGWANDUSE、MILLCHESTACTIVATE…）。
- 請求類：`PACKET_MAPINFO_REQUEST`、`PACKET_VILLAGELIST_REQUEST`、`PACKET_VILLAGERINTERACT_REQUEST`、`PACKET_AVAILABLECONTENT`（客戶端宣告語言與已安裝文化內容）。

### 重要同步意圖：樂觀本地預測（optimistic local apply）
多個 ClientSender 在送出封包後**立即在本地也呼叫一次 GuiActions 的對應方法**（diplomacy、hire、controlled building toggle、raid 等），目的是**即時 UI 回饋**，不等 server round-trip。隨後 server 的權威 `PACKET_BUILDING`/`PACKET_PROFILE` 會覆寫。重做時要保留這個「先本地預測、後伺服器校正」模式以維持 GUI 反應速度，但須確保 server 包會完整覆蓋以免漂移。

---

## 9. 與舊 MC API 耦合、需重新推導的部分

舊碼深度綁 Forge 1.12.2 之前的 API（部分甚至殘留 1.2.5 痕跡）。重做時以下要從零推導：

1. **整套 GUI 渲染**：直接呼叫 `GL11`/`GL12`、`RenderHelper`、`itemRenderer.renderItemIntoGUI`、`drawTexturedModalRect`、手刻 tooltip/gradient rect。MC 26.2 改用 `GuiGraphics`/`DrawContext` + Screen/Widget 系統，全部重畫。GuiText 的「分頁文字 + 行內按鈕/圖示/輸入框」抽象應重建為現代 widget 容器。
2. **反射 hack**：`getDrawSlotInventoryMethod`（反射取 GuiContainer 私有 `drawSlotInventory`）——現代版用公開 API 重寫。
3. **Container/Slot 系統**：ContainerTrade/ContainerPuja 與 TradeSlot/MerchantSlot/OfferingSlot 等是舊 Container API；MC 26.2 用 ScreenHandler/MenuType，需重設計（含 server 同步 slot 的方式）。
4. **網路層**：自訂 `C17PacketCustomPayload` + 手寫 ByteBuf 序列化（StreamReadWrite）。改用 Fabric Networking API 的 CustomPayload（型別化 codec）。封包語意（§8）保留，傳輸機制重寫。
5. **TileEntitySign 繼承**：TileEntityPanel 繼承原版告示牌 TE 並覆寫——MC 26.2 的 BlockEntity/Sign 結構不同，需以自訂 BlockEntity 重做。
6. **方塊/物品 sentinel**：用 vanilla 方塊當觸發器（金磚開建村 GUI、黑曜石生成村莊、沙子/路徑方塊當 dev 工具、玻璃瓶設 alchemy tag）——這些是巧妙但脆弱的耦合，重做時改成明確的自訂物品/事件。
7. **achievements/stats**：`MillAchievements` 走舊 stat 系統，改用 Advancements。
8. **Keyboard/lwjgl input**（GuiHire 顯示 keybind 名、enableRepeatEvents）——改用新輸入 API。
9. **字串/語言**：`MLN.string`、`MLN.questString`、`StatCollector.translateToLocal` 自有 i18n 與檔案載入流程，與 vanilla 語言系統並存——可保留自有系統（因內容資料就是這格式），但要脫離 vanilla 私有 API。

---

## 10. 值得保留的微妙設計

- **聲望=漸進式許可揭露**：同一個 GuiVillageHead 隨聲望長出更多按鈕/選項，而非跳到不同畫面。這是進度感的來源，務必複製。
- **母語 + 遊戲語言雙顯示**：村民名/職業同時顯示文化母語名與玩家語言譯名（`getNativeOccupationName` + `getGameOccupationName`），並有「語言熟練度」會被任務提升——是文化沉浸的核心細節。
- **動態貿易定價＋顏色化**：價格隨供需浮動並用顏色提示是否划算，slot 上有「問題」遮罩說明為何不能交易。
- **任務角色的空間關係解算**（samevillage/samehouse/nearbyvillage/anyvillage）：讓任務自然指向「鄰村的鐵匠」「同屋的妻子」，並透過描述變數展開方向/距離，產生在地敘事。重做時這套關係配對 + 變數替換是 lore 體驗的靈魂。
- **世界任務用 player tag 鏈線性推進**，並把世界探索行為（下潛、登頂、鑽探）轉成 `_complete` tag 解鎖下一步——把敘事與探索綁定，不靠對話樹。
- **樂觀本地預測**讓 GUI 即時回應（§8）。
- **地圖點陣的語意著色**（可達性紅藍、危險、路徑、不可建）——資訊密度高且直覺。
- **上鎖箱（GuiMillChest）以聲望門控**：低聲望者開箱但無法操作，傳達「還不夠信任你」。
- **village scroll 作為玩家自製導航筆記**：把村莊座標物品化。

---

## 11. 未決問題

1. **聲望門檻常數來源**：多數門檻（VILLAGE_SCROLL/CROP/CULTURE_CONTROL/雇傭 64*64、貿易 MIN_REPUTATION_FOR_TRADE）硬編在 GuiActions/Building。重做時要當設定值（config）還是維持硬編？建議集中成可調常數表。
2. **diplomacy points 的產生規則**未在本子系統檔案中完全確認（只見消耗與查詢）——需追 UserProfile/Building 的補給邏輯，確認玩家如何賺外交點數。
3. **villager-scoped quest tag** 與 player tag、global tag 三層 tag 的命名空間（tag 加 `profile.key + "_"` 前綴）在多人遊戲下的隔離正確性，重做時需重新設計持久化 schema。
4. **任務逾時檢查頻率**：`checkStatus` 何時被呼叫（每 tick？每村莊更新？）未在本批檔案確認，影響 duration 的實際手感。
5. **PACKET_BUILDING 體積**：目前一包塞入幾乎全部 GUI 資料。MC 26.2 下是否拆分（建築摘要 vs 詳情按需請求）以省頻寬？是設計決策點。
6. **custom building / custom centre 流程**（GuiCustomBuilding）細節（半徑、招牌登記、資源 registerResources）未深入；若要支援玩家自訂建築需另立專題。
7. **GuiPanelParchment 的 chunk 地圖** 依賴 `Mill.serverWorlds`（單機假設）與 ForgeChunkManager 強制載入——專屬伺服器/Fabric 下此功能的對應物待定。
8. **pujas/sacrifices 附魔目標**（PujaSacrifice 的 targets、enchantment 列表）的內容資料格式未在本批讀取，重做祭祀 GUI 前需補。
