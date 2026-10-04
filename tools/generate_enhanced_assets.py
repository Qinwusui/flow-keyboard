#!/usr/bin/env python3
"""Build enhanced dictionary and offline bigram transition assets from rime-frost and word frequencies."""
from pathlib import Path
import math
from collections import defaultdict
from pypinyin import Style, lazy_pinyin

ROOT = Path(__file__).resolve().parents[1]
ASSETS_DIR = ROOT / "app/src/main/assets"
FROST_DIR = Path("/tmp/rime-frost")

def build():
    print("Loading current dictionary.tsv...")
    current_entries = []
    seen_words = set()
    current_dict_path = ASSETS_DIR / "dictionary.tsv"
    
    with open(current_dict_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) == 4:
                current_entries.append(parts)
                seen_words.add(parts[0])

    print(f"Existing dictionary entries: {len(current_entries)}")

    # 1. Add Idioms from rime-frost
    added_idioms = []
    idiom_path = FROST_DIR / "cn_dicts_cell/idiom.dict.yaml"
    if idiom_path.exists():
        with open(idiom_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or line.startswith("---") or line.startswith("..."):
                    continue
                parts = line.split("\t")
                if len(parts) >= 2:
                    word = parts[0]
                    if len(word) == 4 and all("\u4e00" <= c <= "\u9fff" for c in word):
                        weight = int(parts[2]) if len(parts) >= 3 and parts[2].isdigit() else 0
                        if weight >= 5 and word not in seen_words:
                            syllables = [s.replace("ü", "v") for s in lazy_pinyin(word, style=Style.NORMAL)]
                            if len(syllables) == 4 and all(s.isascii() and s.isalpha() for s in syllables):
                                py = "".join(syllables)
                                init = "".join(s[0] for s in syllables)
                                base_weight = min(40, max(15, 15 + weight // 50))
                                added_idioms.append([word, py, init, str(base_weight)])
                                seen_words.add(word)
    print(f"Added {len(added_idioms)} high-frequency idioms from rime-frost.")

    # 2. Add high-frequency daily phrases from 知频.txt
    added_zhihu = []
    zhihu_path = FROST_DIR / "others/知频.txt"
    if zhihu_path.exists():
        with open(zhihu_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                parts = line.split("\t")
                if len(parts) >= 2:
                    word = parts[0]
                    if 2 <= len(word) <= 4 and all("\u4e00" <= c <= "\u9fff" for c in word):
                        freq = int(parts[1]) if parts[1].isdigit() else 0
                        if freq >= 2000 and word not in seen_words:
                            syllables = [s.replace("ü", "v") for s in lazy_pinyin(word, style=Style.NORMAL)]
                            if len(syllables) == len(word) and all(s.isascii() and s.isalpha() for s in syllables):
                                py = "".join(syllables)
                                init = "".join(s[0] for s in syllables)
                                base_weight = min(45, max(15, int(math.log10(freq) * 7)))
                                added_zhihu.append([word, py, init, str(base_weight)])
                                seen_words.add(word)
    print(f"Added {len(added_zhihu)} high-frequency colloquial phrases from 知频.")

    # 3. Add high-frequency base words from base.dict.yaml
    added_base = []
    base_path = FROST_DIR / "cn_dicts/base.dict.yaml"
    if base_path.exists():
        with open(base_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or line.startswith("---") or line.startswith("..."):
                    continue
                parts = line.split("\t")
                if len(parts) >= 3 and parts[2].isdigit():
                    word = parts[0]
                    weight = int(parts[2])
                    if 2 <= len(word) <= 4 and all("\u4e00" <= c <= "\u9fff" for c in word):
                        if weight >= 800 and word not in seen_words:
                            syllables = [s.replace("ü", "v") for s in lazy_pinyin(word, style=Style.NORMAL)]
                            if len(syllables) == len(word) and all(s.isascii() and s.isalpha() for s in syllables):
                                py = "".join(syllables)
                                init = "".join(s[0] for s in syllables)
                                base_weight = min(40, max(15, 15 + weight // 500))
                                added_base.append([word, py, init, str(base_weight)])
                                seen_words.add(word)
    print(f"Added {len(added_base)} base vocabulary terms from rime-frost base.")

    # Combine all dictionary entries
    all_entries = current_entries + added_idioms + added_zhihu + added_base
    print(f"Total dictionary entries: {len(all_entries)}")

    # Write dictionary.tsv
    rows = ["# word\tpinyin (tone-free, v for ü)\tinitials\tbase weight"]
    for entry in all_entries:
        rows.append("\t".join(entry))
    current_dict_path.write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"Saved enhanced {current_dict_path}")

    # 4. Generate Bigram offline language model transitions
    all_dict_words = set(e[0] for e in all_entries)
    transitions = defaultdict(lambda: defaultdict(int))
    if zhihu_path.exists():
        with open(zhihu_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                parts = line.split("\t")
                if len(parts) >= 2:
                    phrase, freq_str = parts[0], parts[1]
                    freq = int(freq_str) if freq_str.isdigit() else 0
                    if freq < 200:
                        continue
                    if len(phrase) == 4:
                        w1, w2 = phrase[:2], phrase[2:]
                        if w1 in all_dict_words and w2 in all_dict_words:
                            transitions[w1][w2] += freq
                    elif len(phrase) == 3:
                        w1, w2 = phrase[:1], phrase[1:]
                        if w1 in all_dict_words and w2 in all_dict_words:
                            transitions[w1][w2] += freq
                        w1, w2 = phrase[:2], phrase[2:]
                        if w1 in all_dict_words and w2 in all_dict_words:
                            transitions[w1][w2] += freq

    transition_rows = ["# fromWord\ttoWord\tweight"]
    for from_w, to_dict in transitions.items():
        sorted_pairs = sorted(to_dict.items(), key=lambda x: x[1], reverse=True)[:6]
        for to_w, freq in sorted_pairs:
            weight = min(100, max(10, int(math.log10(freq) * 20)))
            transition_rows.append(f"{from_w}\t{to_w}\t{weight}")

    transitions_path = ASSETS_DIR / "transitions.tsv"
    transitions_path.write_text("\n".join(transition_rows) + "\n", encoding="utf-8")
    print(f"Generated {len(transition_rows)-1} Bigram transitions -> {transitions_path}")

if __name__ == "__main__":
    build()
