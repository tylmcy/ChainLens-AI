FROM ghcr.io/foundry-rs/foundry:v1.7.1

WORKDIR /demo
COPY src ./src
COPY build ./build
COPY seed.sh ./seed.sh

# 字节码由源码使用官方 ethereum/solc:0.8.30 生成并随仓库固定；运行期不下载 solc。

ENTRYPOINT ["/bin/sh", "/demo/seed.sh"]
