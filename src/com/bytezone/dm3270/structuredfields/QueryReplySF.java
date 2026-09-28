package com.bytezone.dm3270.structuredfields;

import com.bytezone.dm3270.replyfield.QueryReplyField;
import com.bytezone.dm3270.screen.ScreenTarget;

// -----------------------------------------------------------------------------------//
public class QueryReplySF extends StructuredField
// -----------------------------------------------------------------------------------//
{
  private final QueryReplyField queryReplyField;

  // ---------------------------------------------------------------------------------//
  public QueryReplySF (byte[] buffer, int offset, int length)
  // ---------------------------------------------------------------------------------//
  {
    super (buffer, offset, length);
    assert data[0] == StructuredField.QUERY_REPLY;
    queryReplyField = QueryReplyField.getReplyField (data);
  }

  /*
   * Campo de entrada: vai do terminal ao host, e o ReadStructuredFieldCommand que o carrega
   * nao processa os seus campos. Nao ha o que fazer na tela.
   */
  // ---------------------------------------------------------------------------------//
  @Override
  public void process (ScreenTarget screen)
  // ---------------------------------------------------------------------------------//
  {
  }

  // called from ReadStructuredFieldCommand constructor via Command.getReply() (replay)
  // called from ReadStructuredFieldCommand constructor via ReadPartitionQuery (terminal)
  // ---------------------------------------------------------------------------------//
  public QueryReplyField getQueryReplyField ()
  // ---------------------------------------------------------------------------------//
  {
    return queryReplyField;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  public String toString ()
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();
    text.append (String.format ("Struct Field : %02X QueryReply%n", type));
    text.append (queryReplyField);
    return text.toString ();
  }
}