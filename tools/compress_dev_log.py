#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
压缩 开发日志.md：
- 保留最近 10 个 R 节点完整内容
- 折叠更早的 R 节点为索引（一行一节点 + commit hash + 关键产出）
"""
import re
import sys
from pathlib import Path

LOG_FILE = Path("D:/zhutao/MED_RMS_PMS/开发日志.md")

def parse_r_nodes(text):
    """解析 R 节点，按 R 编号分组"""
    # 找到所有 R 节点顶级标题（## R1xx/R1xx.x 的标题行，不是 ## R1xx 详情 等嵌套）
    # 顶级标题以"## R1xx"或"## R1xx.x"开头（不是"## R1xx 详情"）
    nodes = {}  # R_num -> {"title": ..., "start": line, "end": line, "level": ...}
    lines = text.split("\n")

    # 找到所有顶级 R 节点
    for i, line in enumerate(lines):
        m = re.match(r"^## (R\d{3,4}(?:\.\d+)?)\s+(.+)$", line)
        if m:
            r_num = m.group(1)
            title = m.group(2).strip()
            # 排除嵌套章节
            if any(title.startswith(p) for p in ["详情", "修改记录", "回滚操作", "后续变更记录", "验证结果", "回滚"]):
                continue
            nodes[r_num] = {"title": title, "start": i, "level": "top"}

    # 计算 end（下一个顶级 R 节点开始前）
    sorted_nums = sorted(nodes.keys(), key=lambda x: int(re.match(r"R(\d+)", x).group(1)))
    for idx, r_num in enumerate(sorted_nums):
        if idx + 1 < len(sorted_nums):
            nodes[r_num]["end"] = nodes[sorted_nums[idx + 1]]["start"]
        else:
            nodes[r_num]["end"] = len(lines)
    return nodes, lines


def main():
    text = LOG_FILE.read_text(encoding="utf-8")
    nodes, lines = parse_r_nodes(text)

    # 排序
    def sort_key(r_num):
        m = re.match(r"R(\d+)(?:\.(\d+))?", r_num)
        return (int(m.group(1)), int(m.group(2) or 0))

    sorted_nums = sorted(nodes.keys(), key=sort_key)
    print(f"找到 {len(sorted_nums)} 个顶级 R 节点")

    # 保留最近 10 个
    keep_recent = 10
    keep_nums = set(sorted_nums[-keep_recent:])

    # 构建新内容
    new_lines = []
    new_lines.append("# Med-RMS 开发日志")
    new_lines.append("")
    new_lines.append("> **说明**: 本日志包含所有 R 节点记录。最近 10 个 R 节点保留完整内容，更早的 R 节点折叠为索引。")
    new_lines.append("> **使用**: 新会话从 `CONTEXT.md` 开始，需要历史详情再翻阅此文件。")
    new_lines.append("> **完整 R 节点索引**: 见下方「R 节点索引」章节")
    new_lines.append("> **最后更新**: 2026-07-02（R148）")
    new_lines.append("")

    # 头部保留到第一个 R 节点
    first_r_start = nodes[sorted_nums[0]]["start"]
    head = "\n".join(lines[:first_r_start])
    new_lines.append(head)
    new_lines.append("")

    # 索引章节
    new_lines.append("## R 节点索引（折叠 - 详情见对应 tag）")
    new_lines.append("")
    new_lines.append("> 最近 10 个 R 节点保留在下方「最近 R 节点」章节。更早的 R 节点可 `git show R1XX` 查看完整 commit。")
    new_lines.append("")
    new_lines.append("| R 节点 | 主题 | 行数 |")
    new_lines.append("|--------|------|------|")
    for r_num in sorted_nums:
        info = nodes[r_num]
        end = info["end"]
        line_count = end - info["start"]
        if r_num in keep_nums:
            new_lines.append(f"| **{r_num}** | {info['title']} | {line_count} ⭐ 保留 |")
        else:
            new_lines.append(f"| {r_num} | {info['title']} | {line_count} |")
    new_lines.append("")
    new_lines.append(f"**总计**: {len(sorted_nums)} 个 R 节点，{sum(nodes[n]['end']-nodes[n]['start'] for n in sorted_nums)} 行")
    new_lines.append("")
    new_lines.append("---")
    new_lines.append("")

    # 最近 R 节点完整内容
    new_lines.append("## 最近 R 节点（保留完整内容）")
    new_lines.append("")
    for r_num in sorted_nums:
        if r_num not in keep_nums:
            continue
        info = nodes[r_num]
        for i in range(info["start"], info["end"]):
            new_lines.append(lines[i])
        new_lines.append("")
        new_lines.append("---")
        new_lines.append("")

    # 写入
    new_content = "\n".join(new_lines)
    LOG_FILE.write_text(new_content, encoding="utf-8")
    print(f"原文件: {len(text.split(chr(10)))} 行")
    print(f"新文件: {len(new_lines)} 行")
    print(f"压缩率: {(1 - len(new_lines)/len(text.split(chr(10))))*100:.1f}%")
    print(f"保留: {len(keep_nums)} 个最近 R 节点完整内容")
    print(f"折叠: {len(sorted_nums) - len(keep_nums)} 个 R 节点为索引")


if __name__ == "__main__":
    main()