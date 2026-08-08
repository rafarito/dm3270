package com.bytezone.dm3270.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// -----------------------------------------------------------------------------------//
@DisplayName ("API de plugins - contrato exposto para plugins externos")
class PluginApiTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ScreenLocation")
  class Locations
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "posicao {0} = linha {1}, coluna {2}")
    @CsvSource ({ "0, 0, 0", "79, 0, 79", "80, 1, 0", "161, 2, 1", "1919, 23, 79" })
    @DisplayName ("converte posicao linear em linha/coluna (80 colunas)")
    void convertsLinearToRowColumn (int location, int row, int column)
    {
      ScreenLocation screenLocation = new ScreenLocation (location);

      assertEquals (row, screenLocation.row);
      assertEquals (column, screenLocation.column);
      assertEquals (location, screenLocation.location);
    }

    @ParameterizedTest (name = "linha {1}, coluna {2} = posicao {0}")
    @CsvSource ({ "0, 0, 0", "79, 0, 79", "80, 1, 0", "1919, 23, 79" })
    @DisplayName ("converte linha/coluna em posicao linear")
    void convertsRowColumnToLinear (int location, int row, int column)
    {
      assertEquals (location, new ScreenLocation (row, column).location);
    }

    @Test
    @DisplayName ("os dois construtores sao equivalentes para toda a tela")
    void constructorsAgree ()
    {
      for (int location = 0; location < 24 * 80; location++)
      {
        ScreenLocation fromLinear = new ScreenLocation (location);
        ScreenLocation fromGrid =
            new ScreenLocation (fromLinear.row, fromLinear.column);

        assertTrue (fromLinear.matches (fromGrid), "divergiu na posicao " + location);
      }
    }

    @Test
    @DisplayName ("matches compara posicao, nao identidade")
    void matchesComparesLocation ()
    {
      assertTrue (new ScreenLocation (100).matches (new ScreenLocation (100)));
      assertFalse (new ScreenLocation (100).matches (new ScreenLocation (101)));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("PluginField")
  class Fields
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um campo protegido nunca e modificavel")
    void protectedIsNotModifiable ()
    {
      PluginField field = field (0, 10, 8, true, "TEXTO");

      assertTrue (field.isProtected);
      assertFalse (field.isModifiable);
      assertEquals ("no", field.getModifiable ());
    }

    @Test
    @DisplayName ("isModifiableLength exige desprotegido E tamanho exato")
    void modifiableLengthChecksBoth ()
    {
      PluginField unprotected = field (0, 10, 8, false, "");
      PluginField protectedField = field (1, 10, 8, true, "");

      assertTrue (unprotected.isModifiableLength (8));
      assertFalse (unprotected.isModifiableLength (7));
      assertFalse (protectedField.isModifiableLength (8));
    }

    @Test
    @DisplayName ("contains cobre o campo inteiro, incluindo o byte de atributo")
    void containsCoversWholeField ()
    {
      PluginField field = field (0, 100, 5, false, "ABCDE");

      assertTrue (field.contains (new ScreenLocation (99)), "byte de atributo");
      assertTrue (field.contains (new ScreenLocation (100)));
      assertTrue (field.contains (new ScreenLocation (104)));
      assertFalse (field.contains (new ScreenLocation (105)));
      assertFalse (field.contains (new ScreenLocation (98)));
    }

    @Test
    @DisplayName ("contains funciona para campo que da a volta no fim da tela")
    void containsWrapsAroundScreen ()
    {
      PluginField field = field (0, 1918, 5, false, "ABCDE");

      assertTrue (field.contains (new ScreenLocation (1919)));
      assertTrue (field.contains (new ScreenLocation (0)));
      assertTrue (field.contains (new ScreenLocation (1)));
      assertFalse (field.contains (new ScreenLocation (500)));
    }

    @Test
    @DisplayName ("change registra o novo valor sem sobrescrever o atual")
    void changeRecordsPendingValue ()
    {
      PluginData data = data (field (0, 10, 8, false, "ANTIGO"));
      PluginField field = data.getField (0);

      field.change ("NOVO");

      assertEquals ("NOVO", field.newData);
      assertEquals ("ANTIGO", field.getFieldValue (), "o valor lido da tela nao muda");
      assertEquals (1, data.changedFields.size ());
      assertSame (field, data.changedFields.get (0));
    }

    @Test
    @DisplayName ("change duas vezes registra o campo uma unica vez")
    void changeIsIdempotentInTheChangedList ()
    {
      PluginData data = data (field (0, 10, 8, false, "A"));
      PluginField field = data.getField (0);

      field.change ("B");
      field.change ("C");

      assertEquals (1, data.changedFields.size ());
      assertEquals ("C", field.newData);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("PluginData")
  class Data
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("trimField remove espacos e nunca devolve null")
    void trimFieldIsSafe ()
    {
      PluginData data = data (field (0, 10, 8, true, "  TSO   "));

      assertEquals ("TSO", data.trimField (0));
      assertEquals ("", data.trimField (99), "indice fora da faixa");
      assertEquals ("", data.trimField (-1), "indice negativo");
    }

    @Test
    @DisplayName ("getField por indice devolve null fora da faixa")
    void getFieldByIndexIsSafe ()
    {
      PluginData data = data (field (0, 10, 8, true, "A"));

      assertNotNull (data.getField (0));
      assertNull (data.getField (1));
      assertNull (data.getField (-1));
    }

    @Test
    @DisplayName ("getField por indice injeta o PluginData no campo")
    void getFieldInjectsData ()
    {
      PluginData data = data (field (0, 10, 8, false, "A"));

      // sem a injecao, change(String) dispararia o assert de pluginData != null
      data.getField (0).change ("B");

      assertEquals (1, data.changedFields.size ());
    }

    @Test
    @DisplayName ("getField por valor encontra o campo pelo conteudo exato")
    void getFieldByValue ()
    {
      PluginData data = data (field (0, 10, 8, true, "MENU"), //
          field (1, 100, 8, false, "OPCAO"));

      assertEquals (1, data.getField ("OPCAO").sequence);
      assertNull (data.getField ("nao existe"));
    }

    @Test
    @DisplayName ("getField por posicao encontra o campo que contem aquela celula")
    void getFieldByLocation ()
    {
      PluginData data = data (field (0, 10, 5, true, "ABCDE"), //
          field (1, 100, 5, false, "VWXYZ"));

      assertEquals (1, data.getField (new ScreenLocation (102)).sequence);
      assertNull (data.getField (new ScreenLocation (500)));
    }

    @Test
    @DisplayName ("getModifiableFields devolve apenas os campos desprotegidos")
    void filtersModifiableFields ()
    {
      PluginData data = data (field (0, 10, 8, true, "ROTULO"), //
          field (1, 100, 8, false, "ENTRADA"), //
          field (2, 200, 8, false, "OUTRA"));

      List<PluginField> modifiable = data.getModifiableFields ();

      assertEquals (2, modifiable.size ());
      assertTrue (modifiable.stream ().noneMatch (f -> f.isProtected));
    }

    @Test
    @DisplayName ("o cursor so e considerado movido depois de setNewCursorPosition")
    void cursorMovedFlag ()
    {
      PluginData data = data (field (0, 10, 8, false, "A"));

      assertFalse (data.cursorMoved ());

      data.setNewCursorPosition (5, 10);

      assertTrue (data.cursorMoved ());
      assertEquals (5 * 80 + 10, data.getNewCursorLocation ());
    }

    @Test
    @DisplayName ("getCursorField devolve o campo sob o cursor inicial")
    void findsCursorField ()
    {
      List<PluginField> fields = new ArrayList<> ();
      fields.add (field (0, 10, 5, true, "ABCDE"));
      fields.add (field (1, 100, 5, false, "VWXYZ"));

      PluginData data = new PluginData (0, new ScreenLocation (101), fields);

      assertEquals (1, data.getCursorField ().sequence);
    }

    @Test
    @DisplayName ("size reflete a quantidade de campos da tela")
    void reportsSize ()
    {
      assertEquals (3, data (field (0, 10, 8, true, "A"), field (1, 20, 8, true, "B"),
          field (2, 30, 8, true, "C")).size ());
    }

    @Test
    @DisplayName ("a tecla AID comeca zerada e pode ser definida pelo plugin")
    void keyRoundTrip ()
    {
      PluginData data = data (field (0, 10, 8, false, "A"));

      assertEquals (0, data.getKey ());

      data.setKey ((byte) 0x7D);

      assertEquals ((byte) 0x7D, data.getKey ());
    }
  }

  // ---------------------------------------------------------------------------------//
  private static PluginField field (int sequence, int location, int length,
      boolean isProtected, String value)
  // ---------------------------------------------------------------------------------//
  {
    return new PluginField (sequence, new ScreenLocation (location), length, isProtected,
        true, true, false, value);
  }

  // ---------------------------------------------------------------------------------//
  private static PluginData data (PluginField... fields)
  // ---------------------------------------------------------------------------------//
  {
    List<PluginField> list = new ArrayList<> ();
    for (PluginField field : fields)
      list.add (field);

    return new PluginData (0, new ScreenLocation (0), list);
  }
}
