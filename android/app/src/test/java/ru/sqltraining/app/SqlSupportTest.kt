package ru.sqltraining.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlSupportTest {
    @Test fun splitsMultipleStatements() {
        assertEquals(listOf("SELECT 1", "SELECT 2"), SqlScriptParser.split("SELECT 1; SELECT 2;\n"))
    }

    @Test fun preservesSemicolonsInsideStringsAndIdentifiers() {
        val statements = SqlScriptParser.split(
            "SELECT 'a;b', 'it''s ok', \"a;b\", [column;name] FROM trades;SELECT 3;"
        )
        assertEquals(2, statements.size)
        assertTrue(statements[0].contains("'a;b'"))
        assertTrue(statements[0].contains("[column;name]"))
    }

    @Test fun ignoresSemicolonsInsideComments() {
        val statements = SqlScriptParser.split(
            "SELECT 1; -- ignored ; comment\nSELECT /* ignored ; comment */ 2;"
        )
        assertEquals(2, statements.size)
        assertEquals("SELECT 1", statements[0])
        assertTrue(statements[1].contains("SELECT"))
        assertTrue(statements[1].contains("2"))
        assertFalse(statements[1].contains("ignored"))
    }

    @Test fun convertsTopToLimitAndRemovesSchemaPrefix() {
        val converted = TsqlLiteAdapter.convert("SELECT TOP (3) trade_id FROM dbo.trades ORDER BY trade_id")
        assertFalse(converted.contains("TOP", ignoreCase = true))
        assertTrue(converted.contains("LIMIT 3", ignoreCase = true))
        assertTrue(converted.contains("FROM trades"))
    }

    @Test fun supportsTopWithAndWithoutSpaces() {
        val a = TsqlLiteAdapter.convert(
            "SELECT TOP(3) trade_id,price FROM trades ORDER BY price DESC,trade_id"
        )
        val b = TsqlLiteAdapter.convert(
            "select\n top (3) trade_id,price FROM dbo.trades ORDER BY price DESC,trade_id"
        )
        assertTrue(a.contains("LIMIT 3", ignoreCase = true))
        assertTrue(b.contains("LIMIT 3", ignoreCase = true))
        assertFalse(a.contains("TOP(", ignoreCase = true))
        assertTrue(b.contains("FROM trades", ignoreCase = true))
    }

    @Test fun supportsDistinctWithTop() {
        val converted = TsqlLiteAdapter.convert(
            "SELECT DISTINCT TOP (2) side FROM trades ORDER BY side"
        )
        assertTrue(converted.contains("DISTINCT", ignoreCase = true))
        assertTrue(converted.contains("LIMIT 2", ignoreCase = true))
    }

    @Test fun skipsSqlServerSessionCommands() {
        val prepared = TsqlLiteAdapter.prepare("USE TradingTraining;\nGO\nSELECT 1;")
        assertEquals(listOf("SELECT 1"), SqlScriptParser.split(prepared))
    }

    @Test fun explainsConstraintViolation() {
        val explanation = SqlErrors.explain("CHECK constraint failed: quantity > 0")
        assertTrue(explanation.contains("CHECK"))
        assertTrue(explanation.contains("quantity > 0"))
    }
}
