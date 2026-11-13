package com.saleh.alazzi.grocery.domain.voice

import com.saleh.alazzi.grocery.data.local.entity.ItemEntity
import java.math.BigDecimal

data class ParsedVoiceLine(
    val rawTranscript: String,
    val itemQuery: String,
    val matchedItem: ItemEntity?,
    val totalAmountInput: String,
    val quantityInput: String
)

/**
 * Parses Arabic/mixed voice transcripts such as:
 * - "عصير كرتون 4000 الكمية 2"
 * - "سكر ١٢٠٠٠ العدد ٣"
 * - "أرز 25000 عدد 1"
 * - "الكمية 2.5 زيت 7500"
 */
object VoiceInvoiceParser {
    private val qtyKeywords = setOf("الكمية", "كمية", "العدد", "عدد", "qty", "quantity")

    fun normalizeNumerals(input: String): String = input.map { ch ->
        when (ch) {
            in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
            in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
            '٫' -> '.'
            '٬', ',' -> ' '
            else -> ch
        }
    }.joinToString("")

    fun parse(transcript: String, availableItems: List<ItemEntity>): ParsedVoiceLine {
        val cleaned = normalizeNumerals(transcript)
            .replace(Regex("[،؛؟!]"), " ")
            .replace(Regex("(?<!\\d)\\.(?!\\d)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isBlank()) {
            return ParsedVoiceLine(transcript, "", null, "", "1")
        }

        val tokens = cleaned.split(" ")
        var explicitQuantity: String? = null
        val consumedIndices = mutableSetOf<Int>()

        // 1) Look for quantity right after a quantity keyword ("الكمية 2", "عدد 3")
        for (i in tokens.indices) {
            val token = tokens[i]
            if (token in qtyKeywords && i + 1 < tokens.size && isPositiveDecimal(tokens[i + 1])) {
                explicitQuantity = cleanNumberString(tokens[i + 1])
                consumedIndices += i
                consumedIndices += i + 1
                break
            }
        }

        // 2) Collect remaining numeric tokens
        val remainingNumbers = mutableListOf<Pair<Int, String>>()
        for (i in tokens.indices) {
            if (i in consumedIndices) continue
            if (isPositiveDecimal(tokens[i])) {
                remainingNumbers += i to cleanNumberString(tokens[i])
            }
        }

        var totalAmount = ""
        var quantity = explicitQuantity ?: "1"

        if (explicitQuantity != null) {
            // The largest remaining whole-ish number is the total amount
            val picked = remainingNumbers.maxByOrNull { (_, num) -> num.toBigDecimalOrNull() ?: BigDecimal.ZERO }
            if (picked != null) {
                totalAmount = stripDecimalIfWhole(picked.second)
                consumedIndices += picked.first
            }
        } else if (remainingNumbers.size >= 2) {
            // Heuristic: if two numbers without keyword, usually larger is total amount, smaller is quantity
            val sorted = remainingNumbers.sortedByDescending { it.second.toBigDecimalOrNull() ?: BigDecimal.ZERO }
            totalAmount = stripDecimalIfWhole(sorted.first().second)
            quantity = sorted.last().second
            consumedIndices += sorted.first().first
            consumedIndices += sorted.last().first
        } else if (remainingNumbers.size == 1) {
            totalAmount = stripDecimalIfWhole(remainingNumbers.first().second)
            consumedIndices += remainingNumbers.first().first
        }

        // Remove standalone trailing/leading quantity filler words
        val nameTokens = tokens.filterIndexed { index, token ->
            index !in consumedIndices && token !in setOf("الكمية", "كمية", "العدد", "عدد", "ريال", "بسعر", "ب")
        }
        val itemQuery = nameTokens.joinToString(" ").trim()
        val matched = findBestItemMatch(itemQuery, availableItems)

        return ParsedVoiceLine(
            rawTranscript = transcript,
            itemQuery = itemQuery,
            matchedItem = matched,
            totalAmountInput = totalAmount,
            quantityInput = quantity
        )
    }

    private fun findBestItemMatch(query: String, items: List<ItemEntity>): ItemEntity? {
        if (query.isBlank()) return null
        val q = query.trim()
        items.firstOrNull { it.name.equals(q, ignoreCase = true) }?.let { return it }
        items.firstOrNull { it.name.contains(q, ignoreCase = true) }?.let { return it }
        val words = q.split(" ").filter { it.length >= 2 }
        if (words.isEmpty()) return null
        return items.maxByOrNull { item ->
            words.count { w -> item.name.contains(w, ignoreCase = true) }
        }?.takeIf { item -> words.any { w -> item.name.contains(w, ignoreCase = true) } }
    }

    private fun isPositiveDecimal(token: String): Boolean {
        val bd = token.toBigDecimalOrNull() ?: return false
        return bd > BigDecimal.ZERO
    }

    private fun cleanNumberString(token: String): String {
        val bd = token.toBigDecimalOrNull() ?: return token
        return bd.stripTrailingZeros().toPlainString()
    }

    private fun stripDecimalIfWhole(token: String): String {
        val bd = token.toBigDecimalOrNull() ?: return token
        return bd.toBigInteger().toString()
    }
}
