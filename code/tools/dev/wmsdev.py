#!/usr/bin/env python3
"""容器内开发入口：构建、测试、运行后端，本机不装 JDK。

为什么需要这个脚本：本机不安装 JDK，所有 Maven 命令都必须在
`maven:3.9-eclipse-temurin-21` 容器里执行。一条完整命令要挂源码、挂依赖缓存卷、
挂 Docker socket，再带三个 Testcontainers 环境变量，手敲一屏参数必然漏项——
2026-10-28 实测连踩四次：不挂 socket 报 `Could not connect to Ryuk`；只给 `--dns`
会覆盖 Docker 内嵌 DNS，导致 `host.docker.internal` 无法解析；禁用 Ryuk 后又因为
找不到兄弟容器端口而 `UnknownHostException`。

用法见同目录 README.md。脚本只包装 docker 命令，不修改仓库内任何文件。
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
BACKEND = REPO / "code" / "backend"
FRONTEND = REPO / "code" / "frontend"

MAVEN_IMAGE = "maven:3.9-eclipse-temurin-21"
M2_VOLUME = "yunwms-m2"
NETWORK = "yunwms-dev_default"
BACKEND_CONTAINER = "yunwms-backend"
COMPOSE_FILE = "docker-compose.dev.yml"

# 产物名来自 wms-app/pom.xml 的 artifactId 与根 POM 的 version，改版本号时同步这里
JAR_NAME = "wms-app-0.1.0-SNAPSHOT.jar"

# Docker Desktop on Windows 把 daemon 暴露成同一路径的特殊挂载，Linux 用真实 socket
DOCKER_SOCK = "//var/run/docker.sock" if os.name == "nt" else "/var/run/docker.sock"

# 容器内解析不了 Docker 内嵌 DNS 时才回落到公共 DNS；与 host-gateway 同时用，
# 否则 --dns 会让 host.docker.internal 一起失效
PUBLIC_DNS = "8.8.8.8"


def host_path(path: Path) -> str:
    """转成 docker 能接受的挂载源路径。Windows 上必须是 `D:/...` 而不是 `D:\\...`。"""
    return path.as_posix()


def module_arg(name: str | None) -> str:
    """把 `--module auth` 归一成 `wms-auth`，允许直接写全名。"""
    if not name:
        return ""
    return name if name.startswith("wms-") else f"wms-{name}"


def exec_docker(args: list[str], capture: bool = False) -> int:
    cmd = ["docker", *args]
    print("$ " + " ".join(cmd), flush=True)
    if capture:
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.stdout:
            print(result.stdout.rstrip())
        if result.returncode != 0 and result.stderr:
            print(result.stderr.rstrip(), file=sys.stderr)
        return result.returncode
    return subprocess.run(cmd).returncode


def maven_run(module: str | None, goals: list[str], with_tests: bool) -> int:
    """在 Maven 容器里跑一次构建或测试。

    `with_tests` 为真时补齐 Testcontainers 需要的四项：Docker socket、关闭 Ryuk、
    host 覆盖、host-gateway 映射。少一项都会失败，注释见模块文档。
    """
    mod = module_arg(module)
    args = [
        "run", "--rm",
        "--dns", PUBLIC_DNS,
        "-v", f"{host_path(BACKEND)}:/build",
        "-v", f"{M2_VOLUME}:/root/.m2",
        "-w", "/build",
    ]
    if with_tests:
        args += [
            "--add-host", "host.docker.internal:host-gateway",
            "-v", f"{DOCKER_SOCK}:/var/run/docker.sock",
            "-e", "TESTCONTAINERS_RYUK_DISABLED=true",
            "-e", "TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal",
        ]
    args.append(MAVEN_IMAGE)
    args += ["mvn", "-B", "--no-transfer-progress"]
    if mod:
        args += ["-pl", mod, "-am"]
    return exec_docker(args + goals)


def cmd_up(_: argparse.Namespace) -> int:
    print("启动 PostgreSQL 与 Valkey...", flush=True)
    code = exec_docker(["compose", "-f", host_path(BACKEND / COMPOSE_FILE), "up", "-d"])
    if code != 0:
        return code
    return exec_docker(["compose", "-f", host_path(BACKEND / COMPOSE_FILE), "ps"])


def cmd_down(_: argparse.Namespace) -> int:
    # 先摘掉后端容器：它挂在 compose 网络上，网络删除会因此失败
    exec_docker(["rm", "-f", BACKEND_CONTAINER])
    return exec_docker(["compose", "-f", host_path(BACKEND / COMPOSE_FILE), "down"])


def cmd_build(args: argparse.Namespace) -> int:
    goals = ["-DskipTests"]
    if args.clean:
        # 改过包结构必须 clean：宿主机 target/ 会残留已删除类的 class 与 XML，
        # 增量构建永远清不掉，报错是运行期 ClassNotFoundException 而不是编译错误
        goals = ["clean", *goals]
    return maven_run(args.module, goals + ["install"], with_tests=False)


def cmd_test(args: argparse.Namespace) -> int:
    goals: list[str] = []
    if args.test:
        goals += [f"-Dtest={args.test}", "-Dsurefire.failIfNoSpecifiedTests=false"]
    # verify 在 install 之后跑，集成测试需要依赖模块已进本地仓库
    return maven_run(args.module, goals + ["verify"], with_tests=True)


def cmd_run(_: argparse.Namespace) -> int:
    jar = BACKEND / "wms-app" / "target" / JAR_NAME
    if not jar.exists():
        print(f"缺少 {jar}，先跑 `python code/tools/dev/wmsdev.py build`", file=sys.stderr)
        return 1
    exec_docker(["rm", "-f", BACKEND_CONTAINER])
    return exec_docker([
        "run", "-d",
        "--name", BACKEND_CONTAINER,
        "--network", NETWORK,
        "-p", "8080:8080",
        # 容器内 localhost 指向容器自身，两个地址都必须换成 compose 里的容器名
        "-e", "DB_URL=jdbc:postgresql://yunwms-postgres:5432/yunwms",
        "-e", "SPRING_DATA_REDIS_HOST=yunwms-valkey",
        "-v", f"{host_path(BACKEND / 'wms-app' / 'target')}:/app",
        MAVEN_IMAGE,
        "java", "-jar", f"/app/{JAR_NAME}",
    ])


def cmd_logs(args: argparse.Namespace) -> int:
    cmd = ["logs", "--tail", str(args.tail)]
    if args.follow:
        cmd.append("-f")
    cmd.append(BACKEND_CONTAINER)
    return exec_docker(cmd)


def cmd_status(_: argparse.Namespace) -> int:
    return exec_docker([
        "ps", "-a",
        "--filter", "name=yunwms",
        "--format", "{{.Names}}\t{{.Status}}\t{{.Ports}}",
    ])


def cmd_fe(args: argparse.Namespace) -> int:
    script = "dev:owner" if args.app == "owner" else "dev:network"
    print(f"在 {FRONTEND} 启动 {script}（Ctrl+C 结束）", flush=True)
    return subprocess.run(["pnpm", script], cwd=FRONTEND, shell=(os.name == "nt")).returncode


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="wmsdev",
        description="yunWMS 容器内开发入口（本机不装 JDK）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "示例:\n"
            "  python code/tools/dev/wmsdev.py up\n"
            "  python code/tools/dev/wmsdev.py build --module auth\n"
            "  python code/tools/dev/wmsdev.py test --test BootstrapIntegrationTest\n"
            "  python code/tools/dev/wmsdev.py run\n"
        ),
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("up", help="启动 PostgreSQL 与 Valkey")
    p.set_defaults(func=cmd_up)

    p = sub.add_parser("down", help="停止后端容器与依赖容器，保留数据卷")
    p.set_defaults(func=cmd_down)

    p = sub.add_parser("build", help="编译并安装到本地仓库（跳过测试）")
    p.add_argument("--module", help="只构建某个模块，如 auth 或 wms-auth")
    p.add_argument("--clean", action="store_true", help="先 clean，改过包结构时必加")
    p.set_defaults(func=cmd_build)

    p = sub.add_parser("test", help="跑 verify，含 Testcontainers 集成测试")
    p.add_argument("--module", help="只测某个模块")
    p.add_argument("--test", help="只跑某个测试类，如 BootstrapIntegrationTest")
    p.set_defaults(func=cmd_test)

    p = sub.add_parser("run", help="以容器运行后端 jar，端口 8080")
    p.set_defaults(func=cmd_run)

    p = sub.add_parser("logs", help="看后端容器日志")
    p.add_argument("--tail", type=int, default=100, help="行数，默认 100")
    p.add_argument("-f", "--follow", action="store_true", help="持续跟随")
    p.set_defaults(func=cmd_logs)

    p = sub.add_parser("status", help="列出 yunwms 相关容器")
    p.set_defaults(func=cmd_status)

    p = sub.add_parser("fe", help="启动前端开发服务器")
    p.add_argument("app", choices=["owner", "network"], help="owner=5173，network=5174")
    p.set_defaults(func=cmd_fe)

    return parser


def main() -> int:
    args = build_parser().parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
