#!/usr/bin/env bash
# 生成 2048-bit RSA 密钥对，供 MienMien business 非对称加密使用。
set -euo pipefail
OUT_DIR="${1:-./.secrets}"
mkdir -p "$OUT_DIR"
PRIV="$OUT_DIR/mienmien-rsa-private.pem"
PUB="$OUT_DIR/mienmien-rsa-public.pem"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$PRIV"
openssl rsa -in "$PRIV" -pubout -out "$PUB"
chmod 600 "$PRIV"
echo "已写入:"
echo "  $PRIV"
echo "  $PUB"
echo ""
echo "启动 business 时注入（示例）："
echo "  export MIENMIEN_RSA_PRIVATE_KEY_PEM=\"\$(cat $PRIV)\""
echo "  export MIENMIEN_RSA_PUBLIC_KEY_PEM=\"\$(cat $PUB)\""
