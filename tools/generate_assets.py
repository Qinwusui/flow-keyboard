#!/usr/bin/env python3
"""Rebuild offline assets. Install pypinyin==0.55.0 and wordfreq==3.1.1 first."""
from pathlib import Path
import math
import random
import struct
import wave

from pypinyin import Style, lazy_pinyin
from wordfreq import top_n_list

ROOT = Path(__file__).resolve().parents[1]
MANUAL = ["你好", "中国", "我们", "世界", "谢谢", "再见", "输入法", "传送带", "北京", "上海", "中文", "拼音",
          "今天", "明天", "朋友", "学习", "工作", "生活", "手机", "电脑", "可以", "时间", "喜欢", "快乐", "早上",
          "晚上", "早上好", "晚安", "你们", "他们"]
words = []
for word in MANUAL + top_n_list("zh", 10_000):
    if word in words or not 1 <= len(word) <= 4 or not all("\u4e00" <= c <= "\u9fff" for c in word):
        continue
    syllables = lazy_pinyin(word, style=Style.NORMAL, errors="ignore")
    if len(syllables) != len(word) or any(not s.isascii() or not s.isalpha() for s in syllables):
        continue
    words.append(word)
    if len(words) == 1_000:
        break
assert len(words) == 1_000
rows = ["# word\tpinyin (tone-free, v for ü)\tinitials\tbase weight"]
for index, word in enumerate(words):
    syllables = [s.replace("ü", "v") for s in lazy_pinyin(word, style=Style.NORMAL)]
    rows.append("\t".join([word, "".join(syllables), "".join(s[0] for s in syllables), str(max(1, 50 - index // 20))]))
(ROOT / "app/src/main/assets/dictionary.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")

for name, duration, frequency in [("key_press", .045, 2200), ("belt_tick", .018, 1400)]:
    rng = random.Random(42)
    rate = 22050
    samples = []
    for index in range(int(rate * duration)):
        t = index / rate
        value = (math.sin(2 * math.pi * frequency * t) * .3 + (rng.random() * 2 - 1) * .7) * math.exp(-t * 150)
        samples.append(struct.pack("<h", int(value * 19000)))
    with wave.open(str(ROOT / f"app/src/main/res/raw/{name}.wav"), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(rate)
        output.writeframes(b"".join(samples))
