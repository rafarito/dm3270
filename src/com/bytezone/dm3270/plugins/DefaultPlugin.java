package com.bytezone.dm3270.plugins;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// -----------------------------------------------------------------------------------//
public abstract class DefaultPlugin implements Plugin
// -----------------------------------------------------------------------------------//
{
  private static final Logger logger = LoggerFactory.getLogger (DefaultPlugin.class);

  // ---------------------------------------------------------------------------------//
  protected String getMD5 (byte[] buffer)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      byte[] digest = MessageDigest.getInstance ("MD5").digest (buffer);
      //      return DatatypeConverter.printHexBinary (digest);
      return toHex (digest);
    }
    catch (NoSuchAlgorithmException e)
    {
      logger.error ("NoSuchAlgorithmException in getMD5", e);
    }
    return "";
  }

  //  protected static ScreenField getCursorField (PluginData data)
  //  {
  //    for (ScreenField field : data.screenFields)
  //    {
  //      if (field.contains (data.cursorRow, data.cursorColumn))
  //        return field;
  //    }
  //    return null;
  //  }

  /*
   * OS CINCO FILTROS DE CAMPO AGORA MORAM NO PluginData, que e o objeto que carrega a
   * lista. Eles ficam aqui, com a MESMA assinatura e o MESMO modificador, porque sao API
   * de terceiro: os JARs ja compilados chamam DefaultPlugin.getModifiableFields (data) e
   * o invokestatic gravado neles nao acompanha mudanca nenhuma de forma. Delegar e o que
   * tira a duplicacao sem quebrar a Regra 3.
   *
   * Medido antes de mexer: destes cinco, so getModifiableFields tem chamador - seis
   * sitios em tres dos seis plugins -, e ele ja era identico, linha por linha, ao metodo
   * que o PluginData sempre teve.
   */
  // ---------------------------------------------------------------------------------//
  protected static int countModifiableFields (PluginData data)
  // ---------------------------------------------------------------------------------//
  {
    return data.countModifiableFields ();
  }

  // ---------------------------------------------------------------------------------//
  protected static List<PluginField> getModifiableFields (PluginData data)
  // ---------------------------------------------------------------------------------//
  {
    return data.getModifiableFields ();
  }

  // ---------------------------------------------------------------------------------//
  protected static List<PluginField> getProtectedFields (PluginData data)
  // ---------------------------------------------------------------------------------//
  {
    return data.getProtectedFields ();
  }

  // ---------------------------------------------------------------------------------//
  protected static List<PluginField> getAlphanumericFields (PluginData data)
  // ---------------------------------------------------------------------------------//
  {
    return data.getAlphanumericFields ();
  }

  // ---------------------------------------------------------------------------------//
  protected static List<PluginField> getNumericFields (PluginData data)
  // ---------------------------------------------------------------------------------//
  {
    return data.getNumericFields ();
  }

  // ---------------------------------------------------------------------------------//
  static String toHex (byte[] bytes)
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder builder = new StringBuilder (bytes.length * 2);
    for (int i = 0; i < bytes.length; i++)
    {
      int digit = (bytes[i] >> 4) & 0xF;
      builder.append (digit < 10 ? (char) ('0' + digit) : (char) ('A' - 10 + digit));
      digit = (bytes[i] & 0xF);
      builder.append (digit < 10 ? (char) ('0' + digit) : (char) ('A' - 10 + digit));
    }
    return builder.toString ();
  }
}