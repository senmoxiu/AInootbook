# MinIO 微信小程序域名白名单配置

## 背景

微信小程序要求所有网络请求的域名必须在小程序管理后台配置白名单。本项目使用 MinIO 存储素材文件（图片、音频、视频、文档），需要将 MinIO 域名添加到白名单。

## 配置步骤

### 1. 确认 MinIO 域名

检查后端配置文件中的 MinIO 域名：

```yaml
# application.yml
jeecg:
  minio:
    endpoint: https://minio.example.com
    bucket-name: ainootbook
```

### 2. 配置微信小程序域名白名单

登录微信小程序管理后台：https://mp.weixin.qq.com

**路径**：开发 → 开发管理 → 开发设置 → 服务器域名

**需要配置的域名类型**：
- **uploadFile 合法域名**：`https://minio.example.com`（素材上传）
- **downloadFile 合法域名**：`https://minio.example.com`（素材下载）

### 3. MinIO CORS 配置

MinIO 需要配置 CORS 允许小程序跨域访问：

```bash
# 使用 mc 命令行工具配置
mc admin config set myminio api cors_allow_origin="https://servicewechat.com"
mc admin service restart myminio
```

或在 MinIO 控制台配置：

**路径**：Administrator → Settings → API → CORS Allow Origin

**配置值**：
```
https://servicewechat.com
https://*.servicewechat.com
```

### 4. 验证配置

在小程序开发者工具中测试素材上传/下载功能：

1. 上传图片（≤10MB）
2. 上传音频（≤150MB）
3. 上传文档（≤50MB）
4. 上传视频（≤500MB，分片上传）
5. 下载素材文件

## 注意事项

1. **域名必须使用 HTTPS**：微信小程序不支持 HTTP 域名
2. **域名备案**：域名必须完成 ICP 备案
3. **端口限制**：仅支持 443 端口（HTTPS 默认端口）
4. **域名数量限制**：每个小程序最多配置 20 个域名
5. **生效时间**：配置后立即生效，无需审核

## 常见问题

### Q1: 小程序提示"不在以下 request 合法域名列表中"

**原因**：MinIO 域名未添加到白名单

**解决**：在小程序管理后台添加 MinIO 域名到 `uploadFile` 和 `downloadFile` 白名单

### Q2: 上传失败，提示 CORS 错误

**原因**：MinIO 未配置 CORS

**解决**：按照步骤 3 配置 MinIO CORS

### Q3: 开发环境可以上传，正式环境失败

**原因**：开发环境可以跳过域名校验，正式环境强制校验

**解决**：确保正式环境 MinIO 域名已添加到白名单

## 相关文档

- [微信小程序服务器域名配置](https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html)
- [MinIO CORS 配置](https://min.io/docs/minio/linux/administration/console/security-and-access.html#cors-configuration)
