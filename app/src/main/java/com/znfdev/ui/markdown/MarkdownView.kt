package com.znfdev.ui.markdown

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.core.markdown.MdAlign
import com.znfdev.core.markdown.MdBlock
import com.znfdev.core.markdown.MdInline
import com.znfdev.core.markdown.MdParser
import com.znfdev.ui.components.ZevIcon
import com.znfdev.ui.components.rememberRemoteImage
import com.znfdev.ui.editor.LuaHighlight
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevShapes

/** Renders Markdown (see [MdParser]) with the app's look. Links open in the browser. */
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = ZevColors.Text,
    fontSize: TextUnit = 14.sp
) {
    val blocks = remember(markdown) { MdParser.parse(markdown) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (b in blocks) MdBlockView(b, color, fontSize)
    }
}

private class Ink(val text: Color, val link: Color, val codeBg: Color, val codeText: Color)

private fun inkFor(color: Color) = Ink(
    text = color,
    link = ZevColors.Primary,
    codeBg = Color(0x33FFFFFF),
    codeText = ZevColors.Gold
)

@Composable
private fun MdBlockView(block: MdBlock, color: Color, fontSize: TextUnit) {
    val ink = remember(color) { inkFor(color) }
    when (block) {
        is MdBlock.Heading -> {
            val scale = when (block.level) { 1 -> 1.5f; 2 -> 1.3f; 3 -> 1.18f; 4 -> 1.08f; else -> 1f }
            Text(
                text = inline(block.content, ink),
                color = color,
                fontSize = fontSize * scale,
                lineHeight = fontSize * scale * 1.25f,
                fontWeight = FontWeight.Bold
            )
        }
        is MdBlock.Paragraph -> Text(
            text = inline(block.content, ink),
            color = color,
            fontSize = fontSize,
            lineHeight = fontSize * 1.45f
        )
        is MdBlock.Code -> CodeBlock(block, fontSize)
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(ZevColors.Primary.copy(alpha = 0.7f))
            )
            Column(
                Modifier.padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (b in block.blocks) MdBlockView(b, color.copy(alpha = 0.85f), fontSize)
            }
        }
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top) {
                    val marker = when {
                        item.checked != null -> null
                        block.ordered -> "${block.start + index}."
                        else -> "\u2022"
                    }
                    if (item.checked != null) {
                        ZevIcon(
                            if (item.checked) "check_box" else "check_box_outline_blank",
                            size = (fontSize.value * 1.25f).dp,
                            tint = if (item.checked) ZevColors.Green else ZevColors.TextDim
                        )
                    } else {
                        Text(
                            marker.orEmpty(),
                            color = ZevColors.TextDim,
                            fontSize = fontSize,
                            modifier = Modifier.width(if (block.ordered) 24.dp else 16.dp)
                        )
                    }
                    Column(
                        Modifier.padding(start = if (item.checked != null) 6.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val single = item.blocks.singleOrNull()
                        if (single is MdBlock.Paragraph) {
                            Text(
                                text = inline(single.content, ink),
                                color = if (item.checked == true) color.copy(alpha = 0.6f) else color,
                                fontSize = fontSize,
                                lineHeight = fontSize * 1.4f,
                                textDecoration = if (item.checked == true) TextDecoration.LineThrough else null
                            )
                        } else {
                            for (b in item.blocks) MdBlockView(b, color, fontSize)
                        }
                    }
                }
            }
        }
        is MdBlock.Table -> TableBlock(block, ink, fontSize)
        is MdBlock.Image -> ImageBlock(block)
        MdBlock.Rule -> Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ZevColors.Outline)
        )
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code, fontSize: TextUnit) {
    val text = remember(block) {
        if (block.lang.equals("lua", ignoreCase = true)) LuaHighlight.annotate(block.code, null)
        else AnnotatedString(block.code)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ZevShapes.Small)
            .background(ZevColors.Editor)
    ) {
        if (block.lang.isNotBlank()) {
            Text(
                block.lang,
                color = ZevColors.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp)
            )
        }
        Text(
            text = text,
            color = ZevColors.CodeText,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize * 0.88f,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        )
    }
}

@Composable
private fun TableBlock(table: MdBlock.Table, ink: Ink, fontSize: TextUnit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, ZevColors.Outline, shape)
    ) {
        TableRow(table.header, table.aligns, ink, fontSize, header = true)
        for (row in table.rows) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ZevColors.Outline)
            )
            TableRow(row, table.aligns, ink, fontSize, header = false)
        }
    }
}

@Composable
private fun TableRow(
    cells: List<List<MdInline>>,
    aligns: List<MdAlign>,
    ink: Ink,
    fontSize: TextUnit,
    header: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (header) Color(0x14FFFFFF) else Color.Transparent)
    ) {
        cells.forEachIndexed { i, cell ->
            Text(
                text = inline(cell, ink),
                color = ink.text,
                fontSize = fontSize * 0.92f,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                textAlign = when (aligns.getOrNull(i)) {
                    MdAlign.CENTER -> TextAlign.Center
                    MdAlign.END -> TextAlign.End
                    else -> TextAlign.Start
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun ImageBlock(block: MdBlock.Image) {
    val bitmap by rememberRemoteImage(block.url)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(ZevShapes.Small)
            .background(ZevColors.PanelRaised)
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = block.alt,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                Text(block.alt.ifBlank { "Loading image\u2026" }, color = ZevColors.TextDim, fontSize = 12.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ inline -> AnnotatedString

private fun inline(nodes: List<MdInline>, ink: Ink): AnnotatedString =
    buildAnnotatedString { appendNodes(nodes, ink) }

private fun AnnotatedString.Builder.appendNodes(nodes: List<MdInline>, ink: Ink) {
    for (n in nodes) {
        when (n) {
            is MdInline.Text -> append(n.text)
            is MdInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendNodes(n.children, ink) }
            is MdInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendNodes(n.children, ink) }
            is MdInline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendNodes(n.children, ink)
            }
            is MdInline.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = ink.codeBg, color = ink.codeText)
            ) { append("\u00A0" + n.text + "\u00A0") }
            is MdInline.Link -> withLink(
                LinkAnnotation.Url(
                    n.url,
                    TextLinkStyles(SpanStyle(color = ink.link, textDecoration = TextDecoration.Underline))
                )
            ) { appendNodes(n.children, ink) }
            is MdInline.Image -> withStyle(SpanStyle(color = ink.link)) {
                append(n.alt.ifBlank { "image" })
            }
            MdInline.LineBreak -> append("\n")
        }
    }
}
