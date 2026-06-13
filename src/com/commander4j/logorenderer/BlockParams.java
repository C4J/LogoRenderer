package com.commander4j.logorenderer;

/**
 * Block-data wrap parameters for a T or S element, parsed from the
 * B&lt;maxLines&gt;,&lt;lineSpacing&gt;,&lt;indent&gt; triple in the field
 * definition. Present only when the field was defined to accept BD/UBD
 * payloads.
 *
 * lineSpacing and indent are both in dots for T and S fields.
 */
public record BlockParams(int maxLines, int lineSpacingDots, int indentDots) {}
