# 部署到 GitHub Actions 全步骤

## 一、推到 GitHub（一次性）

打开 PowerShell：

```powershell
cd D:\claude1.1\android-nettools

# 如果还没装 git，先装：winget install Git.Git
git init
git add .
git commit -m "Initial NetTools project"
```

去 https://github.com/new 新建一个空仓库（**不要勾任何 README/.gitignore/license**），比如名字叫 `nettools`，可见性自选（私有也行）。

回到 PowerShell：

```powershell
git branch -M main
git remote add origin https://github.com/<你的用户名>/nettools.git
git push -u origin main
```

push 完，仓库的 **Actions** 标签下应该立刻能看到 "Build Android APK" 在跑。等它跑完，能下载 `nettools-debug-apk` 的 debug APK 验证项目能编译通过。

## 二、生成 Release Keystore（一次性，2 分钟）

1. 进入 GitHub 仓库 → **Actions** 标签
2. 左侧列表点 **Generate Release Keystore (one-shot)**
3. 右上角点 **Run workflow** 按钮，弹出框里：
   - `key_alias`：默认 `nettools` 即可，或改成你喜欢的名字
   - `validity_years`：默认 25 年
   - `dname`：默认值能用，想改可改
   - 点绿色 **Run workflow**
4. 等 30 秒跑完，进入这次运行的页面
5. 页面最下方 **Artifacts** 里下载 `keystore-bundle-DELETE-AFTER-USE.zip`
6. 解压，里面有三个文件：
   - `release.keystore` —— **本地备份保存**（找个安全地方，丢了就再也签不出同一个证书，更新装不上）
   - `release.keystore.b64` —— 一会儿复制内容用
   - `SECRETS_TO_ADD.txt` —— 里面写着 4 个 secret 的名字和值（**含两个随机密码**）

## 三、把 4 个 Secret 加到仓库（5 分钟）

进入仓库 → **Settings** → 左侧 **Secrets and variables** → **Actions** → 右上 **New repository secret**，加 4 次：

| Name | Value 从哪来 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | 用记事本打开 `release.keystore.b64`，全选复制粘贴（一长串单行） |
| `RELEASE_KEYSTORE_PASSWORD` | `SECRETS_TO_ADD.txt` 里给出的值 |
| `RELEASE_KEY_ALIAS` | `SECRETS_TO_ADD.txt` 里给出的值（默认 `nettools`） |
| `RELEASE_KEY_PASSWORD` | `SECRETS_TO_ADD.txt` 里给出的值 |

## 四、清理（重要，安全）

1. 回到 **Actions** → 找到 "Generate Release Keystore" 的那次运行 → 右上角 **...** → **Delete workflow run**（删掉，密码就不留在 GitHub 任何地方了）
2. 把 `.github/workflows/generate-keystore.yml` 文件删掉，提交：

   ```powershell
   cd D:\claude1.1\android-nettools
   git rm .github/workflows/generate-keystore.yml
   git commit -m "Remove one-shot keystore generator"
   git push
   ```

   （以后想再用可以从 git 历史里 checkout 回来，但**生成新 keystore 没意义**——签名证书必须一致，否则用户装不上更新版。）
3. 把本地下载的 `keystore-bundle-DELETE-AFTER-USE` 文件夹里的 `release.keystore.b64` 和 `SECRETS_TO_ADD.txt` 删除。**只保留 `release.keystore` 作为线下备份**。

## 五、以后怎么构建？

直接 push 代码就行。每次 push 到 `main` 会自动跑：
- `nettools-debug-apk`：debug 版（装在自己机器调试用）
- `nettools-release-apk`：**release 签名版**（能正式分发，能上架 Play / 国内应用商店）

也可以去 Actions → "Build Android APK" → **Run workflow** 手动触发。

## 备份提示

`release.keystore` 这一个文件就是你的**应用身份证**。建议：

- 保存到云盘（加密）+ U 盘冷备份至少两份
- 记录下你设的 store/key 密码（用密码管理器，1Password / Bitwarden 都行）
- **绝对不要**把 `release.keystore` 提交进 git 仓库

丢了它的后果：以后发新版本，用户必须卸载老版本才能装新版，等于换了个新应用。
