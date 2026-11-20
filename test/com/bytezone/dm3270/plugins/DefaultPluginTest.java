package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

    // Usa tela assimetrica (3 vs 1) porque mixedScreen() tem 2 protegidos e 2 modificaveis.
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

    // 0x0F testa zero a esquerda e 0xFF testa byte sem sinal em maiusculas.
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

  // Matriz 2x2 (protected x alpha) para distinguir cada filtro sem sobreposicao.
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

  // Subclasse necessaria apenas para alcancar getMD5(), unico metodo protected de instancia.
  // ---------------------------------------------------------------------------------//
  private static final class TestPlugin extends DefaultPlugin
  // ---------------------------------------------------------------------------------//
  {
  }
}
