// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox

import app.mise.recipebox.share.DocumentAnalyzer
import app.mise.recipebox.share.DocumentKind
import app.mise.recipebox.share.PositionedDocumentText
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentAnalyzerTest {
    @Test fun recognizesSupportedDocumentTypes() {
        assertEquals(DocumentKind.PDF, DocumentAnalyzer.documentKind("application/pdf", "recipe"))
        assertEquals(DocumentKind.DOCX, DocumentAnalyzer.documentKind("", "recipe.DOCX"))
        assertEquals(DocumentKind.TEXT, DocumentAnalyzer.documentKind("text/markdown", "notes"))
        assertEquals(DocumentKind.TEXT, DocumentAnalyzer.documentKind("", "recipe.csv"))
    }

    @Test fun extractsParagraphsAndFormattingFromDocxXml() {
        val xml = """<w:document><w:body><w:p><w:r><w:t>Tom &amp; basil pasta</w:t></w:r></w:p><w:p><w:r><w:t>Ingredients</w:t></w:r><w:br/><w:r><w:t>2 cups tomatoes</w:t></w:r></w:p></w:body></w:document>"""

        assertEquals("Tom & basil pasta\nIngredients\n2 cups tomatoes", DocumentAnalyzer.docxXmlToText(xml))
    }

    @Test fun derivesReadableTitleHintFromDocumentName() {
        assertEquals("HEALTHY BIRYANI RECIPE", DocumentAnalyzer.titleHint("HEALTHY-BIRYANI_RECIPE.pdf"))
    }

    @Test fun restoresPdfTextToVisualReadingOrder() {
        val chunks = listOf(
            PositionedDocumentText("@creator Page 2", 790f, 430f),
            PositionedDocumentText("60 g", 180f, 520f),
            PositionedDocumentText("Chocolate Protein Mug Cake", 70f, 55f),
            PositionedDocumentText("Banana", 181f, 80f),
            PositionedDocumentText("Ingredients", 135f, 55f),
        )

        assertEquals(
            "Chocolate Protein Mug Cake\nIngredients\nBanana\n60 g\n@creator Page 2",
            DocumentAnalyzer.orderedPageText(chunks),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsUnsupportedDocuments() {
        DocumentAnalyzer.documentKind("application/msword", "recipe.doc")
    }
}
