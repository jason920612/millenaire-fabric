# 26.2 API 筆記（反編譯實測）

> 來源：`gradlew genSources` 反編譯（Mojmap）+ `javap` 驗證 Fabric API。
> 反編譯源碼解壓在 `.tooling/mc-sources/{common,client}/`（4849 + 2206 類，可直接 grep）。
> 這裡只記「與舊版/直覺不同、踩過或會踩的」差異，供 L1+ 參考。

## Mojmap 命名變動（26.x 去混淆後的官方名）
- **`ResourceLocation` → `net.minecraft.resources.Identifier`**。工廠：`Identifier.fromNamespaceAndPath(ns, path)` / `Identifier.parse("ns:path")` / `Identifier.withDefaultNamespace(path)`。
- id 常數集中在 `net.minecraft.references.*`（`ItemIds`、`BlockItemIds` 等）。
- `FriendlyByteBuf` 上有 `writeIdentifier/readIdentifier`。

## 物件註冊（Item，26.2 實測可用）
```java
ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id("debug_wand"));
Item item = new Item(new Item.Properties().setId(key));   // 必須 setId(key)
Registry.register(BuiltInRegistries.ITEM, key, item);
```
- `Item.Properties.setId(ResourceKey<Item>)` 是必要步驟（vanilla `Items.registerItem` 內部就是這樣做）。
- `BlockItem` 註冊後會自動 `registerBlocks(Item.BY_BLOCK, item)`。

## Registry 查詢（給 LogicalBlockMapping 用）
- `BuiltInRegistries.BLOCK.getOptional(Identifier) -> Optional<Block>`（也有 `getValue(Identifier)` 回 nullable）。
- `block.defaultBlockState() -> BlockState`。

## 自訂網路封包（CustomPayload）
- vanilla：`CustomPacketPayload.Type<T>`（record，持 `Identifier id`）；codec 用 `StreamCodec.composite(...)` 或 `StreamCodec.unit(...)`。
- 單欄位 codec：`StreamCodec.composite(ByteBufCodecs.STRING_UTF8, T::field, T::new)`，型別 `StreamCodec<RegistryFriendlyByteBuf, T>`。
- **Fabric API（networking-api-v1 6.3.3）方法名已變**：
  - `PayloadTypeRegistry.clientboundPlay()` / `serverboundPlay()`（舊稱 `playS2C()`/`playC2S()` 已不存在）。
  - 還有 `clientboundConfiguration()` / `serverboundConfiguration()`。
  - `ServerPlayNetworking.send(ServerPlayer, CustomPacketPayload)`。
  - `ClientPlayNetworking.registerGlobalReceiver(Type<T>, PlayPayloadHandler<T>)`，handler = `(payload, context) -> ...`。
  - 入站事件：`ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ...)`，`handler.player` 是 `ServerPlayer`。

## GUI 渲染管線大改（影響 L5）
- **沒有 `GuiGraphics`**。`Renderable` 介面的方法變成
  `void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)`，
  `Screen` 也走這條（render-state 抽取，與 GPU 後端解耦，配合 26.2 的 OpenGL/Vulkan 雙後端）。
- 結論：L0 的 `GuiText` 做成**純資料模型**（無 MC 渲染依賴）；實際 Screen 繪製到 L5 再對接 `extractRenderState`/`GuiGraphicsExtractor`。相關類別：`client/.../gui/GuiGraphicsExtractor.java`、`Hud.java`、`components/Renderable.java`。

## 工具鏈備忘
- 反編譯重跑：`gradlew genSources`；源碼 jar 在 `.gradle/loom-cache/minecraftMaven/.../*-sources.jar`。
- 查 Fabric API 真實簽名：`javap -cp <fabric-module-jar> <fqcn>`（jar 在 `~/.gradle/caches/modules-2/.../net.fabricmc.fabric-api/...`）。
