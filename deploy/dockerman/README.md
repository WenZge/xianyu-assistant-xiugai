# x64 软路由 DockerMan 部署（本地修改版）

该目录的 `app.jar` 是已加入多规格发货、买家互动的版本。原作者的公开镜像不包含本地修改。

建议将 Docker 数据根目录和本目录都放在已挂载的 SSD/硬盘上。按至少 2 GB 空闲内存、5 GB 可用磁盘预留起步空间，实际占用随账号数、浏览器和日志增加。首次构建需要联网下载 Java 基础镜像、系统依赖和 Chromium。

## 1. 上传文件

把本目录上传到软路由的持久化磁盘，例如 `/mnt/sda1/xianyu-assistant`。路径只是示例，请改成自己的挂载路径。

至少需要 `Dockerfile`、`.dockerignore` 和 `app.jar`。使用 Compose 时还需 `compose.yaml`。

新版本 JAR 构建后，在 Windows 项目根目录执行以下命令刷新部署文件：

```powershell
Copy-Item target/XianYuAssistant-2.0.3.jar deploy/dockerman/app.jar -Force
```

## 2. 在软路由构建镜像

通过 SSH 登录软路由，进入刚上传的目录：

```sh
cd /mnt/sda1/xianyu-assistant
docker build -t xianyu-assistant:sku-interaction .
```

x64 Linux 主机默认生成 amd64 镜像；无需在软路由安装 Java、Node.js 或 Maven。首次构建耗时取决于网络。

该 Dockerfile 使用 Ubuntu Jammy，并通过 JAR 内的 Playwright CLI 安装匹配的 Chromium。本目录用于现成 JAR 部署，根目录 Dockerfile 用于从源码构建。不要给 `/app/ms-playwright` 挂载空目录，以免遮盖镜像内预装的浏览器。

## 3. 创建容器

### SSH 创建，随后在 DockerMan 管理

```sh
mkdir -p dbdata logs
docker run -d \
  --name xianyu-assistant \
  --restart unless-stopped \
  --init \
  --shm-size=256m \
  -p 12400:12400 \
  -e TZ=Asia/Shanghai \
  -e AI_ENABLED=false \
  -e JAVA_TOOL_OPTIONS='-Xms128m -Xmx512m -Dfile.encoding=UTF-8' \
  -v "$PWD/dbdata:/app/dbdata" \
  -v "$PWD/logs:/app/logs" \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  xianyu-assistant:sku-interaction
```

不需要 Compose 插件。创建成功后，可在 DockerMan 的容器列表查看、停止、重启和看日志。

### 在 DockerMan 新建容器

不同固件的字段名称可能不同，按下面参数填写即可。该方式与 SSH 创建二选一，避免同名容器冲突。

| 设置 | 填写内容 |
| --- | --- |
| 容器名称 | `xianyu-assistant` |
| 镜像 | 本地镜像 `xianyu-assistant:sku-interaction` |
| 网络 | `bridge` |
| 端口 | 主机 `12400` → 容器 `12400`，TCP |
| 重启策略 | `unless-stopped` |
| 环境变量 | `TZ=Asia/Shanghai`、`AI_ENABLED=false`、`JAVA_TOOL_OPTIONS=-Xms128m -Xmx512m -Dfile.encoding=UTF-8` |
| 数据挂载 | `/mnt/sda1/xianyu-assistant/dbdata` → `/app/dbdata`，读写 |
| 日志挂载 | `/mnt/sda1/xianyu-assistant/logs` → `/app/logs`，读写 |
| 共享内存（如可设置） | `256m` |
| 启动命令 / 入口点 | 保持镜像默认值 |
| 特权模式 | 关闭 |

镜像已包含应用及 Chromium，无需安装浏览器或在容器内编译。AI 默认关闭以减少软路由开销，需要 AI 回复时将 `AI_ENABLED` 改为 `true`，并在应用系统设置配置 API。

### 已有 Compose 插件

也可以在上传目录执行 `docker compose up -d --build`，与上面方式二选一。

## 4. 访问与检查

在局域网浏览器访问 `http://软路由LAN地址:12400`，按页面注册/登录，添加闲鱼账号、同步商品，配置「自动发货」和「买家互动」。

```sh
docker ps -a --filter name=xianyu-assistant
docker logs --tail 100 xianyu-assistant
```

若端口冲突，将主机端口改为 `12401`（`-p 12401:12400`），用 `http://软路由LAN地址:12401` 访问。

## 5. 旧数据迁移和升级

迁移前先停止旧程序，复制整个旧 `dbdata` 目录到新宿主机挂载目录。SQLite 文件包括可能存在的 `-wal` / `-shm` 文件，应随目录一起复制。新版本启动会自动补充数据库字段和表。

后续升级先备份 `dbdata`，用新 JAR 替换 `app.jar`，重新构建同名镜像，再重建容器；继续使用原来的两个挂载目录。只重启旧容器不会切换到新镜像。Compose 用户可运行 `docker compose up -d --build` 重建。

## 验证范围

本机已验证 JAR 可启动、37 项后端测试、前端构建及本地页面/接口。也已验证 Dockerfile 所用的 Playwright CLI 启动方式能从该 JAR 正常运行。

当前开发电脑未安装 Docker，尚未实测本镜像的 Linux 构建、软路由运行及真实闲鱼订单。镜像下载失败时查看构建日志中的具体下载地址；浏览器报错时查看容器日志；容器退出码 137 时检查软路由内存。

参考：

- Docker 容器参数：https://docs.docker.com/reference/cli/docker/container/run/
- Playwright 浏览器安装：https://playwright.dev/java/docs/browsers
- Playwright Docker 系统兼容性：https://playwright.dev/java/docs/docker
