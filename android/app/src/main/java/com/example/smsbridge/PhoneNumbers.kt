package com.example.smsbridge

object PhoneNumbers {
    fun normalize(value: String): String {
        var digits = value.filter { it.isDigit() }
        if (digits.startsWith("00")) digits = digits.drop(2)
        return when {
            digits.length == 11 && digits.startsWith("8") -> "+7${digits.drop(1)}"
            digits.length == 11 && digits.startsWith("7") -> "+$digits"
            digits.length == 10 -> "+7$digits"
            digits.isNotEmpty() -> "+$digits"
            else -> ""
        }
    }

    fun masked(value: String): String {
        val normalized = normalize(value)
        return if (normalized.length >= 4) "...${normalized.takeLast(4)}" else "<unknown>"
    }
}
