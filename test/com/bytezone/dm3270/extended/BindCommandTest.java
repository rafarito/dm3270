package com.bytezone.dm3270.extended;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.utilities.Dm3270Utility;

// -----------------------------------------------------------------------------------//
@DisplayName ("BindCommand - imagem BIND enviada pelo host (SNA)")
class BindCommandTest
// -----------------------------------------------------------------------------------//
{
  private static final byte[] HEADER = { 0x03, 0x00, 0x00, 0x00, 0x00 };   // BIND_IMAGE

  // O byte 26 (controle criptografico) decide onde o resto do BIND comeca: zero
  // significa "sem campo NS", qualquer outro valor empurra tudo 8 bytes adiante.
  private static final int NS_FIELD_LENGTH = 8;

  // ---------------------------------------------------------------------------------//
  //  Construcao dos buffers
  // ---------------------------------------------------------------------------------//

  // Monta um BIND bem formado. luName vai para o campo de nome da LU primaria,
  // userData para o campo de dados do usuario e extra e concatenado no fim.
  private static Bind bind ()
  {
    return new Bind ();
  }

  // ---------------------------------------------------------------------------------//
  private static class Bind
  // ---------------------------------------------------------------------------------//
  {
    private int format = 0;
    private int type = 0;
    private int fmProfile = 0x03;
    private int tsProfile = 0x03;
    private int psProfile = 0x02;
    private int primaryLu = 0x00;
    private int secondaryLu = 0x00;
    private int commonLu = 0x00;
    private int flags = 0x00;
    private int primaryRows = 24;
    private int primaryColumns = 80;
    private int alternateRows = 0;
    private int alternateColumns = 0;
    private int presentationSpace = 0x7E;
    private int compression = 0x00;
    private int crypto = 0x00;
    private String luName = "DBM01012";
    private byte[] userData = new byte[0];
    private byte[] extra = new byte[0];

    Bind format (int value)
    {
      format = value;
      return this;
    }

    Bind type (int value)
    {
      type = value;
      return this;
    }

    Bind profiles (int fm, int ts, int ps)
    {
      fmProfile = fm;
      tsProfile = ts;
      psProfile = ps;
      return this;
    }

    Bind luProtocols (int primary, int secondary)
    {
      primaryLu = primary;
      secondaryLu = secondary;
      return this;
    }

    Bind commonLu (int value)
    {
      commonLu = value;
      return this;
    }

    Bind flags (int value)
    {
      flags = value;
      return this;
    }

    Bind screen (int rows, int columns, int altRows, int altColumns)
    {
      primaryRows = rows;
      primaryColumns = columns;
      alternateRows = altRows;
      alternateColumns = altColumns;
      return this;
    }

    Bind presentationSpace (int value)
    {
      presentationSpace = value;
      return this;
    }

    Bind compression (int value)
    {
      compression = value;
      return this;
    }

    Bind crypto (int value)
    {
      crypto = value;
      return this;
    }

    Bind luName (String value)
    {
      luName = value;
      return this;
    }

    Bind userData (String value)
    {
      userData = ebcdic (value);
      return this;
    }

    // bytes soltos depois do campo de dados do usuario
    Bind extra (byte... value)
    {
      extra = value;
      return this;
    }

    byte[] buffer ()
    {
      byte[] name = ebcdic (luName);
      int nsOffset = crypto == 0 ? 0 : NS_FIELD_LENGTH;
      int userDataOffset = 28 + nsOffset + name.length;
      byte[] data = new byte[userDataOffset + 1 + userData.length + extra.length];

      data[0] = 0x31;                                     // sempre 0x31
      data[1] = (byte) (((format & 0x0F) << 4) | (type & 0x0F));
      data[2] = (byte) fmProfile;
      data[3] = (byte) tsProfile;
      data[4] = (byte) primaryLu;
      data[5] = (byte) secondaryLu;
      data[6] = (byte) commonLu;
      data[14] = (byte) psProfile;
      data[15] = (byte) flags;
      data[20] = (byte) primaryRows;
      data[21] = (byte) primaryColumns;
      data[22] = (byte) alternateRows;
      data[23] = (byte) alternateColumns;
      data[24] = (byte) presentationSpace;
      data[25] = (byte) compression;
      data[26] = (byte) crypto;

      data[27 + nsOffset] = (byte) name.length;
      System.arraycopy (name, 0, data, 28 + nsOffset, name.length);

      data[userDataOffset] = (byte) userData.length;
      System.arraycopy (userData, 0, data, userDataOffset + 1, userData.length);
      System.arraycopy (extra, 0, data, userDataOffset + 1 + userData.length,
                        extra.length);

      return data;
    }

    BindCommand command ()
    {
      byte[] data = buffer ();
      return new BindCommand (new CommandHeader (HEADER), data, 0, data.length);
    }
  }

  // ---------------------------------------------------------------------------------//
  private static byte[] ebcdic (String text)
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = new byte[text.length ()];
    for (int i = 0; i < text.length (); i++)
      buffer[i] = (byte) Dm3270Utility.asc2ebc[text.charAt (i)];

    return buffer;
  }

  // Divide por \n, e nao por \R: o relatorio mistura "\n" literais (separadores de
  // bloco) com %n (\r\n no Windows), e so o "\n" aparece em todos os finais de linha.
  // O \r do Windows sobra no fim de cada pedaco e sai no trim de value ().
  private static String line (String report, String label)
  {
    for (String line : report.split ("\n"))
      if (line.startsWith (label))
        return line;

    throw new AssertionError ("linha ausente no relatorio: " + label);
  }

  // Descarta "Rotulo ....." e devolve o que sobra de "Rotulo ..... XX  texto".
  private static String value (String segment)
  {
    return segment.replaceFirst ("^[^.]*\\.+", "").trim ();
  }

  // Extrai o valor de uma linha simples do toString().
  private static String field (String report, String label)
  {
    return value (line (report, label));
  }

  // As linhas do bloco de LU trazem duas colunas de 35 caracteres (%-35s %-35s).
  private static String[] luFields (String report, String label)
  {
    String line = line (report, label);

    return new String[] { value (line.substring (0, 35)), value (line.substring (35)) };
  }


  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("identificacao do comando")
  class Identification
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o nome do comando e Bind")
    void hasName ()
    {
      assertEquals ("Bind", bind ().command ().getName ());
    }

    @Test
    @DisplayName ("guarda o cabecalho TN3270E recebido")
    void keepsCommandHeader ()
    {
      CommandHeader commandHeader = new CommandHeader (HEADER);
      byte[] data = bind ().buffer ();

      BindCommand command = new BindCommand (commandHeader, data, 0, data.length);

      assertSame (commandHeader, command.getCommandHeader ());
    }

    @Test
    @DisplayName ("um buffer que nao comeca com 0x31 viola a assercao")
    void rejectsWrongCommandByte ()
    {
      byte[] data = bind ().buffer ();
      data[0] = 0x32;

      assertThrows (AssertionError.class,
                    () -> new BindCommand (new CommandHeader (HEADER), data, 0,
                                           data.length));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("formato e negociabilidade")
  class FormatAndType
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "byte 1 = {0}{1} -> format {0}, type {1}")
    @CsvSource ({ "0, 0", "0, 1", "1, 3", "0xF, 0xF" })
    @DisplayName ("o byte 1 traz formato no nibble alto e tipo no baixo")
    void splitsNibbles (int format, int type)
    {
      String report = bind ().format (format).type (type).command ().toString ();

      assertEquals (String.format ("%02X  must be zero", format),
                    field (report, "Format ."));
      assertTrue (field (report, "Type .").startsWith (String.format ("%02X", type)),
                  field (report, "Type ."));
    }

    @Test
    @DisplayName ("type 1 significa BIND nao negociavel")
    void typeOneIsNonNegotiable ()
    {
      assertEquals ("01  non-negotiable",
                    field (bind ().type (1).command ().toString (), "Type ."));
    }

    @Test
    @DisplayName ("qualquer outro type e negociavel")
    void otherTypesAreNegotiable ()
    {
      assertEquals ("00  negotiable",
                    field (bind ().type (0).command ().toString (), "Type ."));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("perfis FM / TS / PS")
  class Profiles
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le os tres perfis das posicoes 2, 3 e 14")
    void readsProfiles ()
    {
      String report = bind ().profiles (0x03, 0x03, 0x02).command ().toString ();

      assertEquals ("03", field (report, "FM profile"));
      assertEquals ("03", field (report, "TS profile"));
      assertEquals ("02", field (report, "PS profile"));
    }

    @Test
    @DisplayName ("PS profile 02 habilita o bloco de espaco de apresentacao")
    void psProfileTwoParsesScreen ()
    {
      String report =
          bind ().profiles (0x03, 0x03, 0x02).screen (24, 80, 43, 80).command ().toString ();

      assertEquals ("18   24", field (report, "Default rows"));
      assertEquals ("50   80", field (report, "Default columns"));
    }

    @Test
    @DisplayName ("PS profile diferente de 02 deixa o bloco de tela zerado")
    void otherPsProfileSkipsScreen ()
    {
      // com psProfile != 2 o construtor nao le rows/columns nem o nome da LU
      BindCommand command =
          bind ().profiles (0x03, 0x03, 0x01).screen (24, 80, 0, 0).command ();
      String report = command.toString ();

      assertEquals ("01", field (report, "PS profile"));
      assertEquals ("00    0", field (report, "Default rows"));
      assertEquals ("00    0", field (report, "Default columns"));
      assertEquals ("00", field (report, "NS offset"));
      assertEquals ("00", field (report, "User data offset"));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("protocolos das LUs")
  class LuProtocols
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o relatorio mostra LU primaria e secundaria lado a lado")
    void reportsBothColumns ()
    {
      // 0xB1 = chaining 1, mode 0, response 11, commit 0, scb 0, endBracket 1
      // 0x90 = chaining 1, mode 0, response 01, commit 0, scb 0, endBracket 0
      String report = bind ().luProtocols (0xB1, 0x90).command ().toString ();

      assertArrayEquals (new String[] { "01", "01" },
                         luFields (report, "Chaining use"));
      assertArrayEquals (new String[] { "03", "01" },
                         luFields (report, "Response protocol"));
      assertArrayEquals (new String[] { "01", "00" },
                         luFields (report, "Send End Bracket"));
    }

    @Test
    @DisplayName ("as duas colunas sao independentes")
    void columnsAreIndependent ()
    {
      String report = bind ().luProtocols (0xFF, 0x00).command ().toString ();

      assertArrayEquals (new String[] { "01", "00" }, luFields (report, "Chaining use"));
      assertArrayEquals (new String[] { "01", "00" },
                         luFields (report, "Mode selection"));
      assertArrayEquals (new String[] { "03", "00" },
                         luFields (report, "Response protocol"));
      assertArrayEquals (new String[] { "01", "00" }, luFields (report, "Commit"));
    }

    @Test
    @DisplayName ("os seis campos da LU saem em linhas separadas")
    void reportsEveryLuField ()
    {
      String report = bind ().luProtocols (0xFF, 0x00).command ().toString ();

      for (String label : new String[] { "Chaining use", "Mode selection",
                                         "Response protocol", "Commit",
                                         "SCB compression", "Send End Bracket" })
        assertTrue (report.contains (label), "falta " + label);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("layout do relatorio")
  class ReportLayout
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("as colunas de LU nao carregam resto de separador de linha")
    void luLinesHaveNoSeparatorResidue ()
    {
      // LogicalUnit.toString () termina cada linha com %n e BindCommand divide o
      // resultado com split ("\\R"), que consome o separador inteiro. Dividir por "\n"
      // deixaria o \r do Windows no meio da linha de duas colunas, e num terminal ele
      // devolveria o cursor ao inicio: a coluna secundaria apagaria a primaria.
      String report = bind ().luProtocols (0xB1, 0x90).command ().toString ();
      // o pedaco devolvido por split ("\n") pode terminar com o \r do proprio %n da
      // linha; o que nao pode mais existir e um \r no MEIO, entre as duas colunas
      String luLine = line (report, "Chaining use").replaceAll ("\r$", "");

      assertFalse (luLine.contains ("\r"), luLine.replace ("\r", "<CR>"));
      assertArrayEquals (new String[] { "01", "01" },
                         luFields (report, "Chaining use"));
    }

    @Test
    @DisplayName ("os blocos aparecem na ordem documentada")
    void sectionsInOrder ()
    {
      String report = bind ().command ().toString ();

      int previous = -1;
      for (String heading : new String[] { "BND:", "---- Primary LU", "---- Common LU",
                                           "---- Cryptography", "--- Presentation Space" })
      {
        int index = report.indexOf (heading);
        assertTrue (index > previous, "fora de ordem: " + heading);
        previous = index;
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("protocolos comuns (byte 6)")
  class CommonProtocols
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("todos os bits ligados viram 01 em cada campo")
    void allBitsSet ()
    {
      String report = bind ().commonLu (0xFF).command ().toString ();

      for (String label : new String[] { "Whole BIUs", "FM header usage",
                                         "Brackets usage", "Brackets termination",
                                         "Alt code set", "Set availability", "BIS sent",
                                         "Bind queuing" })
        assertEquals ("01", field (report, label), label);
    }

    @Test
    @DisplayName ("todos os bits desligados viram 00 em cada campo")
    void noBitsSet ()
    {
      String report = bind ().commonLu (0x00).command ().toString ();

      for (String label : new String[] { "Whole BIUs", "FM header usage",
                                         "Brackets usage", "Brackets termination",
                                         "Alt code set", "Set availability", "BIS sent",
                                         "Bind queuing" })
        assertEquals ("00", field (report, label), label);
    }

    @ParameterizedTest (name = "byte 6 = {0} isola {1}")
    @CsvSource ({ "0x80, Whole BIUs", "0x40, FM header usage", "0x20, Brackets usage",
                  "0x10, Brackets termination", "0x08, Alt code set",
                  "0x04, Set availability", "0x02, BIS sent", "0x01, Bind queuing" })
    @DisplayName ("cada bit alimenta um campo diferente")
    void eachBitMapsToOneField (int value, String label)
    {
      String report = bind ().commonLu (value).command ().toString ();

      assertEquals ("01", field (report, label));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("espaco de apresentacao")
  class PresentationSpace
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o bit 0 do byte 15 declara suporte a Query")
    void querySupported ()
    {
      assertEquals ("80  true",
                    field (bind ().flags (0x80).command ().toString (),
                           "Query supported"));
    }

    @Test
    @DisplayName ("sem o bit 0 o Query nao e suportado")
    void queryNotSupported ()
    {
      assertEquals ("7F  false",
                    field (bind ().flags (0x7F).command ().toString (),
                           "Query supported"));
    }

    @Test
    @DisplayName ("le tela default e alternativa das posicoes 20 a 23")
    void readsBothScreenSizes ()
    {
      String report = bind ().screen (24, 80, 43, 80).command ().toString ();

      assertEquals ("18   24", field (report, "Default rows"));
      assertEquals ("50   80", field (report, "Default columns"));
      assertEquals ("2B   43", field (report, "Alternate Rows"));
      assertEquals ("50   80", field (report, "Alternate Columns"));
    }

    @Test
    @DisplayName ("dimensoes acima de 127 nao viram negativas")
    void readsUnsignedDimensions ()
    {
      String report = bind ().screen (0xFF, 0xFF, 0xFF, 0xFF).command ().toString ();

      assertEquals ("FF  255", field (report, "Default rows"));
      assertEquals ("FF  255", field (report, "Alternate Columns"));
    }

    @ParameterizedTest (name = "0x{0} -> {1}")
    @CsvSource ({ "0, Undefined", "1, '12 x 40'", "2, '24 x 80'",
                  "3, 'default 24 x 80, alternate in Query Reply'" })
    @DisplayName ("valores baixos indexam a tabela direto")
    void describesLowValues (int value, String expected)
    {
      assertEquals (String.format ("%02X  %s", value, expected),
                    field (bind ().presentationSpace (value).command ().toString (),
                           "Presentation space"));
    }

    @ParameterizedTest (name = "0x{0} -> indice {0} - 0x7A")
    @CsvSource ({ "0x7A, Undefined", "0x7B, '12 x 40'", "0x7C, '24 x 80'",
                  "0x7D, 'default 24 x 80, alternate in Query Reply'",
                  "0x7E, 'fixed size as defined by default values'",
                  "0x7F, 'both default and alternate as specifed'" })
    @DisplayName ("valores a partir de 0x7A sao deslocados antes de indexar")
    void describesHighValues (int value, String expected)
    {
      assertEquals (String.format ("%02X  %s", value, expected),
                    field (bind ().presentationSpace (value).command ().toString (),
                           "Presentation space"));
    }

    @ParameterizedTest (name = "0x{0}")
    @ValueSource (ints = { 0x04, 0x10, 0x50, 0x79 })
    @DisplayName ("valores entre 0x04 e 0x79 sao descritos pelo codigo recebido")
    void middleValuesAreDescribed (int value)
    {
      // presentationText cobre 0x00-0x03 e 0x7A-0x7F; fora dessas faixas o relatorio
      // mostra o codigo em vez de indexar a tabela
      BindCommand command = bind ().presentationSpace (value).command ();

      assertEquals (String.format ("%02X  Unknown (%02X)", value, value),
                    field (command.toString (), "Presentation space"));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("compressao")
  class Compression
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "byte 25 = {0} -> {1}")
    @CsvSource ({ "0x00, No compression", "0x01, Compression bid", "0x02, Reserved",
                  "0x03, Compression required" })
    @DisplayName ("os dois bits baixos escolhem o texto")
    void describesCompression (int value, String expected)
    {
      assertEquals (String.format ("%02X  %s", value, expected),
                    field (bind ().compression (value).command ().toString (),
                           "Compression"));
    }

    @Test
    @DisplayName ("os bits altos entram no valor mas nao no texto")
    void ignoresHighBitsForText ()
    {
      // 0xFC = 11111100: texto vem de 0x00, valor mostrado e o byte inteiro
      assertEquals ("FC  No compression",
                    field (bind ().compression (0xFC).command ().toString (),
                           "Compression"));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("criptografia e deslocamento NS")
  class Cryptography
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("controle zerado nao insere o campo NS")
    void noCryptoMeansNoOffset ()
    {
      String report = bind ().crypto (0x00).luName ("LU01").command ().toString ();

      assertEquals ("00", field (report, "NS offset"));
      assertEquals ("04", field (report, "Primary LU name len"));
      assertEquals ("LU01", field (report, "Primary LU name ."));
    }

    @Test
    @DisplayName ("controle diferente de zero empurra o nome da LU 8 bytes adiante")
    void cryptoShiftsByEightBytes ()
    {
      String report = bind ().crypto (0x40).luName ("LU01").command ().toString ();

      assertEquals ("08", field (report, "NS offset"));
      assertEquals ("04", field (report, "Primary LU name len"));
      assertEquals ("LU01", field (report, "Primary LU name ."));
    }

    @Test
    @DisplayName ("o byte 26 tambem se divide em opcoes privadas, de sessao e tamanho")
    void splitsCryptoBits ()
    {
      // 0xC5 = 11 00 0101 -> privadas 3, sessao 0, tamanho 5
      String report = bind ().crypto (0xC5).command ().toString ();

      assertEquals ("03", field (report, "Private options"));
      assertEquals ("00", field (report, "Session options ."));
      assertEquals ("05", field (report, "Session options len"));
    }

    @Test
    @DisplayName ("as opcoes de sessao vem dos bits 2 e 3")
    void readsSessionOptions ()
    {
      // 0x30 = 00 11 0000 -> privadas 0, sessao 3, tamanho 0
      String report = bind ().crypto (0x30).command ().toString ();

      assertEquals ("00", field (report, "Private options"));
      assertEquals ("03", field (report, "Session options ."));
      assertEquals ("00", field (report, "Session options len"));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("nome da LU primaria")
  class PrimaryLuName
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("decodifica o nome de EBCDIC")
    void decodesEbcdic ()
    {
      assertEquals ("DBM01012",
                    field (bind ().luName ("DBM01012").command ().toString (),
                           "Primary LU name ."));
    }

    @Test
    @DisplayName ("um nome vazio deixa o campo em branco")
    void handlesEmptyName ()
    {
      String report = bind ().luName ("").command ().toString ();

      assertEquals ("00", field (report, "Primary LU name len"));
      assertEquals ("", field (report, "Primary LU name ."));
    }

    @Test
    @DisplayName ("o tamanho do nome desloca o campo de dados do usuario")
    void nameLengthMovesUserData ()
    {
      // userDataOffset = 28 + nsOffset + tamanho do nome
      assertEquals ("20",       // 0x20 = 32 = 28 + 0 + 4
                    field (bind ().luName ("LU01").command ().toString (),
                           "User data offset"));
      assertEquals ("24",       // 0x24 = 36 = 28 + 0 + 8
                    field (bind ().luName ("DBM01012").command ().toString (),
                           "User data offset"));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("dados do usuario e bytes extras")
  class UserData
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("sem dados do usuario nao ha bytes extras")
    void noUserData ()
    {
      String report = bind ().command ().toString ();

      assertEquals ("00", field (report, "User data length"));
      assertEquals ("00", field (report, "Extra bytes"));
    }

    @Test
    @DisplayName ("le o tamanho declarado do campo de dados do usuario")
    void readsUserDataLength ()
    {
      assertEquals ("06",
                    field (bind ().userData ("ABCDEF").command ().toString (),
                           "User data length"));
    }

    @Test
    @DisplayName ("bytes depois do campo de dados do usuario contam como extras")
    void countsExtraBytes ()
    {
      // um sub-campo extra: 1 byte de tamanho + 3 bytes de conteudo
      byte[] name = ebcdic ("ABC");
      BindCommand command = bind ().userData ("XY")
          .extra ((byte) name.length, name[0], name[1], name[2]).command ();

      assertEquals ("04", field (command.toString (), "Extra bytes"));
    }

    @Test
    @DisplayName ("o dump dos bytes extras entra no fim do relatorio")
    void dumpsExtraBytes ()
    {
      byte[] name = ebcdic ("ABC");
      BindCommand command = bind ()
          .extra ((byte) name.length, name[0], name[1], name[2]).command ();

      String report = command.toString ();

      assertTrue (report.contains ("Extra bytes .......... 04"), report);
      assertTrue (report.trim ().endsWith ("ABC"), report);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("serializacao")
  class Serialisation
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("getData prefixa o cabecalho de 5 bytes")
    void prependsHeader ()
    {
      byte[] data = bind ().buffer ();
      BindCommand command = new BindCommand (new CommandHeader (HEADER), data, 0,
                                             data.length);

      byte[] result = command.getData ();

      assertEquals (data.length + 5, result.length);
      assertEquals (data.length + 5, command.size ());
      assertArrayEquals (HEADER, java.util.Arrays.copyOf (result, 5));
      assertArrayEquals (data, java.util.Arrays.copyOfRange (result, 5, result.length));
    }

    @Test
    @DisplayName ("getTelnetData termina em IAC EOR")
    void appendsEor ()
    {
      byte[] telnet = bind ().command ().getTelnetData ();

      assertEquals ((byte) 0xFF, telnet[telnet.length - 2]);
      assertEquals ((byte) 0xEF, telnet[telnet.length - 1]);
    }

    @Test
    @DisplayName ("getTelnetData duplica os 0xFF do corpo")
    void escapesFF ()
    {
      // 0xFF nas dimensoes de tela: 4 bytes que precisam de escape
      BindCommand command = bind ().screen (0xFF, 0xFF, 0xFF, 0xFF).command ();

      byte[] data = command.getData ();
      byte[] telnet = command.getTelnetData ();

      assertEquals (data.length + 4 + 2, telnet.length);
    }

    @Test
    @DisplayName ("a resposta comeca vazia")
    void replyStartsEmpty ()
    {
      assertFalse (bind ().command ().getReply ().isPresent ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("LogicalUnit")
  class LogicalUnits
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "0x{0} -> chaining={1} mode={2} response={3}")
    @CsvSource ({ "0x00, 0, 0, 0", "0x80, 1, 0, 0", "0x40, 0, 1, 0", "0x30, 0, 0, 3",
                  "0x10, 0, 0, 1", "0x20, 0, 0, 2", "0xF0, 1, 1, 3" })
    @DisplayName ("separa os bits altos em encadeamento, modo e protocolo")
    void splitsHighBits (int value, int chaining, int mode, int response)
    {
      LogicalUnit lu = new LogicalUnit ((byte) value);

      assertEquals (chaining, lu.chainingUse);
      assertEquals (mode, lu.modeSelection);
      assertEquals (response, lu.responseProtocol);
    }

    @ParameterizedTest (name = "0x{0} -> commit={1} scb={2} endBracket={3}")
    @CsvSource ({ "0x00, 0, 0, 0", "0x08, 1, 0, 0", "0x02, 0, 1, 0", "0x01, 0, 0, 1",
                  "0x0B, 1, 1, 1" })
    @DisplayName ("separa os bits baixos em commit, compressao e end bracket")
    void splitsLowBits (int value, int commit, int scb, int endBracket)
    {
      LogicalUnit lu = new LogicalUnit ((byte) value);

      assertEquals (commit, lu.commit);
      assertEquals (scb, lu.scbCompression);
      assertEquals (endBracket, lu.sendEndBracket);
    }

    @Test
    @DisplayName ("o bit 4 nao e lido por nenhum campo")
    void bitFourIsIgnored ()
    {
      // 0x04 fica entre responseProtocol (0x30) e commit (0x08): nenhum campo o le
      LogicalUnit lu = new LogicalUnit ((byte) 0x04);

      assertEquals (0, lu.chainingUse);
      assertEquals (0, lu.modeSelection);
      assertEquals (0, lu.responseProtocol);
      assertEquals (0, lu.commit);
      assertEquals (0, lu.scbCompression);
      assertEquals (0, lu.sendEndBracket);
    }

    @Test
    @DisplayName ("toString devolve seis linhas rotuladas")
    void printsSixLines ()
    {
      String[] lines = new LogicalUnit ((byte) 0xFF).toString ().split ("\n");

      assertEquals (6, lines.length);
      assertTrue (lines[0].startsWith ("Chaining use"));
      assertTrue (lines[5].startsWith ("Send End Bracket"));
    }
  }
}
