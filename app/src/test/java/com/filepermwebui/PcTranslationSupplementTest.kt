package com.lcpatch

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PcTranslationSupplementTest {
    @Test fun addsMissingIdsOnlyInIdAddressedResourceLists() {
        val target = JSONObject("""{"dataList":[{"id":"a","content":"既有中文"}],"other":[{"id":"a","content":"既有中文"}]}""")
        val reference = JSONObject("""{"dataList":[{"id":"a","content":"原譯"},{"id":"b","content":"新增譯文"}],"other":[{"id":"b","content":"不應加入"}]}""")
        val report = CoverageReport()
        supplementJson(target, reference, report)
        assertEquals(2, target.getJSONArray("dataList").length())
        assertEquals(1, target.getJSONArray("other").length())
        assertEquals(1, report.addedRecords)
    }
    @Test fun matchesIdsPreservesChineseAndNeverReplacesByPosition() {
        val target = JSONObject("""{"dataList":[{"id":"b","content":"Already English"},{"id":"a","content":"既有譯文"},{"id":"c","content":"日本語の文章"}]}""")
        val pc = JSONObject("""{"dataList":[{"id":"a","content":"不同中文"},{"id":"b","content":"補齊中文"}]}""")
        val report = CoverageReport()
        supplementJson(target, pc, report)
        val rows = target.getJSONArray("dataList")
        assertEquals("補齊中文", rows.getJSONObject(0).getString("content"))
        assertEquals("既有譯文", rows.getJSONObject(1).getString("content"))
        assertEquals("日本語の文章", rows.getJSONObject(2).getString("content"))
        assertEquals(1, report.filledFields)
        assertEquals(1, report.unresolvedFields)
    }
    @Test fun blocksPlaceholderChangesAndAmbiguousIds() {
        val target = JSONObject("""{"dataList":[{"id":1,"content":"Download {0}"},{"id":2,"content":"Other"}]}""")
        val pc = JSONObject("""{"dataList":[{"id":1,"content":"下載 {1}"},{"id":2,"content":"甲"},{"id":2,"content":"乙"}]}""")
        val report = CoverageReport()
        supplementJson(target, pc, report)
        assertEquals("Download {0}", target.getJSONArray("dataList").getJSONObject(0).getString("content"))
        assertEquals(0, report.filledFields)
        assertEquals(1, report.placeholderConflicts)
    }
}
