package com.bytezone.dm3270.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.attributes.ColorAttribute;

import com.bytezone.dm3270.attributes.TerminalColor;

public class ContextManager
{
  private static final Logger logger = LoggerFactory.getLogger (ContextManager.class);

  private static final List<ScreenContext> contextPool = new ArrayList<> ();
  private FontMetrics fontMetrics;

  public ContextManager ()
  {
    addNewContext (ColorAttribute.colors[0], ColorAttribute.colors[8], (byte) 0, false);
  }

  public ScreenContext getDefaultScreenContext ()
  {
    return contextPool.get (0);
  }

  void setFontMetrics (FontMetrics fontMetrics)
  {
    this.fontMetrics = fontMetrics;
    contextPool.forEach (sc -> sc.setFontMetrics (fontMetrics));
  }

  public void dump ()
  {
    logger.debug ("");
    contextPool.forEach (sc -> logger.debug ("{}", sc));
  }

  public ScreenContext getScreenContext (TerminalColor foregroundColor,
      TerminalColor backgroundColor,
      byte highlight, boolean highIntensity)
  {
    Optional<ScreenContext> opt = contextPool.stream ().filter (sc -> sc
        .matches (foregroundColor, backgroundColor, highlight, highIntensity))
        .findFirst ();

    return opt.isPresent () ? opt.get ()
        : addNewContext (foregroundColor, backgroundColor, highlight, highIntensity);
  }

  public ScreenContext setForeground (ScreenContext oldContext,
      TerminalColor foregroundColor)
  {
    Optional<ScreenContext> opt = contextPool.stream ()
        .filter (sc -> sc.matches (foregroundColor, oldContext.backgroundColor,
                                   oldContext.highlight, oldContext.highIntensity))
        .findFirst ();

    return opt.isPresent () ? opt.get ()
        : addNewContext (foregroundColor, oldContext.backgroundColor,
                         oldContext.highlight, oldContext.highIntensity);
  }

  public ScreenContext setBackground (ScreenContext oldContext,
      TerminalColor backgroundColor)
  {
    Optional<ScreenContext> opt = contextPool.stream ()
        .filter (sc -> sc.matches (oldContext.foregroundColor, backgroundColor,
                                   oldContext.highlight, oldContext.highIntensity))
        .findFirst ();

    return opt.isPresent () ? opt.get ()
        : addNewContext (oldContext.foregroundColor, backgroundColor,
                         oldContext.highlight, oldContext.highIntensity);
  }

  public ScreenContext setHighlight (ScreenContext oldContext, byte highlight)
  {
    Optional<ScreenContext> opt =
        contextPool.stream ()
            .filter (sc -> sc.matches (oldContext.foregroundColor,
                                       oldContext.backgroundColor, highlight,
                                       oldContext.highIntensity))
            .findFirst ();

    return opt.isPresent () ? opt.get ()
        : addNewContext (oldContext.foregroundColor, oldContext.backgroundColor,
                         highlight, oldContext.highIntensity);
  }

  public ScreenContext setHighIntensity (ScreenContext oldContext, boolean highIntensity)
  {
    Optional<ScreenContext> opt =
        contextPool.stream ()
            .filter (sc -> sc.matches (oldContext.foregroundColor,
                                       oldContext.backgroundColor, oldContext.highlight,
                                       highIntensity))
            .findFirst ();

    return opt.isPresent () ? opt.get ()
        : addNewContext (oldContext.foregroundColor, oldContext.backgroundColor,
                         oldContext.highlight, highIntensity);
  }

  private ScreenContext addNewContext (TerminalColor foregroundColor,
      TerminalColor backgroundColor,
      byte highlight, boolean highIntensity)
  {
    ScreenContext newContext = new ScreenContext (foregroundColor, backgroundColor,
        highlight, highIntensity, fontMetrics);
    contextPool.add (newContext);
    return newContext;
  }
}