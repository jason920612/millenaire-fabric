# Millénaire 26.2 — 架構藍圖（框架地圖）

> 依 `docs/intent/01–06` 的設計意圖，把整個 mod 的**套件 / 類別框架**一次鋪出。
> 標記：**[✓]** 已實作（或實作中）、**[~]** 已有 skeleton、**[ ]** 待建 skeleton。
> 每個型別在 javadoc 標註其來源意圖文檔。實作填實沿 PLAN.md 的分層推進。

---

## 套件總覽（`org.millenaire`）

```
org.millenaire
├─ Millenaire [✓]                 main entrypoint：註冊 item/block/entity、事件接線
├─ MillInteractions [✓]           server 權威玩家互動（debug wand…）
├─ core/                          跨子系統共用（常數/設定/工具）
│   ├─ MillConfig [~]             KeepActiveRadius/BackgroundRadius/各種上限（doc02/01）
│   └─ MillMath [ ]               方位/距離/qualifier 工具（Point 語意→BlockPos）
│
├─ content/  ……………………………………  子系統 04（內容 DSL/文化/經濟資料）
│   ├─ Dsl [✓] ContentRepository [✓] ContentLoader [✓] MillContent [✓]
│   ├─ block/  PointType [✓] BlockList [✓] LogicalBlockMapping [✓]
│   ├─ building/  BuildingPlan [✓] BuildingPlacer [✓]   (schematic：子系統 03)
│   ├─ culture/  Culture [✓] VillageType [✓] VillagerType [✓]
│   │            NameList [ ] Shop(def) [ ] LanguageTable [ ]   (doc04)
│   └─ economy/  Goods [✓] TradeGood [ ] PriceTable [ ]         (doc04/06 資料面)
│
├─ world/  …………………………………………  子系統 02（世界模擬骨幹）
│   ├─ MillWorld [✓] MillWorldData [✓]        active/inactive + 持久索引 single-source-of-truth
│   ├─ TownHall [✓]                            村莊聚合根（buildings/villagers/relations/control）
│   ├─ BuildingProject [✓] VillagerMember [✓]  子建築/村民記錄
│   ├─ Construction [✓]                        L3 逐塊建造
│   ├─ VillageSiting [ ]                        選址規則（biome/距離/weight/tag，doc02 §3.1）
│   ├─ VillageGrowth [ ]                        成長：選下一個 buildingProject（doc02 §3.2）
│   ├─ Relations [ ]                            村↔村外交（doc02 §3.4）
│   └─ MillWorldInfo [ ]                        地形認知快取（非持久，doc02 §2.4）
│
├─ building/  ………………………………………  子系統 03（schematic/建造，執行期面）
│   ├─ ResManager [ ]                           建築具名站位（craftingPos/sellingPos/sleepingPos…）
│   └─ BuildingTags [ ]                          tag 常數/查詢
│
├─ entity/  …………………………………………  子系統 01（NPC/Goal）
│   ├─ MillVillagerEntity [✓]                   村民實體（goalKey/type/home 持久化）
│   ├─ VillagerSpawning [ ]                      chanceweight/繁衍/becomeadult（doc01 §2.8）
│   └─ ai/
│       ├─ VillagerGoal [✓] (= Goal 介面)        無狀態 singleton goal
│       ├─ VillagerScheduler [✓]                 單一最高優先序排程器
│       ├─ VillagerGoals [✓]                     registry + fallback goals
│       ├─ GenericGoal [✓] GenericGoalDefinition [✓] GoalDefinitions [✓]
│       ├─ GoalInformation [ ]                    目的地（dest/destBuildingPos/targetEnt，doc01 §2.3）
│       ├─ PathingConfig [ ]                      per-goal 尋路設定（doc01 §6）
│       ├─ goals/ (hard-coded 特殊行為，doc01 §5.1)
│       │   ├─ SleepGoal [ ] GetToolGoal [ ] BringBackResourcesHomeGoal [ ]
│       │   ├─ DeliverGoodsGoal [ ] BeSellerGoal [ ] ChopTreesGoal [ ] MineGoal [ ]
│       └─ combat/  DefendVillageGoal [ ] RaidVillageGoal [ ] HideGoal [ ] (doc01 §7)
│
├─ economy/  ………………………………………  子系統 06（交易機制）
│   ├─ Denier [ ]                               base-64 三幣貨幣換算（doc06）
│   ├─ Trade [ ]                                買賣結算（ContainerTrade 語意）
│   └─ ShopStock [ ]                            商店庫存/價格（三層價格）
│
├─ progression/  …………………………………  子系統 05/02（玩家進度）
│   ├─ UserProfile [ ]                          聲望(村/文化)/控制/quest 進度（doc02 §2.7、doc05）
│   ├─ Reputation [ ]                            許可制階梯（doc05 §聲望）
│   ├─ Quest [ ] QuestStep [ ] QuestInstance [ ]  任務（player-tag 鏈，doc05）
│   └─ Lore [ ]                                  卷軸/parchment 文本展開（doc05）
│
├─ item/   MillItems [ ] SummoningWand [ ] Parchment [ ] Purse [ ]   (doc06)
├─ block/  MillBlocks [ ] PanelBlock [ ] CropBlock [ ]               (doc06)
│
├─ net/  ……………………………………………  子系統 06（server 權威同步）
│   ├─ MillHandshakePayload [✓]
│   ├─ BuildingSyncPayload [ ]                   PACKET_BUILDING（最重，doc06）
│   ├─ GuiActionPayload [ ]                      PACKET_GUIACTION 萬用分派（doc06）
│   └─ ContentNegotiationPayload [ ]             內容協商同步（doc06）
│
├─ client/  ………………………………………  子系統 05/06（客戶端）
│   ├─ MillenaireClient [✓]
│   ├─ gui/  GuiText [✓]                         多頁多行文字模型（doc05）
│   │        GuiTextScreen [ ]                    接 26.2 extractRenderState（doc05/API-NOTES）
│   │        VillageHeadScreen [ ] TradeScreen [ ] QuestScreen [ ]
│   └─ renderer/  MillVillagerRenderer [ ]        實體 renderer（issue #9）
│
└─ dev/  FakePlayerProbe [✓]                     headless 假玩家測試
```

---

## 子系統 → 意圖文檔對照

| 套件 | 意圖文檔 | 核心不變量（務必保留） |
|---|---|---|
| `entity` / `entity.ai` | [01](intent/01-npc-ai-goals.md) | goal 無狀態 singleton + 村民持狀態；單一最高優先序；leisure 讓位於工作；生產三閘門；戰鬥由村莊狀態由上而下驅動 |
| `world` | [02](intent/02-core-domain-world.md) | Town Hall 為村莊單一擁有者並整批存檔；雙層資料粒度（輕量索引+重實體）；active/inactive 綁玩家鄰近度但世界時間照走 |
| `content.building` / `building` | [03](intent/03-building-schematic.md) | 分層 PNG 編碼契約；blocklist 5 欄；逐塊兩遍式建造；startLevel/orientation/升級疊加；ResManager 具名站位 |
| `content` / `content.culture` / `content.economy` | [04](intent/04-content-dsl-cultures.md) | 四種微 DSL；重複 key=append；good 名中介層；雙語值；base-64 貨幣記法 |
| `progression` / `client.gui` | [05](intent/05-gui-quests-progression.md) | 聲望=許可制漸進揭露；GuiText 多頁多行模型；quest player-tag 鏈；lore 變數展開 |
| `economy` / `item` / `block` / `net` | [06](intent/06-items-blocks-trade-net.md) | denier 64 進位；server 權威（連開哪個 GUI 都 server 決定）；good 名中介；強制區塊載入 |

---

## 實作順序（對映 PLAN.md 分層）
框架鋪好後，依 PLAN.md L0–L7 把 **[ ]/[~]** 逐一填實；每個 skeleton 的 javadoc 註明其意圖來源與 TODO。已實作的世界/建造/NPC 線（見 PLAN §9）即沿此框架繼續深化。
