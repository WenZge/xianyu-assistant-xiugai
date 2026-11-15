# DockerMan 直接拉取修改版镜像

直接拉取的前提是已将项目构建成镜像并发布到镜像仓库。本项目已添加 `.github/workflows/publish-image.yml`，在 GitHub Actions 上构建 `linux/amd64` 镜像，发布到 GitHub Container Registry（GHCR）。软路由只下载镜像，无需 Git、Java、Node.js 或源码构建。

## 1. 上传源码和工作流

将项目提交到自己的 GitHub 仓库，确认仓库中包含根目录 `Dockerfile` 和 `.github/workflows/publish-image.yml`。工作流需要位于仓库默认分支，才能从 Actions 页面手动运行。

本地已经创建仓库并设置远程地址的情况下，可以在 PowerShell 执行：

```powershell
cd E:\闲鱼管家\XianYuAssistant-master
git add .github/workflows/publish-image.yml docs/DIRECT_IMAGE_PULL.md README.md
git commit -m "Add x64 container image publishing"
git push
```

尚未上传过源码时，按 `docs/GIT_DOCKERMAN_DEPLOY.md` 先完成第一次上传。需要将完整源码一起提交。

## 2. 在 GitHub 构建并发布

进入仓库页面：

`Actions → Publish x64 Docker image → Run workflow → 选择默认分支 → Run workflow`

如果第一次进入 Actions 要求启用工作流，先按页面提示启用。工作流使用 GitHub 自动生成的 `GITHUB_TOKEN`，申请 `packages: write` 权限，不需要另外注册 Docker Hub 或手动填写令牌。

等待运行结果为绿色成功。点进任务的 Summary，可复制实际镜像地址，格式为：

```text
ghcr.io/仓库所有者/仓库名:latest
```

地址中的所有者和仓库名自动转成小写。例如源码仓库是 `ExampleUser/xianyu-assistant`，发布地址就是 `ghcr.io/exampleuser/xianyu-assistant:latest`。也会发布完整提交号对应的 `sha-...` 标签，便于选定版本。

工作流仅手动运行；后续更新源码后需要再次点击 Run workflow 才会发布新镜像。

## 3. 设置镜像访问权限

若希望在 DockerMan 无需登录直接拉取：打开 GitHub 个人主页（组织仓库则打开组织页）的 `Packages`，找到该镜像包，进入 `Package settings → Change visibility`，将包设置为 `Public`。这样任何人都能下载这个镜像。

源代码仓库与镜像包的可见性分别管理；源代码仓库公开，不代表首次发布的镜像一定公开。工作流不会替你修改可见性。

如果选择保留私有镜像，则需先在 DockerMan 配置 GHCR 凭据，或者在软路由使用 `docker login ghcr.io` 登录；个人账号通常使用有 `read:packages` 权限的 classic token 读取私有镜像。不要把令牌提交到 Git 或发到聊天里。

## 4. 在 DockerMan 拉取

进入截图中的 `镜像 → 拉取`，输入 Summary 中的实际镜像地址，点击「拉取」。例如：

```text
ghcr.io/exampleuser/xianyu-assistant:latest
```

拉取完成后，在「容器」中新建容器，选择这个镜像，继续使用之前的 `12400:12400` 端口和 `/app/dbdata`、`/app/logs` 目录挂载。

升级时先备份数据，再拉取新镜像、重建容器，并保留原数据目录。单纯拉取镜像或重启旧容器不会更新运行中的程序。

## 常见情况

- `denied` / `unauthorized`：确认镜像包已公开，或 Docker 已使用有读取权限的凭据登录。
- `manifest unknown`：确认 Actions 已成功，复制 Summary 中的地址及标签。
- 连接 GHCR 超时：检查软路由是否能访问 `ghcr.io`；这属于镜像仓库网络连接问题。
- Actions 发布被拒绝：检查仓库/组织是否允许 Actions 及 Packages 写入；工作流已声明所需的 `packages: write` 权限。

本地已检查工作流 YAML、构建上下文和镜像命名逻辑；尚未在你的 GitHub 账号运行或发布镜像，当前没有已验证可直接拉取的修改版地址。

参考：

- https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images
- https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry
