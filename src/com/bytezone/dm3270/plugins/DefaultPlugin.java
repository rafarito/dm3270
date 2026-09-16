package com.bytezone.dm3270.plugins;

import java.util.List;

// -----------------------------------------------------------------------------------//
public abstract class DefaultPlugin implements Plugin
// -----------------------------------------------------------------------------------//
{
  /*
   * O DIGEST MORA NO PluginDigest desde o passo 9 - um resumo criptografico de um vetor de
   * bytes nao tem nada a ver com plugin, e este metodo nunca tocou num PluginData.
   *
   * Os dois ficam aqui, com assinatura e modificador intactos, porque sao API de terceiro:
   * um JAR ja compilado que chame getMD5 tem o invokevirtual gravado contra esta classe.
   */
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
    return PluginDigest.toHex (bytes);
  }
}