package com.bytezone.dm3270.buffers;

import com.bytezone.dm3270.display.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultBuffer extends AbstractBuffer
{
  private static final Logger logger = LoggerFactory.getLogger (DefaultBuffer.class);

  public DefaultBuffer (byte[] buffer)
  {
    super (buffer);
  }

  @Override
  public void process (Screen screen)
  {
    logger.warn ("Nothing to process");
  }

  @Override
  public String toString ()
  {
    return "DefaultBuffer";
  }
}