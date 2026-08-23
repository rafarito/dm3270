package com.bytezone.dm3270.structuredfields;

import com.bytezone.dm3270.screen.ScreenTarget;
import com.bytezone.dm3270.utilities.Dm3270Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// -----------------------------------------------------------------------------------//
public class DefaultStructuredField extends StructuredField
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DefaultStructuredField.class);

  // ---------------------------------------------------------------------------------//
  public DefaultStructuredField (byte[] buffer, int offset, int length)
  // ---------------------------------------------------------------------------------//
  {
    super (buffer, offset, length);
    logger.debug ("Default Structured Field !!");
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public void process (ScreenTarget screen)
  // ---------------------------------------------------------------------------------//
  {
    logger.debug ("Processing a DefaultStructuredField: {}", String.format ("%02X", type));
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();
    text.append (String.format ("Unknown SF   : %02X%n", data[0]));
    text.append (Dm3270Utility.toHex (data));
    return text.toString ();
  }
}