# 发布 Bovf Proxy

[English](RELEASING.md) | 简体中文

本分支使用 `io.github.bovf.proxy` 和自己的固定签名证书。Release 绝不使用上游或 Debug 密钥。

## 签名与备份

私有 keystore 和密码文件必须放在仓库外，目录权限为 `0700`，文件权限为 `0600`。请对整个签名目录（包括别名和密码）做加密离线备份。丢失密钥后，已用它签名安装的应用将无法正常更新；生成新密钥不能替代原密钥。系统不会自动创建离线备份。

公开证书的 SHA-256 固定在 `.github/release-certificate.sha256`，它不是秘密。后续发布应继续使用同一证书。

使用已经认证、且可访问本分支的 `gh`。通过标准输入设置已有的仓库 Actions Secrets，绝不把密码放进命令参数或聊天。以下路径只是占位符，不要在仓库中创建这些文件：

```sh
base64 < private/release.p12 | gh secret set ANDROID_KEYSTORE_BASE64 --repo bovf/android-proxy-server
gh secret set ANDROID_KEYSTORE_PASSWORD --repo bovf/android-proxy-server < private/keystore-password
gh secret set ANDROID_KEY_ALIAS --repo bovf/android-proxy-server < private/key-alias
gh secret set ANDROID_KEY_PASSWORD --repo bovf/android-proxy-server < private/key-password
```

PKCS12 的密钥密码可以与 keystore 密码相同。绝不打印、提交、作为附件上传或缓存私有 keystore 和密码文件。工作流通过环境变量向 `keytool` 输入密码，生产签名禁用配置缓存和构建缓存。PR 任务不接收签名秘密，也不会发布。

## 一次最终推送

1. 确认 `origin` 是个人分支、默认分支为 `master`。保留无关本地修改。检查远端分支、标签、Release、Actions 权限及保护规则。
2. 更新 `app/build.gradle.kts` 中的 `versionName`，递增 `versionCode`。本次为 `1.1.3` 和 `5`。同步中英文文档与 `docs/releases/<版本>.md`。
3. 用合成数据运行相关回归、全部单元测试及可行的本地构建。检查完整差异和公开文件是否含秘密或可识别的网络/环境数据。不得削弱自连接防护或禁用检查。
4. 提交所有预期修改。检查将发送的每个提交，然后在最终发布提交上创建带注释的标签。确认本地及远端不存在同名标签，且两个引用都指向该提交。

```sh
git tag -a v1.1.3 -m "Bovf Proxy v1.1.3"
git rev-parse HEAD 'v1.1.3^{commit}'
git push --atomic origin HEAD:refs/heads/master refs/tags/v1.1.3
```

此流程替代旧的“推送分支、等待、再推送标签”。**不得提前测试推送。** 如果保护规则阻止原子推送，应停止，不得改用部分推送、强制推送、重打标签或替换 Release。

标签工作流检出事件对应的精确提交。测试任务通过后，发布任务才会对同一提交签名，核对标签/版本、证书、应用 ID 和内嵌提交号，生成校验文件，为最终 APK 创建并验证 GitHub 原生构建来源证明。之后才创建仅包含签名 APK 和 `.sha256` 文件的新 Release。Debug APK 和依赖报告仅作为临时 Actions Artifact。绝不覆盖已有 Release 或附件。

如果这一次推送后的标签任务失败，应调查并报告；未经单独授权，不得再次推送或发布未验证 APK。当前 GitHub 套餐的公开仓库支持来源证明；若账号/套餐不支持或权限不足，必须报告，不能悄悄绕过。

## 验证发布

把两个发布附件下载到干净目录。验证校验文件、APK 签名及证书是否匹配固定摘要。使用 Android 构建工具检查清单中的应用 ID `io.github.bovf.proxy`、versionName/versionCode，以及 `io.github.bovf.proxy.SOURCE_REVISION` 是否等于发布提交。核对标签解引用后的提交和成功的标签 Actions 运行。

```sh
sha256sum --check android-proxy-server-1.1.3-release.apk.sha256
apksigner verify --verbose --print-certs android-proxy-server-1.1.3-release.apk
gh attestation verify android-proxy-server-1.1.3-release.apk \
  --repo bovf/android-proxy-server \
  --signer-workflow bovf/android-proxy-server/.github/workflows/android-apk.yml \
  --source-ref refs/tags/v1.1.3 --source-digest RELEASE_COMMIT --deny-self-hosted-runners
```

把 `RELEASE_COMMIT` 替换为已核对的完整提交哈希。确认来源证明中的 APK 摘要和源代码提交与下载文件及标签一致。这证明 GitHub 工作流与源代码的关系，**不等同于独立复现构建**。单元测试和本地构建不能证明真机、熄屏、热点或 VPN 行为。
