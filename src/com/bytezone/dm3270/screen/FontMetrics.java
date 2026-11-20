package com.bytezone.dm3270.screen;

/**
 * Dimensoes pre-medidas da fonte, sem depender do toolkit JavaFX.
 * {@code name} guarda o nome resolvido por Font.getName, usado em ScreenContext.toString.
 */
// -----------------------------------------------------------------------------------//
public record FontMetrics (String name, int width, int height, int ascent)
// -----------------------------------------------------------------------------------//
{
}
