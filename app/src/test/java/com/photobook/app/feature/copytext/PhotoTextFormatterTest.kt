package com.photobook.app.feature.copytext

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhotoTextFormatterTest {

    @Test
    fun format_preservesCaseAndLineBreaks() {
        val formatter = PhotoTextFormatter(maxChars = 200)

        val raw = "Invoice 123\r\nTOTAL\tAMOUNT\r\n$42.00"
        val formatted = formatter.format(raw)

        assertThat(formatted).isEqualTo("Invoice 123\nTOTAL AMOUNT\n$42.00")
    }

    @Test
    fun format_trimsNoiseAndCapsLength() {
        val formatter = PhotoTextFormatter(maxChars = 10)

        val raw = "line  one\n\n\nline two    "
        val formatted = formatter.format(raw)

        assertThat(formatted).isEqualTo("line one")
    }
}
