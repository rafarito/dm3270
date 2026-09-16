package com.bytezone.dm3270.plugins;

import java.util.List;

/*
 * CAMADA DE COMPATIBILIDADE. Nao estenda esta classe num plugin novo.
 *
 * Ela existia para entregar sete utilitarios por heranca, e cobrava por isso o unico slot de
 * superclasse que Java da a cada plugin. Desde o passo 9 nao ha mais o que entregar: os cinco
 * filtros de campo moram no PluginData - que e o objeto que o plugin ja recebe em toda
 * chamada - e o digest mora no PluginDigest. Os sete metodos daqui sao delegacoes de uma
 * linha.
 *
 * UM PLUGIN NOVO IMPLEMENTA Plugin e chama data.getModifiableFields () ou
 * PluginDigest.md5 () direto, ficando com a heranca livre para o que ele quiser.
 *
 * POR QUE A CLASSE NAO FOI REMOVIDA, e nao sera: os JARs de terceiros ja compilados trazem
 * "extends com.bytezone.dm3270.plugins.DefaultPlugin" gravado no bytecode, e os que usam os
 * utilitarios trazem o invokestatic contra ESTA classe. Remove-la, transforma-la em interface
 * ou mexer na assinatura ou no modificador de qualquer um dos sete quebra o carregamento
 * deles - Regra 3. O PluginApiShapeTest afirma essa forma binaria a cada build, e o
 * LegacyPluginCompatibilityTest carrega um JAR compilado contra ela.
 *
 * Os quatro filtros sem chamador nenhum nos dois repositorios tambem ficam, pela mesma razao:
 * "protected" e contrato publicado, e remove-lo e quebra de compatibilidade, nao limpeza de
 * codigo morto.
 */
@Deprecated
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