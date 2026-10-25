package com.askdb.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlExtractorTest {

    @Test
    void 取出sql代码块() {
        String reply = "好的：\n```sql\nSELECT COUNT(*) FROM orders;\n```\n以上。";
        assertEquals("SELECT COUNT(*) FROM orders", SqlExtractor.extract(reply));
    }

    @Test
    void 代码块没写语言也能取出() {
        assertEquals("SELECT 1", SqlExtractor.extract("```\nSELECT 1\n```"));
    }

    @Test
    void 其他语言标记会被去掉() {
        assertEquals("SELECT 4", SqlExtractor.extract("```mysql\nSELECT 4\n```"));
        assertEquals("SELECT 5", SqlExtractor.extract("```SELECT 5```"));
    }

    @Test
    void 有多个代码块时优先取sql代码块() {
        String reply = "```text\n说明\n```\n```SQL\nSELECT 2\n```";
        assertEquals("SELECT 2", SqlExtractor.extract(reply));
    }

    @Test
    void 没有代码块时整段当作sql() {
        assertEquals("SELECT 3", SqlExtractor.extract("  SELECT 3 ;; \n"));
    }

    @Test
    void 空回复报错() {
        assertThrows(IllegalArgumentException.class, () -> SqlExtractor.extract("  "));
        assertThrows(IllegalArgumentException.class, () -> SqlExtractor.extract("```sql\n;\n```"));
    }
}
