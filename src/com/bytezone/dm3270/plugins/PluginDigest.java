package com.bytezone.dm3270.plugins;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// -----------------------------------------------------------------------------------//
public final class PluginDigest
// -----------------------------------------------------------------------------------//
{
  // Mantem o nome de logger de DefaultPlugin para compatibilidade da saida no logback.xml.
  @SuppressWarnings ("deprecation")
  private static final Logger logger = LoggerFactory.getLogger (DefaultPlugin.class);

  // ---------------------------------------------------------------------------------//
  private PluginDigest ()
  // ---------------------------------------------------------------------------------//
  {
  }

  // Retorna string vazia se MD5 faltar na JVM, preservando o comportamento legado.
  // ---------------------------------------------------------------------------------//
  public static String md5 (byte[] buffer)
  // ---------------------------------------------------------------------------------//
  {
    try
    {
      byte[] digest = MessageDigest.getInstance ("MD5").digest (buffer);
      return toHex (digest);
    }
    catch (NoSuchAlgorithmException e)
    {
      logger.error ("NoSuchAlgorithmException in getMD5", e);
    }
    return "";
  }

  // Formata dois digitos hexadecimais maiusculos por byte (sem omitir zero a esquerda).
  // ---------------------------------------------------------------------------------//
  public static String toHex (byte[] bytes)
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
