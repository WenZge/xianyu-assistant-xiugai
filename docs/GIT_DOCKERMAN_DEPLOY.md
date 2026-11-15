# Git 拉取源码后部署到 x64 软路由

项目根目录的 `Dockerfile` 和 `compose.yaml` 支持从 Git 源码构建，包含这次新增的多规格发货与买家互动。`deploy/dockerman` 则是上一种“上传现成 JAR”的独立方案；从 Git 部署时使用根目录文件。

## 上传源码到自己的仓库

仓库根目录应直接包含 `pom.xml`、`Dockerfile`、`compose.yaml`、`src`、`vue-code`、`.mvn` 和 `mvnw`。不要将部署 ZIP 解压内容作为源码仓库。

本地项目来自解压包，尚未创建 Git 仓库。先在 GitHub 等服务新建一个空仓库，再在本机项目根目录执行，替换自己的地址：

```powershell
cd E:\闲鱼管家\XianYuAssistant-master
git init
git add .
git status --short
git commit -m "Add SKU delivery and buyer interaction"
git branch -M main
git remote add origin https://github.com/YOUR_NAME/YOUR_REPO.git
git push -u origin main
```

提交前查看 `git status`。当前忽略规则排除了 `target`、`node_modules`、数据库、日志、环境配置文件和 `deploy/dockerman/app.jar`。运行数据留在设备上，源码由 Docker 构建，不需要将约 229 MB 的成品 JAR 纳入 Git。

## 软路由首次部署

先确认 `git --version`、`docker version` 能正常执行。下面的 `/mnt/sda1` 是已挂载硬盘路径示例，请替换成实际路径。

```sh
cd /mnt/sda1
git clone https://github.com/YOUR_NAME/YOUR_REPO.git xianyu-assistant
cd xianyu-assistant
mkdir -p dbdata logs
docker build -t xianyu-assistant:sku-interaction .
```

镜像在软路由本机构建，首次需要下载 Node.js、Java、Maven、npm 依赖和 Chromium，耗时与临时磁盘占用都比现成 JAR 方案大。建议预留至少 4 GB 空闲内存与 10 GB 磁盘作为源码构建起点，实际需求依设备和依赖缓存而变；资源不足时可改为在 x64 Linux 电脑构建镜像，再导入软路由。

构建完成后，在 DockerMan 选择本地镜像 `xianyu-assistant:sku-interaction` 新建容器。参数如下：

| 字段 | 值 |
| --- | --- |
| 名称 | `xianyu-assistant` |
| 网络 | `bridge` |
| 端口 | 主机 `12400` → 容器 `12400` / TCP |
| 重启策略 | `unless-stopped` |
| 数据挂载 | `/mnt/sda1/xianyu-assistant/dbdata` → `/app/dbdata`，读写 |
| 日志挂载 | `/mnt/sda1/xianyu-assistant/logs` → `/app/logs`，读写 |
| 环境变量 | `TZ=Asia/Shanghai`、`AI_ENABLED=false` |
| JVM 参数（需要调整时） | `JAVA_OPTS=-Xms128m -Xmx512m -Dfile.encoding=UTF-8` |
| 共享内存（如可设置） | `256m` |
| 启动命令、入口点 | 保持默认 |
| 特权模式 | 关闭 |

不要同时保留另一套同名容器占用 `12400`。启动后访问 `http://软路由LAN地址:12400`。

## 有 Compose 插件时

如果 `docker compose version` 能正常执行，可以在源码根目录用下面一条命令代替手动构建和 DockerMan 新建：

```sh
docker compose up -d --build
```

启动后仍可在 DockerMan 管理容器。没有 Compose 插件不影响前面的 DockerMan 方式。

## 后续升级

先停止应用并备份整个 `dbdata` 目录，然后：

```sh
cd /mnt/sda1/xianyu-assistant
git pull --ff-only
docker build -t xianyu-assistant:sku-interaction .
```

在 DockerMan 用新镜像重建应用容器，继续使用原来的数据挂载目录。单纯重启旧容器不会使用刚构建的新镜像。

Compose 用户在拉取代码后执行 `docker compose up -d --build` 即可重建。不要删除 `dbdata`。后续 Git 拉取不会自动升级运行中的容器。

私有仓库在 Git 提示时按所用服务的认证方式登录；不要将访问令牌写进源码或提交文件。

## 已验证与未验证

前后端源码已通过本机 37 项后端测试、前端类型检查与生产构建；Playwright CLI 可通过打包 JAR 启动。此次新增的容器配置已检查构建路径与 YAML；当前电脑未安装 Docker，尚未实测整套 Linux 镜像构建和软路由运行。

参考文档：

- https://docs.docker.com/get-started/tutorials/run-an-app/
- https://docs.docker.com/reference/cli/docker/compose/build/
