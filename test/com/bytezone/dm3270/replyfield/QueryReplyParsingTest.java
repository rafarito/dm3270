package com.bytezone.dm3270.replyfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.display.ScreenDimensions;
import com.bytezone.dm3270.structuredfields.StructuredField;

// -----------------------------------------------------------------------------------//
@DisplayName ("QueryReplyField - leitura dos campos de cada reply")
class QueryReplyParsingTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  //  Construcao dos buffers
  // ---------------------------------------------------------------------------------//

  // Uma reply lida do host: 0x81, o tipo e os campos que vierem depois.
  private static byte[] reply (byte type, int... rest)
  {
    byte[] buffer = new byte[rest.length + 2];
    buffer[0] = StructuredField.QUERY_REPLY;
    buffer[1] = type;
    for (int i = 0; i < rest.length; i++)
      buffer[i + 2] = (byte) rest[i];

    return buffer;
  }

  // toString() consulta a lista de replies do modo replay: sem ela estoura.
  private static String report (QueryReplyField field)
  {
    field.addReplyFields (List.of (field));

    return field.toString ();
  }

  private static byte ebcdic (char c)
  {
    return (byte) com.bytezone.dm3270.utilities.Dm3270Utility.asc2ebc[c];
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("UsableArea")
  class UsableAreas
  // ---------------------------------------------------------------------------------//
  {
    // flags1, flags2, largura, altura, unidade, 4 razoes de 2 bytes, xUnits, yUnits
    private byte[] buffer (int flags1, int units, int width, int height)
    {
      return reply (QueryReplyField.USABLE_AREA_REPLY, flags1, 0x00,
                    (width >> 8) & 0xFF, width & 0xFF, (height >> 8) & 0xFF,
                    height & 0xFF, units, 0x00, 0x0A, 0x00, 0x02, 0x00, 0x04, 0x00, 0x03,
                    0x09, 0x0C, 0x07, 0x80);
    }

    @Test
    @DisplayName ("le as dimensoes da area utilizavel")
    void readsDimensions ()
    {
      UsableArea field = new UsableArea (buffer (0x01, 0x00, 80, 24));

      ScreenDimensions dimensions = field.getScreenDimensions ();

      assertEquals (24, dimensions.rows);
      assertEquals (80, dimensions.columns);
    }

    @Test
    @DisplayName ("le o tamanho do buffer quando ele vem declarado")
    void readsBufferSize ()
    {
      // os dois ultimos bytes do buffer trazem 0x0780 = 1920
      String text = report (new UsableArea (buffer (0x01, 0x00, 80, 24)));

      assertTrue (text.contains ("buffer     : 1920"), text);
    }

    @ParameterizedTest (name = "flags1 {0} -> {1}")
    @CsvSource ({ "0x00, Reserved", "0x01, '12/14 bit'", "0x03, '12/14/16 bit'",
                  "0x0F, Unmapped" })
    @DisplayName ("os quatro bits baixos dizem o modo de enderecamento")
    void describesAddressingMode (int flags1, String expected)
    {
      String text = report (new UsableArea (buffer (flags1, 0x00, 80, 24)));

      assertTrue (text.contains ("ad mode    : " + expected), text);
    }

    @ParameterizedTest (name = "unidade {0} -> {1}")
    @CsvSource ({ "0x00, Inches", "0x01, Millimetres" })
    @DisplayName ("a unidade de medida tem dois valores")
    void describesUnits (int units, String expected)
    {
      String text = report (new UsableArea (buffer (0x01, units, 80, 24)));

      assertTrue (text.contains (expected), text);
    }

    @Test
    @DisplayName ("as razoes de escala aparecem como fracoes")
    void showsRatios ()
    {
      String text = report (new UsableArea (buffer (0x01, 0x00, 80, 24)));

      assertTrue (text.contains ("x ratio    : 10 / 2"), text);
      assertTrue (text.contains ("y ratio    : 4 / 3"), text);
      assertTrue (text.contains ("x units    : 9"), text);
      assertTrue (text.contains ("y units    : 12"), text);
    }

    @ParameterizedTest (name = "flags1 {0}")
    @ValueSource (ints = { 0x05, 0x08, 0x0E })
    @DisplayName ("um modo de enderecamento fora da tabela e descrito pelo valor bruto")
    void unknownAddressingMode (int flags1)
    {
      // addressingModes tem 5 entradas (indices 0 a 4) e so 0x0F e remapeado para 4
      UsableArea field = new UsableArea (buffer (flags1, 0x00, 80, 24));

      assertTrue (report (field)
          .contains (String.format ("ad mode    : Unknown (%02X)", flags1)),
                  report (field));
    }

    @Test
    @DisplayName ("uma unidade de medida acima de 1 e descrita pelo valor bruto")
    void unknownUnits ()
    {
      UsableArea field = new UsableArea (buffer (0x01, 0x02, 80, 24));

      assertTrue (report (field).contains ("units      : 2 - Unknown (02)"),
                  report (field));
    }

    @Test
    @DisplayName ("a reply que nos geramos declara a tela pedida")
    void buildsReply ()
    {
      UsableArea field = new UsableArea (24, 80);

      byte[] packed = new byte[field.replySize ()];
      field.packReply (packed, 0);

      assertEquals (23, packed.length);
      assertEquals (StructuredField.QUERY_REPLY, packed[2]);
      assertEquals (QueryReplyField.USABLE_AREA_REPLY, packed[3]);
      assertEquals (80, com.bytezone.dm3270.utilities.Dm3270Utility
          .unsignedShort (packed, 6));
      assertEquals (24, com.bytezone.dm3270.utilities.Dm3270Utility
          .unsignedShort (packed, 8));
      assertEquals (1920, com.bytezone.dm3270.utilities.Dm3270Utility
          .unsignedShort (packed, 21));
    }

    @Test
    @DisplayName ("a reply gerada pode ser lida de volta")
    void generatedReplyIsParseable ()
    {
      UsableArea field = new UsableArea (43, 80);

      byte[] packed = new byte[field.replySize ()];
      field.packReply (packed, 0);

      UsableArea parsed =
          (UsableArea) QueryReplyField.getReplyField (Arrays.copyOfRange (packed, 2,
                                                                        packed.length));

      assertEquals (43, parsed.getScreenDimensions ().rows);
      assertEquals (80, parsed.getScreenDimensions ().columns);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ImplicitPartition")
  class ImplicitPartitions
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le a tela implicita e a alternativa")
    void readsBothScreens ()
    {
      // largura em data[7], altura em data[9], alternativas em data[11] e data[13]
      ImplicitPartition field = new ImplicitPartition (
          reply (QueryReplyField.IMP_PART_QUERY_REPLY, 0x00, 0x00, 0x00, 0x00, 0x00,
                 0x00, 0x50, 0x00, 0x18, 0x00, 0x50, 0x00, 0x2B));

      assertEquals (24, field.getScreenDimensions ().rows);
      assertEquals (80, field.getScreenDimensions ().columns);
      assertEquals (43, field.getAlternateScreenDimensions ().rows);
      assertEquals (80, field.getAlternateScreenDimensions ().columns);
    }

    @Test
    @DisplayName ("a reply que geramos usa a tela pedida como alternativa")
    void buildsReply ()
    {
      ImplicitPartition field = new ImplicitPartition (43, 80);

      byte[] packed = new byte[field.replySize ()];
      field.packReply (packed, 0);

      ImplicitPartition parsed = (ImplicitPartition) QueryReplyField
          .getReplyField (Arrays.copyOfRange (packed, 2, packed.length));

      assertEquals (43, parsed.getAlternateScreenDimensions ().rows);
      assertEquals (80, parsed.getAlternateScreenDimensions ().columns);
    }

    @Test
    @DisplayName ("a reply que geramos descreve as telas que declara")
    void writeConstructorFillsTheFields ()
    {
      // o construtor de escrita alimenta os campos de leitura junto com o buffer
      ImplicitPartition field = new ImplicitPartition (43, 80);

      assertEquals (24, field.getScreenDimensions ().rows);
      assertEquals (80, field.getScreenDimensions ().columns);
      assertEquals (43, field.getAlternateScreenDimensions ().rows);
      assertEquals (80, field.getAlternateScreenDimensions ().columns);

      String text = report (field);

      assertTrue (text.contains ("width      : 80"), text);
      assertTrue (text.contains ("height     : 24"), text);
    }

    @Test
    @DisplayName ("o relatorio de uma reply lida mostra as duas telas")
    void describesBothScreens ()
    {
      ImplicitPartition field = new ImplicitPartition (
          reply (QueryReplyField.IMP_PART_QUERY_REPLY, 0x00, 0x00, 0x00, 0x00, 0x00,
                 0x00, 0x50, 0x00, 0x18, 0x00, 0x50, 0x00, 0x2B));

      String text = report (field);

      assertTrue (text.contains ("width      : 80"), text);
      assertTrue (text.contains ("height     : 24"), text);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("OEMAuxilliaryDevice")
  class OemDevices
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le tipo de dispositivo e usuario em EBCDIC")
    void readsNames ()
    {
      byte[] buffer = new byte[20];
      buffer[0] = StructuredField.QUERY_REPLY;
      buffer[1] = QueryReplyField.OEM_AUXILLIARY_DEVICE_REPLY;
      buffer[2] = 0x11;                       // flags
      buffer[3] = 0x22;                       // refID

      byte[] type = "TCP3270 ".getBytes (java.nio.charset.StandardCharsets.US_ASCII);
      for (int i = 0; i < 8; i++)
        buffer[4 + i] = ebcdic ((char) type[i]);

      byte[] name = "USER01  ".getBytes (java.nio.charset.StandardCharsets.US_ASCII);
      for (int i = 0; i < 8; i++)
        buffer[12 + i] = ebcdic ((char) name[i]);

      OEMAuxilliaryDevice field = new OEMAuxilliaryDevice (buffer);

      assertEquals ("USER01", field.getUserName ());

      String text = report (field);

      assertTrue (text.contains ("type       : TCP3270"), text);
      assertTrue (text.contains ("name       : USER01"), text);
      assertTrue (text.contains ("flags1     : 11"), text);
      assertTrue (text.contains ("ref ID     : 22"), text);
    }

    @Test
    @DisplayName ("a reply que geramos se identifica como dm3270")
    void buildsReply ()
    {
      OEMAuxilliaryDevice field = new OEMAuxilliaryDevice ();

      byte[] packed = new byte[field.replySize ()];
      field.packReply (packed, 0);

      OEMAuxilliaryDevice parsed = (OEMAuxilliaryDevice) QueryReplyField
          .getReplyField (Arrays.copyOfRange (packed, 2, packed.length));

      assertEquals ("dm3270", parsed.getUserName ());
      assertTrue (report (parsed).contains ("type       : TCP3270"), report (parsed));
    }

    @Test
    @DisplayName ("um tipo diferente viola a assercao")
    void rejectsWrongType ()
    {
      byte[] buffer = new byte[20];
      buffer[0] = StructuredField.QUERY_REPLY;
      buffer[1] = QueryReplyField.COLOR_QUERY_REPLY;

      assertThrows (AssertionError.class, () -> new OEMAuxilliaryDevice (buffer));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("AlphanumericPartitions")
  class Partitions
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le o maximo de particoes e o armazenamento disponivel")
    void readsLimits ()
    {
      AlphanumericPartitions field =
          new AlphanumericPartitions (reply (
              QueryReplyField.ALPHANUMERIC_PARTITIONS_REPLY, 0x08, 0x1F, 0x40, 0x00));

      String text = report (field);

      assertTrue (text.contains ("max        : 8"), text);
      assertTrue (text.contains ("storage    : 8000"), text);
    }

    @ParameterizedTest (name = "flags {0} -> {1}")
    @CsvSource ({ "0x80, 'vert win   : true'", "0x40, 'hor win    : true'",
                  "0x10, 'APA        : true'", "0x08, 'protect    : true'",
                  "0x04, 'lcopy      : true'", "0x02, 'modpart    : true'" })
    @DisplayName ("cada bit das flags liga uma capacidade")
    void readsFlags (int flags, String expected)
    {
      AlphanumericPartitions field = new AlphanumericPartitions (
          reply (QueryReplyField.ALPHANUMERIC_PARTITIONS_REPLY, 0x08, 0x00, 0x00, flags));

      assertTrue (report (field).contains (expected), report (field));
    }

    @Test
    @DisplayName ("sem flags nenhuma capacidade e declarada")
    void noFlags ()
    {
      AlphanumericPartitions field = new AlphanumericPartitions (
          reply (QueryReplyField.ALPHANUMERIC_PARTITIONS_REPLY, 0x00, 0x00, 0x00, 0x00));

      String text = report (field);

      assertFalse (text.contains ("true"), text);
    }

    @Test
    @DisplayName ("a reply que geramos e vazia: nao suportamos particoes")
    void buildsEmptyReply ()
    {
      AlphanumericPartitions field = new AlphanumericPartitions ();

      assertEquals (8, field.replySize ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("RPQNames")
  class Rpq
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le o tipo de dispositivo, o modelo e o nome RPQ")
    void readsFields ()
    {
      byte[] buffer = new byte[11 + 4];
      buffer[0] = StructuredField.QUERY_REPLY;
      buffer[1] = QueryReplyField.RPQ_NAMES_REPLY;

      for (int i = 0; i < 4; i++)
        buffer[2 + i] = ebcdic ("3278".charAt (i));

      buffer[9] = 0x02;                     // modelo 2
      buffer[10] = 0x05;                    // tamanho do nome + 1
      for (int i = 0; i < 4; i++)
        buffer[11 + i] = ebcdic ("NONE".charAt (i));

      String text = report (new RPQNames (buffer));

      assertTrue (text.contains ("3278"), text);
      assertTrue (text.contains ("NONE"), text);
    }

    @Test
    @DisplayName ("um nome RPQ vazio e aceito")
    void emptyRpqName ()
    {
      byte[] buffer = new byte[11];
      buffer[0] = StructuredField.QUERY_REPLY;
      buffer[1] = QueryReplyField.RPQ_NAMES_REPLY;
      buffer[10] = 0x01;                    // tamanho 1 significa nome vazio

      assertNotNull (report (new RPQNames (buffer)));
    }

    @Test
    @DisplayName ("a reply que geramos pode ser lida de volta")
    void buildsReply ()
    {
      RPQNames field = new RPQNames ();

      byte[] packed = new byte[field.replySize ()];
      field.packReply (packed, 0);

      assertNotNull (QueryReplyField
          .getReplyField (Arrays.copyOfRange (packed, 2, packed.length)));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Highlight")
  class Highlights
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le os pares de atributo e acao")
    void readsPairs ()
    {
      Highlight field = new Highlight (reply (QueryReplyField.HIGHLIGHT_QUERY_REPLY,
                                              0x03, 0xF0, 0x00, 0xF1, 0xF1, 0xF2, 0xF2));

      assertTrue (report (field).contains ("3"), report (field));
    }

    @Test
    @DisplayName ("sem pares declarados o relatorio nao lista nada")
    void noPairs ()
    {
      Highlight field =
          new Highlight (reply (QueryReplyField.HIGHLIGHT_QUERY_REPLY, 0x00));

      assertNotNull (report (field));
    }

    @Test
    @DisplayName ("a reply que geramos pode ser lida de volta")
    void buildsReply ()
    {
      Highlight field = new Highlight ();

      byte[] packed = new byte[field.replySize ()];
      field.packReply (packed, 0);

      assertNotNull (QueryReplyField
          .getReplyField (Arrays.copyOfRange (packed, 2, packed.length)));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("CharacterSets")
  class Characters
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le as flags, o tamanho do slot e os tipos de carga")
    void readsHeader ()
    {
      byte[] buffer = new byte[11 + 9];
      buffer[0] = StructuredField.QUERY_REPLY;
      buffer[1] = QueryReplyField.CHARACTER_SETS_REPLY;
      buffer[2] = (byte) 0x82;              // flags1
      buffer[3] = 0x00;                     // flags2
      buffer[4] = 0x09;                     // largura do slot
      buffer[5] = 0x0C;                     // altura do slot
      buffer[10] = 0x09;                    // tamanho do descritor

      CharacterSets field = (CharacterSets) QueryReplyField.getReplyField (buffer);

      assertEquals (9, field.descriptorLength);
      assertEquals (1, field.descriptors.size ());
      assertNotNull (report (field));
    }

    @Test
    @DisplayName ("a reply que geramos pode ser lida de volta")
    void buildsReply ()
    {
      CharacterSets field = new CharacterSets ();

      byte[] packed = new byte[field.replySize ()];
      field.packReply (packed, 0);

      assertNotNull (QueryReplyField
          .getReplyField (Arrays.copyOfRange (packed, 2, packed.length)));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("outras replies simples")
  class Others
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("ReplyModes le os modos suportados")
    void replyModes ()
    {
      ReplyModes field = (ReplyModes) QueryReplyField
          .getReplyField (reply (QueryReplyField.REPLY_MODES_REPLY, 0x00, 0x01, 0x02));

      assertNotNull (report (field));
    }

    @Test
    @DisplayName ("Color le os pares de cor")
    void color ()
    {
      Color field = (Color) QueryReplyField.getReplyField (
          reply (QueryReplyField.COLOR_QUERY_REPLY, 0x00, 0x02, 0x00, 0xF4, 0xF1, 0xF1));

      assertNotNull (report (field));
    }

    @Test
    @DisplayName ("DistributedDataManagement le limites e subconjuntos")
    void ddm ()
    {
      DistributedDataManagement field =
          (DistributedDataManagement) QueryReplyField.getReplyField (
              reply (QueryReplyField.DISTRIBUTED_DATA_MANAGEMENT_REPLY, 0x00, 0x00, 0x08,
                     0x00, 0x08, 0x00, 0x01, 0x01, 0x02, 0x01));

      assertNotNull (report (field));
    }

    @Test
    @DisplayName ("Transparency, Segment e AuxilliaryDevices sao apenas declaracoes")
    void markerReplies ()
    {
      for (byte type : new byte[] { QueryReplyField.TRANSPARENCY_REPLY,
                                    QueryReplyField.SEGMENT_REPLY,
                                    QueryReplyField.AUXILLIARY_DEVICE_REPLY })
      {
        QueryReplyField field = QueryReplyField.getReplyField (reply (type, 0x00, 0x00));

        assertNotNull (report (field));
      }
    }

    @Test
    @DisplayName ("AuxilliaryDevices e DistributedDataManagement se constroem sozinhos")
    void buildsSimpleReplies ()
    {
      for (QueryReplyField field : List.of (new AuxilliaryDevices (),
                                            new DistributedDataManagement (),
                                            new ReplyModes (), new Color ()))
      {
        byte[] packed = new byte[field.replySize ()];
        field.packReply (packed, 0);

        assertNotNull (QueryReplyField
            .getReplyField (Arrays.copyOfRange (packed, 2, packed.length)));
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Summary")
  class Summaries
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o resumo lista a si mesmo e todas as replies recebidas")
    void listsEverything ()
    {
      List<QueryReplyField> replies =
          new ArrayList<> (List.of (new Color (), new Highlight (), new ReplyModes ()));

      Summary summary = new Summary (replies);

      assertEquals (4, summary.size ());          // as tres mais o proprio resumo
      assertEquals (8, summary.replySize ());     // 4 bytes de cabecalho + 4 tipos
    }

    @Test
    @DisplayName ("o iterador percorre o resumo e as replies")
    void iterates ()
    {
      Summary summary = new Summary (new ArrayList<> (List.of (new Color ())));

      int count = 0;
      for (QueryReplyField field : summary)
      {
        assertNotNull (field);
        count++;
      }

      assertEquals (2, count);
    }

    @Test
    @DisplayName ("isListed reconhece os tipos declarados")
    void recognisesListedTypes ()
    {
      Summary built = new Summary (new ArrayList<> (List.of (new Color ())));

      byte[] packed = new byte[built.replySize ()];
      built.packReply (packed, 0);

      Summary parsed =
          (Summary) QueryReplyField.getReplyField (Arrays.copyOfRange (packed, 2,
                                                                     packed.length));

      assertTrue (parsed.isListed (QueryReplyField.SUMMARY_QUERY_REPLY));
      assertTrue (parsed.isListed (QueryReplyField.COLOR_QUERY_REPLY));
      assertFalse (parsed.isListed (QueryReplyField.SEGMENT_REPLY));
    }

    @Test
    @DisplayName ("um resumo lido do host conta os tipos declarados")
    void parsedSummaryCountsTypes ()
    {
      Summary summary = (Summary) QueryReplyField
          .getReplyField (reply (QueryReplyField.SUMMARY_QUERY_REPLY, 0x80, 0x81, 0x86));

      assertEquals (3, summary.size ());
    }

    @Test
    @DisplayName ("o relatorio marca as replies prometidas que nao chegaram")
    void marksMissingReplies ()
    {
      Summary summary = (Summary) QueryReplyField
          .getReplyField (reply (QueryReplyField.SUMMARY_QUERY_REPLY, 0x80, 0x86, 0xB0));

      // so o resumo chegou: as outras duas estao faltando
      summary.addReplyFields (List.of (summary));
      String text = summary.toString ();

      assertEquals (2, count (text, "** missing **"), text);
    }

    @Test
    @DisplayName ("o relatorio aponta as replies que chegaram sem estar no resumo")
    void marksUnlistedReplies ()
    {
      Summary summary = (Summary) QueryReplyField
          .getReplyField (reply (QueryReplyField.SUMMARY_QUERY_REPLY, 0x80));
      Color color = (Color) QueryReplyField
          .getReplyField (reply (QueryReplyField.COLOR_QUERY_REPLY, 0x00, 0x00));

      summary.addReplyFields (List.of (summary, color));
      String text = summary.toString ();

      assertTrue (text.contains ("Not listed in Summary:"), text);
      assertTrue (text.contains ("Color"), text);
    }

    @Test
    @DisplayName ("uma reply que consta do resumo e nao chegou fica marcada nela mesma")
    void otherRepliesReportTheirOwnStatus ()
    {
      Summary summary = (Summary) QueryReplyField
          .getReplyField (reply (QueryReplyField.SUMMARY_QUERY_REPLY, 0x80, 0x86));
      Color color = (Color) QueryReplyField
          .getReplyField (reply (QueryReplyField.COLOR_QUERY_REPLY, 0x00, 0x00));
      Segment segment = (Segment) QueryReplyField
          .getReplyField (reply (QueryReplyField.SEGMENT_REPLY, 0x00));

      List<QueryReplyField> replies = List.of (summary, color, segment);
      color.addReplyFields (replies);
      segment.addReplyFields (replies);

      assertFalse (color.toString ().contains ("missing"), color.toString ());
      assertTrue (segment.toString ().contains ("** missing **"), segment.toString ());
    }

    @Test
    @DisplayName ("sem resumo na lista cada reply informa que nao ha resumo")
    void noSummaryAtAll ()
    {
      Color color = (Color) QueryReplyField
          .getReplyField (reply (QueryReplyField.COLOR_QUERY_REPLY, 0x00, 0x00));

      color.addReplyFields (List.of (color));

      assertTrue (color.toString ().contains ("no summary"), color.toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  private static int count (String text, String needle)
  // ---------------------------------------------------------------------------------//
  {
    int total = 0;
    for (int index = text.indexOf (needle); index >= 0;
        index = text.indexOf (needle, index + needle.length ()))
      total++;

    return total;
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("brief")
  class Brief
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("resume tipo e nome numa linha")
    void summarisesInOneLine ()
    {
      QueryReplyField field = QueryReplyField
          .getReplyField (reply (QueryReplyField.COLOR_QUERY_REPLY, 0x00, 0x00));

      assertEquals ("Type  : 86 Color", field.brief ());
    }

    @Test
    @DisplayName ("um tipo desconhecido tem nome genérico")
    void unknownTypeName ()
    {
      QueryReplyField field = QueryReplyField.getReplyField (reply ((byte) 0x7F, 0x00));

      assertEquals ("Type  : 7F Unknown Reply Type", field.brief ());
    }
  }
}
