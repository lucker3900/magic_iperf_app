# MagicIperf (Android)

一个类似 Magic iPerf 的轻量级 Android 客户端，用来快速发起 TCP/UDP iperf3 测速。采用 Jetpack Compose 构建，UI 支持常见参数（地址、端口、时长、UDP 目标带宽、正反向切换）并即时展示输出日志。

## 目录结构
- `app/`：Android 应用模块（Compose UI、ViewModel、iperf3 运行器）
- `app/src/main/assets/`：放置设备架构对应的 `iperf3` 可执行文件
- `gradlew` / `gradlew.bat`：Gradle Wrapper 脚本（需要本地 JDK 17+）

## 快速开始
1) 准备 iperf3 可执行文件  
   - 将为设备架构编译好的 `iperf3` 重命名为 `iperf3`（无扩展名）后放到 `app/src/main/assets/`  
   - 安装后应用会复制到内部存储并设置为可执行；若缺失会在 UI 给出提示

2) 构建 / 运行  
   - 使用 Android Studio 直接导入 `magic_iperf_app` 目录并运行  
   - 或者在命令行执行（需要本地 Java 17+；若缺少 `gradle/wrapper/gradle-wrapper.jar`，可用本地 Gradle 执行 `gradle wrapper` 生成）：  
     ```bash
     cd magic_iperf_app
     ./gradlew assembleDebug
     ./gradlew installDebug
     ```

3) App 使用  
   - 填写服务器地址与端口，选择 TCP/UDP，设置持续时间与 UDP 目标带宽（可选）  
   - 切换“正向/反向”按钮以便测试上行或下行  
   - 点击“开始测试”，日志区域会实时显示 iperf3 输出；错误会以 Snackbar 提示

## 备注
- 若需要调整 Gradle 版本，可修改 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl`
- 项目默认禁用混淆，发布前可在 `app/build.gradle.kts` 中开启
- 建议不要将真实 iperf3 二进制提交到仓库，以避免仓库过大
