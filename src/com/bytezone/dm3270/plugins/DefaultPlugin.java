package com.bytezone.dm3270.plugins;

import java.util.List;

/**
 * Camada de compatibilidade para JARs legados; novos plugins devem implementar Plugin.
 * Nao remover nem alterar assinaturas/modificadores (contrato binario de terceiros).
 */
@Deprecated
// -----------------------------------------------------------------------------------//
public abstract class DefaultPlugin implements Plugin
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  protected String getMD5 (byte[] buffer)
  // ---------------------------------------------------------------------------------//
  {
    return PluginDigest.md5 (buffer);
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
    return PluginDigest.toHex (bytes);
  }
}