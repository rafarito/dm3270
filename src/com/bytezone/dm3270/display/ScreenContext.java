package com.bytezone.dm3270.display;

import com.bytezone.dm3270.attributes.ColorAttribute;

import com.bytezone.dm3270.attributes.TerminalColor;

// -----------------------------------------------------------------------------------//
public class ScreenContext
// -----------------------------------------------------------------------------------//
{
  final public TerminalColor foregroundColor;
  final public TerminalColor backgroundColor;
  final public byte highlight;
  final public boolean highIntensity;

  final public boolean underscore;
  final public boolean reverseVideo;
  final public boolean blink;

  FontMetrics fontMetrics;

  // ---------------------------------------------------------------------------------//
  public ScreenContext (TerminalColor foregroundColor, TerminalColor backgroundColor,
      byte highlight, boolean highIntensity, FontMetrics fontMetrics)
  // ---------------------------------------------------------------------------------//
  {
    this.foregroundColor = foregroundColor;
    this.backgroundColor = backgroundColor;
    this.highlight = highlight;
    this.highIntensity = highIntensity;

    this.fontMetrics = fontMetrics;

    this.underscore = highlight == (byte) 0xF4;
    this.reverseVideo = highlight == (byte) 0xF2;
    this.blink = highlight == (byte) 0xF1;
    //    this.normalHighlight = highlight == (byte) 0xF0;
  }

  // ---------------------------------------------------------------------------------//
  public boolean matches (ScreenContext other)
  // ---------------------------------------------------------------------------------//
  {
    return foregroundColor == other.foregroundColor
        && backgroundColor == other.backgroundColor     //
        && highlight == other.highlight                 //
        && highIntensity == other.highIntensity;
  }

  // ---------------------------------------------------------------------------------//
  public boolean matches (TerminalColor foregroundColor, TerminalColor backgroundColor,
      byte highlight, boolean highIntensity)
  // ---------------------------------------------------------------------------------//
  {
    return this.foregroundColor == foregroundColor
        && this.backgroundColor == backgroundColor     //
        && this.highlight == highlight                 //
        && this.highIntensity == highIntensity;
  }

  // ---------------------------------------------------------------------------------//
  public void setFontMetrics (FontMetrics fontMetrics)
  // ---------------------------------------------------------------------------------//
  {
    this.fontMetrics = fontMetrics;
  }

  // ---------------------------------------------------------------------------------//
  public FontMetrics getFontMetrics ()
  // ---------------------------------------------------------------------------------//
  {
    return fontMetrics;
  }

  // ---------------------------------------------------------------------------------//
  public void setFontData (FontMetrics fontMetrics)
  // ---------------------------------------------------------------------------------//
  {
    this.fontMetrics = fontMetrics;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    String name = fontMetrics == null ? "" : fontMetrics.name ();
    return String.format ("[Fg:%-10s Bg:%-10s In:%s  Hl:%02X, f:%s]",
        ColorAttribute.getName (foregroundColor),
        ColorAttribute.getName (backgroundColor), (highIntensity ? 'x' : ' '), highlight,
        name);
  }
}