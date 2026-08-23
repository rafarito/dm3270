package com.bytezone.dm3270.display;

import com.bytezone.dm3270.attributes.TerminalColor;

import javafx.scene.canvas.GraphicsContext;

/*
 * Liga ScreenCanvas ao GraphicsContext do JavaFX.
 *
 * Delegacao pura: cada metodo repassa a chamada correspondente, na mesma ordem e com os
 * mesmos argumentos que ScreenPosition fazia direto no GraphicsContext. A unica traducao e
 * a da cor, via FxPalette, que reconstroi exatamente a cor de antes - ver
 * PaletteFidelityTest.
 *
 * Nao ha logica aqui de proposito: qualquer decisao neste adaptador seria uma diferenca de
 * comportamento entre o desenho de antes e o de agora.
 */
// -----------------------------------------------------------------------------------//
public final class FxScreenCanvas implements ScreenCanvas
// -----------------------------------------------------------------------------------//
{
  private final GraphicsContext gc;

  // ---------------------------------------------------------------------------------//
  public FxScreenCanvas (GraphicsContext gc)
  // ---------------------------------------------------------------------------------//
  {
    this.gc = gc;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void setFill (TerminalColor color)
  // ---------------------------------------------------------------------------------//
  {
    gc.setFill (FxPalette.toFx (color));
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void setStroke (TerminalColor color)
  // ---------------------------------------------------------------------------------//
  {
    gc.setStroke (FxPalette.toFx (color));
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void fillRect (double x, double y, double width, double height)
  // ---------------------------------------------------------------------------------//
  {
    gc.fillRect (x, y, width, height);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void fillText (String text, double x, double y)
  // ---------------------------------------------------------------------------------//
  {
    gc.fillText (text, x, y);
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void strokeLine (double x1, double y1, double x2, double y2)
  // ---------------------------------------------------------------------------------//
  {
    gc.strokeLine (x1, y1, x2, y2);
  }
}
