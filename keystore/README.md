# 签名配置说明

## 文件说明

- `keystore.properties`: 密钥库配置文件
- `yuyan-release-key.jks`: 密钥库文件
- `README.md`: 本说明文件

## 配置信息

### 默认配置
- **密钥库文件**: `yuyan-release-key.jks`
- **密钥库密码**: `yuyan123456`
- **密钥别名**: `yuyan-key`
- **密钥密码**: `yuyan123456`
- **有效期**: 10000天（约27年）

### 证书信息
- **算法**: RSA 2048位
- **CN**: YuYan IME
- **OU**: Development
- **O**: YuYan
- **L**: Beijing
- **ST**: Beijing
- **C**: CN

## 使用方法

### 生成Release版本APK

```bash
# 生成在线版本Release APK
.\gradlew.bat assembleOnlineRelease

# 生成离线版本Release APK
.\gradlew.bat assembleOfflineRelease
```

### 修改签名配置

如需修改签名配置，请编辑 `keystore.properties` 文件：

```properties
storeFile=your-keystore.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

## 安全注意事项

⚠️ **重要提醒**:
1. 请妥善保管密钥库文件和密码
2. 不要将密钥库文件提交到版本控制系统
3. 建议在生产环境中使用更复杂的密码
4. 定期备份密钥库文件

## 生成新密钥库

如需生成新的密钥库，可以使用以下命令：

```bash
keytool -genkey -v -keystore keystore/your-new-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias your-alias -storepass your-store-password -keypass your-key-password -dname "CN=Your App, OU=Development, O=Your Company, L=City, S=State, C=Country"
```

## 验证密钥库

可以使用以下命令验证密钥库：

```bash
keytool -list -v -keystore keystore/yuyan-release-key.jks -storepass yuyan123456
```
