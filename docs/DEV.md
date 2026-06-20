# 開發環境 (L0)

## 工具鏈（已確認）
- **JDK 25**（必須）。本機已 bootstrap 一份免安裝版：`.tooling/jdk-25.0.3+9`（Temurin，不入系統 PATH/登錄）。
- **Gradle 9.5.1**：由 wrapper 自動下載，不需手動裝。
- **Loom 1.17.11**、**Fabric Loader 0.19.3**、**Fabric API 0.152.2+26.2**、**Minecraft 26.2**。
- Mappings：**官方 Mojang 名**（26.x 已去混淆，build.gradle 無 `mappings` 行）。

## 每次開 shell 先設 JAVA_HOME
PowerShell：
```powershell
$env:JAVA_HOME="C:\Users\jason\Desktop\game\mcMillenaire\.tooling\jdk-25.0.3+9"
```
（或安裝你自己的系統 JDK 25 並設好 `JAVA_HOME` 亦可。）

## 常用指令
```powershell
.\gradlew.bat genSources        # 反編譯 MC 26.2 成可讀源碼（查 API 用）
.\gradlew.bat build             # 編譯 + 打包 mod jar
.\gradlew.bat runClient         # 啟動含 mod 的客戶端
.\gradlew.bat runServer         # 啟動專用伺服器（D2 多人驗證）
.\gradlew.bat genSources --refresh-dependencies   # 依賴出問題時
```

## Headless 測試（假玩家，不用進遊戲）
原則：**走真實生產流程測試**（真實 ServerLevel / 真實事件），不打中間層。
- 假玩家測試由環境變數 `MILLENAIRE_SELFTEST` 觸發：伺服器啟動後，Fabric `FakePlayer` 會**真的手持 debug wand 右鍵方塊**，驅動與真實玩家相同的 `UseBlockCallback`（`MillInteractions`）→ 蓋出建築，再從世界讀回方塊驗證。
- 跑法（PowerShell）：
```powershell
$env:JAVA_HOME="C:\Users\jason\Desktop\game\mcMillenaire\.tooling\jdk-25.0.3+9"
$env:MILLENAIRE_SELFTEST="1"     # 開啟假玩家自驗證
.\gradlew.bat runServer --no-daemon --console=plain
```
- 看 log 的 `FakePlayer self-test:` / `DEBUG_WAND built ...` / `Readback:` 行即知結果。
- 真實玩家也可：手持 `millenaire:debug_wand` 右鍵任意方塊 → 在該處蓋出 norman armoury_A。
- 停伺服器：kill `.tooling` JDK 的 java 進程（背景跑時）。

## 反編譯源碼在哪
`genSources` 完成後，Loom 會把反編譯出的 Minecraft 源碼附到 minecraft 依賴上。
- IDE 內：對任一 vanilla 類別「跳到定義」即見可讀源碼。
- 檔案位置（供 grep）：`~/.gradle/caches/fabric-loom/...` 下的 `minecraft-*-sources.jar`／解壓的 `*-sources` 目錄（實際路徑見 `genSources` 輸出）。

## 專案結構（L0）
```
build.gradle / settings.gradle / gradle.properties   # Loom 專案
src/main/java/org/millenaire/...        # common（server 權威，D2）
src/main/resources/fabric.mod.json
src/client/java/org/millenaire/client/...# client-only（splitEnvironmentSourceSets）
src/client/resources/
```

## 注意
- `.tooling/`、`_reference/`、`run/`、`build/`、`.gradle/` 都已 `.gitignore`，不入版控。
- mod id / namespace = `millenaire`（決策 D4）。
