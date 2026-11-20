package com.bytezone.dm3270.display;

import com.bytezone.dm3270.attributes.TerminalColor;
import com.bytezone.dm3270.screen.ScreenCanvas;

import javafx.scene.canvas.GraphicsContext;

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
