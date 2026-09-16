package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/*
 * Os sete utilitarios do DefaultPlugin, que nunca tiveram teste.
 *
 * Eles sao API de terceiro: protected e package-private numa classe que os JARs instalados
 * estendem - o javap confirma "extends com.bytezone.dm3270.plugins.DefaultPlugin" dentro do
 * DownloadDataset.jar. Esta classe nasceu como a rede que precedeu a delegacao, e continua
 * sendo o que diz se ela preserva o resultado: hoje os cinco filtros de campo moram no
 * PluginData e os daqui apenas delegam, com assinatura e modificador intactos.
 *
 * O teste mora no MESMO pacote, entao "protected static" e package-private sao chamaveis
 * direto - nao e preciso subclasse nenhuma, salvo para o unico metodo de instancia, o
 * getMD5.
 *
 * MEDIDO, e e o que justifica o bloco 2: destes sete, so getModifiableFields e chamado por
 * alguem - seis sitios em tres dos seis plugins -, e ele e identico, linha por linha, ao
 * PluginData.getModifiableFields () que o proprio parametro ja oferece. Os outros seis tem
 * zero referencias nos dois repositorios, e o toHex nem sequer e visivel de
 * com.bytezone.plugins, que e onde os plugins moram. A heranca custa o unico slot de
 * superclasse dos seis plugins para entregar um metodo que tres deles usam.
 */
// A classe sob teste e deprecated de proposito - ela e a camada de compatibilidade com
// os JARs ja compilados -, e vigia-la e exatamente o trabalho desta rede.
@SuppressWarnings ("deprecation")
// -----------------------------------------------------------------------------------//
@DisplayName ("DefaultPlugin - os sete utilitarios entregues por heranca")
class DefaultPluginTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("As consultas de campo")
  class FieldQueries
  // ---------------------------------------------------------------------------------//
  {
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("getModifiableFields devolve os nao protegidos, na ordem da tela")
    void getModifiableFieldsDevolveOsNaoProtegidos ()
    // -------------------------------------------------------------------------------//
    {
      PluginData data = mixedScreen ();

      assertEquals (List.of ("entrada1", "entrada2"),
                    values (DefaultPlugin.getModifiableFields (data)));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("getProtectedFields devolve exatamente o complemento")
    void getProtectedFieldsDevolveOComplemento ()
    // -------------------------------------------------------------------------------//
    {
      PluginData data = mixedScreen ();

      assertEquals (List.of ("titulo", "rotulo"),
                    values (DefaultPlugin.getProtectedFields (data)));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("countModifiableFields concorda com o tamanho da lista")
    void countConcordaComOTamanhoDaLista ()
    // -------------------------------------------------------------------------------//
    {
      PluginData data = mixedScreen ();

      assertEquals (DefaultPlugin.getModifiableFields (data).size (),
                    DefaultPlugin.countModifiableFields (data));
      assertEquals (2, DefaultPlugin.countModifiableFields (data));
    }

    /*
     * A tela de apoio tem dois protegidos e dois nao, e com ela a contagem da 2 tanto no
     * codigo certo quanto num que contasse os PROTEGIDOS - foi um mutante sobrevivente do PIT
     * que mostrou isso. Este caso usa uma tela assimetrica, onde as duas contagens diferem.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("a contagem e dos modificaveis, e nao dos protegidos")
    void aContagemEDosModificaveis ()
    // -------------------------------------------------------------------------------//
    {
      List<PluginField> fields = new ArrayList<> ();
      fields.add (field (0, 0, true, true, "protegido1"));
      fields.add (field (1, 20, true, true, "protegido2"));
      fields.add (field (2, 40, true, true, "protegido3"));
      fields.add (field (3, 60, false, true, "entrada"));
      PluginData data = new PluginData (0, new ScreenLocation (0), fields);

      assertEquals (1, DefaultPlugin.countModifiableFields (data));
      assertEquals (1, data.countModifiableFields ());
    }

    /*
     * getNumericFields e o complemento de getAlphanumericFields, e nao um filtro proprio: o
     * criterio dos dois e o mesmo campo isAlpha, negado num deles. Vale registrar porque os
     * nomes sugerem duas classificacoes independentes, e nao sao.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("alfanumericos e numericos particionam a tela pelo isAlpha")
    void alfanumericosENumericosParticionamPeloIsAlpha ()
    // -------------------------------------------------------------------------------//
    {
      PluginData data = mixedScreen ();

      assertEquals (List.of ("titulo", "entrada1"),
                    values (DefaultPlugin.getAlphanumericFields (data)));
      assertEquals (List.of ("rotulo", "entrada2"),
                    values (DefaultPlugin.getNumericFields (data)));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("numa tela sem campos as cinco consultas devolvem vazio ou zero")
    void telaSemCamposDevolveVazio ()
    // -------------------------------------------------------------------------------//
    {
      PluginData data = new PluginData (0, new ScreenLocation (0), new ArrayList<> ());

      assertEquals (List.of (), values (DefaultPlugin.getModifiableFields (data)));
      assertEquals (List.of (), values (DefaultPlugin.getProtectedFields (data)));
      assertEquals (List.of (), values (DefaultPlugin.getAlphanumericFields (data)));
      assertEquals (List.of (), values (DefaultPlugin.getNumericFields (data)));
      assertEquals (0, DefaultPlugin.countModifiableFields (data));
    }

    /*
     * A REDE DA DELEGACAO. Desde o passo 9 os cinco filtros moram no PluginData e o
     * DefaultPlugin apenas delega, mantendo assinatura e modificador porque sao API de
     * terceiro. Estes cinco pares sao o que prova que a delegacao nao inverteu nem trocou
     * nenhum dos filtros - o erro mais facil de cometer quando cinco lacos quase iguais viram
     * cinco chamadas quase iguais.
     *
     * Eles so podem ser escritos porque o teste mora no mesmo pacote e alcanca os
     * "protected static" direto.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("os cinco utilitarios herdados dao o mesmo que os do PluginData")
    void osCincoUtilitariosDelegamParaOPluginData ()
    // -------------------------------------------------------------------------------//
    {
      PluginData data = mixedScreen ();

      assertEquals (values (data.getModifiableFields ()),
                    values (DefaultPlugin.getModifiableFields (data)));
      assertEquals (values (data.getProtectedFields ()),
                    values (DefaultPlugin.getProtectedFields (data)));
      assertEquals (values (data.getAlphanumericFields ()),
                    values (DefaultPlugin.getAlphanumericFields (data)));
      assertEquals (values (data.getNumericFields ()),
                    values (DefaultPlugin.getNumericFields (data)));
      assertEquals (data.countModifiableFields (),
                    DefaultPlugin.countModifiableFields (data));
    }

    /*
     * E as quatro consultas novas do PluginData tambem respondem direito numa tela vazia -
     * elas entram no <targetClasses> do PIT junto com o resto da classe, e um mutante que
     * trocasse o filtro por "true" sobreviveria sem este caso.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("as consultas do PluginData filtram pelos mesmos criterios")
    void asConsultasDoPluginDataFiltramPelosMesmosCriterios ()
    // -------------------------------------------------------------------------------//
    {
      PluginData data = mixedScreen ();

      assertEquals (List.of ("entrada1", "entrada2"), values (data.getModifiableFields ()));
      assertEquals (List.of ("titulo", "rotulo"), values (data.getProtectedFields ()));
      assertEquals (List.of ("titulo", "entrada1"), values (data.getAlphanumericFields ()));
      assertEquals (List.of ("rotulo", "entrada2"), values (data.getNumericFields ()));
      assertEquals (2, data.countModifiableFields ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("O digest")
  class Digest
  // ---------------------------------------------------------------------------------//
  {
    /*
     * Valores conhecidos, e nao recalculados no proprio teste - do contrario a assercao seria
     * tautologica. Sao os dois MD5 mais publicados que existem.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("getMD5 de valores conhecidos, em maiusculas")
    void getMD5DeValoresConhecidos ()
    // -------------------------------------------------------------------------------//
    {
      DefaultPlugin plugin = new TestPlugin ();

      assertEquals ("D41D8CD98F00B204E9800998ECF8427E", plugin.getMD5 (new byte[0]));
      assertEquals ("900150983CD24FB0D6963F7D28E17F72",
                    plugin.getMD5 ("abc".getBytes (StandardCharsets.US_ASCII)));
    }

    /*
     * toHex escreve DOIS digitos por byte, com A-F em MAIUSCULAS, e trata o byte como sem
     * sinal. O 0x0F com zero a esquerda e o 0xFF sao as duas bordas que uma reimplementacao
     * por Integer.toHexString erraria - aquela come o zero e escreve minusculas.
     */
    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("toHex: dois digitos por byte, maiusculas, sem sinal")
    void toHexDoisDigitosPorByte ()
    // -------------------------------------------------------------------------------//
    {
      assertEquals ("", DefaultPlugin.toHex (new byte[0]));
      assertEquals ("000F10FF",
                    DefaultPlugin.toHex (new byte[] { 0x00, 0x0F, 0x10, (byte) 0xFF }));
    }

    // -------------------------------------------------------------------------------//
    @Test
    @DisplayName ("toHex cobre os 256 valores, sempre com dois caracteres")
    void toHexCobreOs256Valores ()
    // -------------------------------------------------------------------------------//
    {
      byte[] todos = new byte[256];
      for (int i = 0; i < 256; i++)
        todos[i] = (byte) i;

      String hex = DefaultPlugin.toHex (todos);

      assertEquals (512, hex.length ());
      assertEquals ("000102", hex.substring (0, 6));
      assertEquals ("FDFEFF", hex.substring (506));
    }
  }

  // ---------------------------------------------------------------------------------//
  //  Apoio
  // ---------------------------------------------------------------------------------//

  /*
   * Quatro campos que separam as tres classificacoes de forma independente: dois protegidos e
   * dois nao, dois alfa e dois nao, e as duas divisoes cruzadas de proposito - do contrario
   * um metodo devolvendo a lista do outro passaria despercebido.
   */
  // ---------------------------------------------------------------------------------//
  private static PluginData mixedScreen ()
  // ---------------------------------------------------------------------------------//
  {
    List<PluginField> fields = new ArrayList<> ();
    fields.add (field (0, 0, true, true, "titulo"));
    fields.add (field (1, 20, true, false, "rotulo"));
    fields.add (field (2, 40, false, true, "entrada1"));
    fields.add (field (3, 60, false, false, "entrada2"));

    return new PluginData (0, new ScreenLocation (0), fields);
  }

  // ---------------------------------------------------------------------------------//
  private static PluginField field (int sequence, int location, boolean isProtected,
      boolean isAlpha, String value)
  // ---------------------------------------------------------------------------------//
  {
    return new PluginField (sequence, new ScreenLocation (location), value.length (),
        isProtected, isAlpha, true, false, value);
  }

  // ---------------------------------------------------------------------------------//
  private static List<String> values (List<PluginField> fields)
  // ---------------------------------------------------------------------------------//
  {
    List<String> values = new ArrayList<> ();
    for (PluginField field : fields)
      values.add (field.getFieldValue ());
    return values;
  }

  /*
   * getMD5 e o unico dos sete que e metodo de INSTANCIA, e protected - entao precisa de uma
   * subclasse, que e exatamente a forma que um plugin de verdade tem.
   */
  // ---------------------------------------------------------------------------------//
  private static final class TestPlugin extends DefaultPlugin
  // ---------------------------------------------------------------------------------//
  {
  }
}
