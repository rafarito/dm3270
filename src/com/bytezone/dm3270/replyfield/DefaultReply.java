package com.bytezone.dm3270.replyfield;

import com.bytezone.dm3270.utilities.Dm3270Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// -----------------------------------------------------------------------------------//
public class DefaultReply extends QueryReplyField
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DefaultReply.class);
  // ---------------------------------------------------------------------------------//
  public DefaultReply (byte[] buffer)
  // ---------------------------------------------------------------------------------//
  {
    super (buffer);
    logger.warn ("Unknown reply field: {}", String.format ("%02X", buffer[0]));
    logger.warn ("{}", Dm3270Utility.toHex (buffer));
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder (super.toString ());

    text.append (String.format ("%n%n%s", Dm3270Utility.toHex (data)));

    return text.toString ();
  }
}