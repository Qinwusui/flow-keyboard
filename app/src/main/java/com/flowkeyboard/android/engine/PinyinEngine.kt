package com.flowkeyboard.android.engine

import com.flowkeyboard.android.model.DictionaryItem
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bounded, tone-free pinyin composition. Apostrophes separate ambiguous syllables. */
class PinyinEngine {
    private val buffer = MutableStateFlow("")
    val composing = buffer.asStateFlow()
    val text: String get() = buffer.value
    val searchPrefix: String get() = text.replace("'", "")

    fun append(character: Char): Boolean {
        val normalized = character.lowercaseChar()
        if (text.length >= MAX_LENGTH) return false
        if (normalized !in 'a'..'z' && normalized != '\'') return false
        if (normalized == '\'' && (text.isEmpty() || text.endsWith("'"))) return false
        buffer.value += normalized
        return true
    }

    fun backspace(): Boolean {
        if (text.isEmpty()) return false
        buffer.value = text.dropLast(1)
        return true
    }

    fun clear(): String = text.also { buffer.value = "" }

    fun matches(candidate: DictionaryItem): Boolean {
        val prefix = searchPrefix
        return prefix.isNotEmpty() && candidate.pinyin.lowercase(Locale.ROOT).replace("'", "").startsWith(prefix)
    }

    /** Returns full syllables plus an optional incomplete last syllable, or null for invalid input. */
    fun syllables(): List<String>? {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        for (part in text.split('\'')) {
            if (part.isEmpty()) continue
            val paths = arrayOfNulls<List<String>>(part.length + 1)
            paths[0] = emptyList()
            for (end in 1..part.length) {
                for (start in (end - 6).coerceAtLeast(0) until end) {
                    val previous = paths[start] ?: continue
                    val value = part.substring(start, end)
                    if (value in VALID_SYLLABLES || (end == part.length && VALID_SYLLABLES.any { it.startsWith(value) })) {
                        paths[end] = previous + value
                        break
                    }
                }
            }
            result += paths[part.length] ?: return null
        }
        return result
    }

    companion object {
        const val MAX_LENGTH = 64
        private val VALID_SYLLABLES = ("a ai an ang ao ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu " +
            "ca cai can cang cao ce cen ceng cha chai chan chang chao che chen cheng chi chong chou chu chua chuai chuan chuang chui chun chuo ci cong cou cu cuan cui cun cuo " +
            "da dai dan dang dao de dei den deng di dia dian diao die ding diu dong dou du duan dui dun duo " +
            "e ei en eng er fa fan fang fei fen feng fo fou fu ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo " +
            "ha hai han hang hao he hei hen heng hong hou hu hua huai huan huang hui hun huo " +
            "ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun ka kai kan kang kao ke ken keng kong kou ku kua kuai kuan kuang kui kun kuo " +
            "la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu long lou lu luan lun luo lv lve " +
            "ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu na nai nan nang nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou nu nuan nuo nv nve " +
            "o ou pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun " +
            "ran rang rao re ren reng ri rong rou ru ruan rui run ruo sa sai san sang sao se sen seng sha shai shan shang shao she shei shen sheng shi shou shu shua shuai shuan shuang shui shun shuo si song sou su suan sui sun suo " +
            "ta tai tan tang tao te teng ti tian tiao tie ting tong tou tu tuan tui tun tuo wa wai wan wang wei wen weng wo wu " +
            "xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun ya yan yang yao ye yi yin ying yo yong you yu yuan yue yun " +
            "za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei zhen zheng zhi zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo").split(' ').toSet()
    }
}
