package com.bytezone.dm3270.replyfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bytezone.dm3270.structuredfields.StructuredField;
import com.bytezone.dm3270.utilities.Dm3270Utility;

// -----------------------------------------------------------------------------------//
@DisplayName ("QueryReplyField - respostas de capacidade do terminal")
class QueryReplyFieldTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("fabrica getReplyField()")
  class Factory
  // ---------------------------------------------------------------------------------//
  {
    // o tamanho do buffer e escolhido por tipo: cada reply le campos em posicoes fixas
    @ParameterizedTest (name = "{0} -> {1}")
    @CsvSource ({ //
        "0x80, Summary, 8", //
        "0x81, UsableArea, 19", //
        "0x84, AlphanumericPartitions, 8", //
        "0x85, CharacterSets, 11", //
        "0x86, Color, 8", //
        "0x87, Highlight, 8", //
        "0x88, ReplyModes, 8", //
        "0x8F, OEMAuxilliaryDevice, 20", //
        "0x95, DistributedDataManagement, 10", //
        "0x99, AuxilliaryDevices, 8", //
        "0xA1, RPQNames, 11", //
        "0xA6, ImplicitPartition, 15", //
        "0xA8, Transparency, 8", //
        "0xB0, Segment, 8" })
    @DisplayName ("cria a classe certa para cada tipo de reply")
    void createsCorrectType (String hex, String expectedClass, int size)
    {
      byte type = (byte) Integer.decode (hex).intValue ();
      byte[] buffer = new byte[size];
      buffer[0] = StructuredField.QUERY_REPLY;
      buffer[1] = type;

      QueryReplyField field = QueryReplyField.getReplyField (buffer);

      assertEquals (expectedClass, field.getClass ().getSimpleName ());
      assertEquals (type, field.getReplyType ().type);
    }

    @Test
    @DisplayName ("CharacterSets percorre os descritores usando o tamanho declarado")
    void characterSetsWalksDescriptors ()
    {
      byte[] buffer = new byte[11 + 2 * 9];
      buffer[0] = StructuredField.QUERY_REPLY;
      buffer[1] = QueryReplyField.CHARACTER_SETS_REPLY;
      buffer[10] = 9;                                   // tamanho de cada descritor

      CharacterSets field = (CharacterSets) QueryReplyField.getReplyField (buffer);

      assertEquals (9, field.descriptorLength);
      assertEquals (2, field.descriptors.size ());
    }

    @Test
    @DisplayName ("ATENCAO: toString() so funciona depois de addReplyFields()")
    void toStringNeedsReplyList ()
    {
      // QueryReplyField.toString() consulta a lista `replies`, que so e preenchida
      // por addReplyFields() (usado no modo REPLAY). Fora desse fluxo, imprimir uma
      // reply recem-parseada estoura NullPointerException.
      byte[] buffer = { StructuredField.QUERY_REPLY, QueryReplyField.SEGMENT_REPLY };

      QueryReplyField field = QueryReplyField.getReplyField (buffer);

      assertThrows (NullPointerException.class, field::toString);

      field.addReplyFields (java.util.List.of (field));
      assertTrue (field.toString ().contains ("Segment"), field.toString ());
    }

    @Test
    @DisplayName ("tipos nao mapeados caem no DefaultReply")
    void unknownTypeFallsBack ()
    {
      byte[] buffer = new byte[8];
      buffer[0] = StructuredField.QUERY_REPLY;
      buffer[1] = (byte) 0xB4;                    // Graphic Color: sem classe propria

      QueryReplyField field = QueryReplyField.getReplyField (buffer);

      assertInstanceOf (DefaultReply.class, field);
      assertEquals ("Graphic Color", field.getReplyType ().name);
    }

    @Test
    @DisplayName ("tipo totalmente desconhecido recebe um nome generico")
    void completelyUnknownType ()
    {
      byte[] buffer = new byte[8];
      buffer[0] = StructuredField.QUERY_REPLY;
      buffer[1] = 0x7F;

      QueryReplyField field = QueryReplyField.getReplyField (buffer);

      assertEquals ("Unknown Reply Type", field.getReplyType ().name);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("replies construidas por nos (enviadas ao host)")
  class GeneratedReplies
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o cabecalho traz tamanho, 0x81 e o tipo")
    void headerLayout ()
    {
      Color color = new Color ();

      byte[] buffer = new byte[color.replySize ()];
      color.packReply (buffer, 0);

      assertEquals (buffer.length, Dm3270Utility.unsignedShort (buffer, 0),
          "os dois primeiros bytes sao o tamanho total");
      assertEquals ((byte) 0x81, buffer[2]);
      assertEquals (QueryReplyField.COLOR_QUERY_REPLY, buffer[3]);
    }

    @Test
    @DisplayName ("a reply de cores declara 16 pares")
    void colorReplyHasSixteenPairs ()
    {
      Color color = new Color ();

      assertEquals (38, color.replySize (), "4 de cabecalho + 2 de contagem + 16 pares");

      byte[] buffer = new byte[color.replySize ()];
      color.packReply (buffer, 0);

      assertEquals (16, buffer[5], "campo de contagem");
    }

    @Test
    @DisplayName ("packReply respeita o offset e devolve a proxima posicao")
    void packReplyHonoursOffset ()
    {
      Color color = new Color ();
      byte[] buffer = new byte[color.replySize () + 4];

      int next = color.packReply (buffer, 2);

      assertEquals (color.replySize () + 2, next);
      assertEquals (0, buffer[0]);
      assertEquals (0, buffer[1]);
      assertEquals ((byte) 0x81, buffer[4]);
    }

    @Test
    @DisplayName ("uma reply gerada tambem pode ser lida de volta pela fabrica")
    void generatedReplyIsParseable ()
    {
      Color color = new Color ();
      byte[] packed = new byte[color.replySize ()];
      color.packReply (packed, 0);

      // a fabrica espera o buffer ja sem o campo de tamanho
      byte[] withoutLength = new byte[packed.length - 2];
      System.arraycopy (packed, 2, withoutLength, 0, withoutLength.length);

      QueryReplyField parsed = QueryReplyField.getReplyField (withoutLength);

      assertInstanceOf (Color.class, parsed);
      assertEquals (QueryReplyField.COLOR_QUERY_REPLY, parsed.getReplyType ().type);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("tabela de tipos")
  class ReplyTypes
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("nao ha codigos repetidos")
    void typesAreUnique ()
    {
      boolean[] seen = new boolean[256];
      for (QueryReplyField.ReplyType replyType : QueryReplyField.replyTypes)
      {
        int value = replyType.type & 0xFF;
        assertTrue (!seen[value], String.format ("tipo repetido: %02X", replyType.type));
        seen[value] = true;
      }
    }

    @Test
    @DisplayName ("toString mostra codigo e nome")
    void replyTypeToString ()
    {
      QueryReplyField.ReplyType replyType =
          new QueryReplyField.ReplyType ((byte) 0x86, "Color");

      assertEquals ("86 Color", replyType.toString ());
    }
  }
}
