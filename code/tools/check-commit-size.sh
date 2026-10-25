#!/bin/sh
# 统计每次提交的变更行数（增删合计），用于核对「单次提交 ≤400 行」。
# 口径来源：docs/agents.md 第五节（唯一判据）。
# 用法：
#   sh code/tools/check-commit-size.sh                 # 全部历史
#   sh code/tools/check-commit-size.sh main..HEAD      # 只看区间
#   LIMIT=300 sh code/tools/check-commit-size.sh       # 改阈值
# 退出码：0 全部合规；2 存在超限提交（便于 CI 直接使用）。
set -eu

RANGE="${1:-HEAD}"
LIMIT="${LIMIT:-400}"

# 生成物与锁文件不计入，与 docs/agents.md 第五节一致
EXCLUDE_1="packages/tokens/src/primitive.css"
EXCLUDE_2="docs/api/inventory.md"
EXCLUDE_3="docs/api/openapi.json"
EXCLUDE_4="pnpm-lock.yaml"

git log --numstat --format='%x01%h%x01%s' "$RANGE" |
awk -v limit="$LIMIT" -v range="$RANGE" \
    -v e1="$EXCLUDE_1" -v e2="$EXCLUDE_2" -v e3="$EXCLUDE_3" -v e4="$EXCLUDE_4" '
function flush() {
  if (hash == "") return
  over = (total > limit)
  if (over) over_count++
  count++
  printf "%s %7d %-4s %s\n", hash, total, (over ? "OVER" : "ok"), subject
  hash = ""
}
BEGIN { count = 0; over_count = 0; total = 0 }
{
  if (substr($0, 1, 1) == "\001") {
    flush()
    n = split(substr($0, 2), f, "\001")
    hash = f[1]
    subject = (n >= 2 ? f[2] : "")
    total = 0
    next
  }
  if (NF == 3) {
    add = $1; del = $2; path = $3
    if (add == "-") next
    if (index(path, e1) || index(path, e2) || index(path, e3) || index(path, e4)) next
    total += add + del
  }
}
END {
  flush()
  printf "\n范围 %s，阈值 %d 行，共 %d 次提交，%d 次超限\n", range, limit, count, over_count
  if (over_count > 0) exit 2
}
'
