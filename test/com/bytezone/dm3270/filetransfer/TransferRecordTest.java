package com.bytezone.dm3270.filetransfer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.security.InvalidParameterException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.filetransfer.Transfer.TransferContents;
import com.bytezone.dm3270.filetransfer.Transfer.TransferType;

// -----------------------------------------------------------------------------------//
@DisplayName ("TransferRecord - registros do protocolo IND$FILE")
class TransferRecordTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  //  Construcao dos buffers
  // ---------------------------------------------------------------------------------//

  private static byte[] bytes (int... values)
  {
    byte[] buffer = new byte[values.length];
    for (int i = 0; i < values.length; i++)
      buffer[i] = (byte) values[i];

    return buffer;
  }

  private static byte[] ascii (String text)
  {
    return text.getBytes (java.nio.charset.StandardCharsets.US_ASCII);
  }

  // Monta um campo estruturado D0 completo: tipo, subtipo e os registros que seguem.
  private static byte[] transferField (int rectype, int subtype, byte[]... records)
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    out.write (0xD0);
    out.write (rectype);
    out.write (subtype);
    for (byte[] record : records)
      out.write (record, 0, record.length);

    return out.toByteArray ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("TransferRecord")
  class Generic
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o segundo byte declara o tamanho do registro")
    void lengthComesFromSecondByte ()
    {
      byte[] buffer = bytes (0x01, 0x04, 0xAA, 0xBB, 0xCC, 0xDD);

      TransferRecord record = new TransferRecord (buffer, 0);

      assertEquals (4, record.length ());
      assertEquals ("01 04 AA BB", record.toString ().substring (12));
    }

    @Test
    @DisplayName ("le o registro a partir de um deslocamento")
    void readsFromOffset ()
    {
      byte[] buffer = bytes (0xFF, 0xFF, 0x01, 0x03, 0xAA, 0xBB);

      TransferRecord record = new TransferRecord (buffer, 2);

      assertEquals (3, record.length ());
      assertTrue (record.toString ().endsWith ("01 03 AA"), record.toString ());
    }

    @Test
    @DisplayName ("o construtor de tamanho cria um registro zerado")
    void sizeOnlyConstructor ()
    {
      TransferRecord record = new TransferRecord (5);

      assertEquals (5, record.length ());
      assertTrue (record.toString ().endsWith ("00 00 00 00 00"),
                  record.toString ());
    }

    @Test
    @DisplayName ("pack copia o registro para o buffer de saida")
    void packsIntoBuffer ()
    {
      byte[] buffer = bytes (0x01, 0x03, 0xAA);
      TransferRecord record = new TransferRecord (buffer, 0);

      byte[] target = new byte[6];
      int ptr = record.pack (target, 2);

      assertEquals (5, ptr);
      assertArrayEquals (bytes (0x00, 0x00, 0x01, 0x03, 0xAA, 0x00), target);
    }

    @Test
    @DisplayName ("o dump de um registro nao termina com espaco sobrando")
    void hexDumpHasNoTrailingSpace ()
    {
      // Dm3270Utility.toHexString (byte[]) delega para a versao de tres argumentos, que
      // remove o separador final. Todo toString de TransferRecord herda isso.
      byte[] buffer = bytes (0x01, 0x03, 0xAA);

      assertEquals ("record    : 01 03 AA", new TransferRecord (buffer, 0).toString ());
    }

    @Test
    @DisplayName ("um tamanho declarado maior que o buffer estoura na copia")
    void declaredLengthBeyondBuffer ()
    {
      byte[] buffer = bytes (0x01, 0x10, 0xAA);

      assertThrows (IndexOutOfBoundsException.class,
                    () -> new TransferRecord (buffer, 0));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("RecordNumber")
  class RecordNumbers
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("empacota o numero em quatro bytes big endian")
    void packsNumber ()
    {
      RecordNumber record = new RecordNumber (0x01020304);

      assertEquals (6, record.length ());
      assertTrue (record.toString ().endsWith ("63 06 01 02 03 04"),
                  record.toString ());
    }

    @Test
    @DisplayName ("o tipo do registro e 0x63")
    void hasType63 ()
    {
      assertTrue (new RecordNumber (1).toString ().contains ("63 06"));
    }

    @Test
    @DisplayName ("le o numero de um registro que comeca no inicio do buffer")
    void readsNumberAtOffsetZero ()
    {
      byte[] buffer = bytes (0x63, 0x06, 0x00, 0x00, 0x01, 0x00);

      assertEquals (256, new RecordNumber (buffer, 0).recordNumber);
    }

    @Test
    @DisplayName ("com deslocamento o numero e lido do proprio registro")
    void readsNumberFromTheRecord ()
    {
      // o construtor le de this.data (a copia do registro), e nao da posicao 2 do
      // buffer recebido
      byte[] buffer = bytes (0xD0, 0x00, 0xDE, 0xAD, 0xBE, 0xEF,     // cabecalho
                             0x63, 0x06, 0x00, 0x00, 0x00, 0x07);    // registro em 6

      RecordNumber record = new RecordNumber (buffer, 6);

      assertEquals (7, record.recordNumber);
      assertTrue (record.toString ().endsWith ("63 06 00 00 00 07"),
                  record.toString ());
    }

    @Test
    @DisplayName ("o numero empacotado sobrevive ao round trip com deslocamento")
    void numberRoundTripsAtAnyOffset ()
    {
      byte[] target = new byte[10];
      new RecordNumber (0x00ABCDEF).pack (target, 4);

      assertEquals (0x00ABCDEF, new RecordNumber (target, 4).recordNumber);
    }

    @Test
    @DisplayName ("o numero maximo nao vira negativo")
    void packsLargeNumber ()
    {
      RecordNumber record = new RecordNumber (0x7FFFFFFF);

      assertTrue (record.toString ().endsWith ("7F FF FF FF"),
                  record.toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("RecordSize")
  class RecordSizes
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("le os dois tamanhos declarados")
    void readsBothSizes ()
    {
      byte[] buffer = bytes (0x08, 0x06, 0x00, 0x50, 0x01, 0x00);

      RecordSize record = new RecordSize (buffer, 0);

      assertEquals (80, record.recordSize1);
      assertEquals (256, record.recordSize2);
    }

    @Test
    @DisplayName ("le corretamente a partir de um deslocamento")
    void readsFromOffset ()
    {
      byte[] buffer = bytes (0xD0, 0x00, 0x12,
                             0x08, 0x06, 0x00, 0x50, 0x00, 0x3F);

      RecordSize record = new RecordSize (buffer, 3);

      assertEquals (80, record.recordSize1);
      assertEquals (63, record.recordSize2);
    }

    @Test
    @DisplayName ("o relatorio mostra os dois tamanhos em decimal")
    void describesItself ()
    {
      byte[] buffer = bytes (0x08, 0x06, 0x00, 0x50, 0x00, 0x3F);

      String report = new RecordSize (buffer, 0).toString ();

      assertTrue (report.startsWith ("rec size  : 08 06 00 50 00 3F"), report);
      assertTrue (report.endsWith ("(80 or 63)"), report);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ErrorRecord")
  class ErrorRecords
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "{0} -> {1}")
    @CsvSource ({ "0x0100, Command failed", "0x2200, EOF", "0x4700, Cancel",
                  "0x0000, Unknown error", "0x9999, Unknown error" })
    @DisplayName ("cada codigo tem seu texto")
    void describesErrors (int code, String expected)
    {
      byte[] buffer = bytes (0x69, 0x04, (code >> 8) & 0xFF, code & 0xFF);

      assertTrue (new ErrorRecord (buffer, 0).toString ().endsWith ("- " + expected),
                  new ErrorRecord (buffer, 0).toString ());
    }

    @Test
    @DisplayName ("as tres constantes publicas batem com o protocolo")
    void constantsMatchProtocol ()
    {
      assertEquals (0x2200, ErrorRecord.EOF);
      assertEquals (0x4700, ErrorRecord.CANCEL);
      assertEquals (0x0100, ErrorRecord.CMD_FAIL);
    }

    @Test
    @DisplayName ("o construtor de escrita empacota o codigo em dois bytes")
    void packsErrorCode ()
    {
      ErrorRecord record = new ErrorRecord (ErrorRecord.EOF);

      assertEquals (4, record.length ());
      assertTrue (record.toString ().contains ("69 04 22 00"), record.toString ());
    }

    @Test
    @DisplayName ("o registro criado para escrita tambem descreve o erro")
    void writeConstructorDescribesTheError ()
    {
      assertTrue (new ErrorRecord (ErrorRecord.CANCEL).toString ().endsWith ("- Cancel"),
                  new ErrorRecord (ErrorRecord.CANCEL).toString ());
      assertTrue (new ErrorRecord (ErrorRecord.EOF).toString ().endsWith ("- EOF"),
                  new ErrorRecord (ErrorRecord.EOF).toString ());
      assertTrue (new ErrorRecord (ErrorRecord.CMD_FAIL).toString ()
          .endsWith ("- Command failed"));
      assertTrue (new ErrorRecord (0x9999).toString ().endsWith ("- Unknown error"));
    }

    @Test
    @DisplayName ("le o codigo a partir de um deslocamento")
    void readsFromOffset ()
    {
      byte[] buffer = bytes (0xD0, 0x00, 0x00, 0x69, 0x04, 0x22, 0x00);

      assertTrue (new ErrorRecord (buffer, 3).toString ().endsWith ("- EOF"));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ContentsRecord")
  class ContentsRecords
  // ---------------------------------------------------------------------------------//
  {
    private byte[] contents (String text)
    {
      ByteArrayOutputStream out = new ByteArrayOutputStream ();
      out.write (0x03);
      out.write (2 + text.length ());
      out.write (ascii (text), 0, text.length ());

      return out.toByteArray ();
    }

    @Test
    @DisplayName ("FT:MSG identifica uma mensagem")
    void recognisesMessage ()
    {
      ContentsRecord record = new ContentsRecord (contents ("FT:MSG "), 0);

      assertEquals (TransferContents.MSG, record.transferContents);
      assertEquals ("FT:MSG ", record.contents);
    }

    @Test
    @DisplayName ("FT:DATA identifica dados")
    void recognisesData ()
    {
      ContentsRecord record = new ContentsRecord (contents ("FT:DATA"), 0);

      assertEquals (TransferContents.DATA, record.transferContents);
    }

    @ParameterizedTest (name = "[{0}]")
    @ValueSource (strings = { "FT:XXXX", "ft:data", "FT:MSGX", "0000000" })
    @DisplayName ("qualquer outro conteudo e recusado")
    void rejectsUnknownContents (String text)
    {
      byte[] buffer = contents (text);

      assertThrows (InvalidParameterException.class,
                    () -> new ContentsRecord (buffer, 0));
    }

    @Test
    @DisplayName ("o relatorio traz o hex e o texto")
    void describesItself ()
    {
      String report = new ContentsRecord (contents ("FT:DATA"), 0).toString ();

      assertTrue (report.startsWith ("contents  : 03 09"), report);
      assertTrue (report.endsWith ("(FT:DATA)"), report);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("DataRecord")
  class DataRecords
  // ---------------------------------------------------------------------------------//
  {
    // Um DataRecord lido do stream: C0 80 <flag> <tamanho de 2 bytes> <conteudo>
    private byte[] incoming (int flag, byte[] content)
    {
      ByteArrayOutputStream out = new ByteArrayOutputStream ();
      out.write (0xC0);
      out.write (0x80);
      out.write (flag);
      int total = DataRecord.HEADER_LENGTH + content.length;
      out.write ((total >> 8) & 0xFF);
      out.write (total & 0xFF);
      out.write (content, 0, content.length);

      return out.toByteArray ();
    }

    @Test
    @DisplayName ("o flag 0x61 significa dados nao comprimidos")
    void uncompressedFlag ()
    {
      DataRecord record = new DataRecord (incoming (0x61, ascii ("CONTEUDO")), 0);

      assertEquals (8, record.getBufferLength ());
      assertEquals (13, record.length ());              // 5 de cabecalho + 8
      assertTrue (record.toString ().contains ("uncompressed"), record.toString ());
    }

    @Test
    @DisplayName ("qualquer outro flag e tratado como comprimido")
    void compressedFlag ()
    {
      DataRecord record = new DataRecord (incoming (0x00, ascii ("XX")), 0);

      assertTrue (record.toString ().contains ("(compressed"), record.toString ());
    }

    @Test
    @DisplayName ("o construtor de escrita monta o cabecalho de cinco bytes")
    void buildsHeader ()
    {
      byte[] content = ascii ("ABCDE");

      DataRecord record = new DataRecord (content, 0, content.length, false);

      assertEquals (5, record.getBufferLength ());
      byte[] target = new byte[5];
      record.pack (target, 0);

      assertArrayEquals (bytes (0xC0, 0x80, 0x61, 0x00, 0x0A), target);
    }

    @Test
    @DisplayName ("o construtor de escrita marca a compressao no cabecalho")
    void marksCompression ()
    {
      byte[] content = ascii ("AB");

      byte[] target = new byte[5];
      new DataRecord (content, 0, content.length, true).pack (target, 0);

      assertEquals (0x00, target[2]);
    }

    @Test
    @DisplayName ("o construtor de escrita copia apenas a faixa pedida")
    void copiesOnlyTheRange ()
    {
      byte[] content = ascii ("ABCDEFGH");

      DataRecord record = new DataRecord (content, 2, 3, false);

      byte[] target = new byte[3];
      record.packBuffer (target, 0);

      assertArrayEquals (ascii ("CDE"), target);
      assertEquals (3, record.getBufferLength ());
    }

    @Test
    @DisplayName ("pack grava cabecalho e conteudo em sequencia")
    void packsHeaderAndContent ()
    {
      DataRecord record = new DataRecord (incoming (0x61, ascii ("AB")), 0);

      byte[] target = new byte[7];
      int ptr = record.pack (target, 0);

      assertEquals (7, ptr);
      assertArrayEquals (bytes (0xC0, 0x80, 0x61, 0x00, 0x07, 0x41, 0x42), target);
    }

    @Test
    @DisplayName ("packBuffer aceita um destino menor e trunca")
    void packBufferTruncates ()
    {
      DataRecord record = new DataRecord (incoming (0x61, ascii ("ABCDEFGH")), 0);

      byte[] target = new byte[4];
      int ptr = record.packBuffer (target, 0);

      assertEquals (4, ptr);
      assertArrayEquals (ascii ("ABCD"), target);
    }

    @Test
    @DisplayName ("getText devolve o conteudo ASCII quando termina em espaco")
    void readsAsciiText ()
    {
      DataRecord record = new DataRecord (incoming (0x61, ascii ("TEXTO ")), 0);

      assertEquals ("TEXTO ", record.getText ());
    }

    @Test
    @DisplayName ("getText converte de EBCDIC quando nao termina em espaco ASCII")
    void readsEbcdicText ()
    {
      // 0xC1 0xC2 0xC3 = ABC em EBCDIC
      DataRecord record = new DataRecord (incoming (0x61, bytes (0xC1, 0xC2, 0xC3)), 0);

      assertEquals ("ABC", record.getText ());
    }

    @Test
    @DisplayName ("getText de um registro vazio devolve texto vazio")
    void getTextOnEmptyRecord ()
    {
      DataRecord record = new DataRecord (incoming (0x61, new byte[0]), 0);

      assertEquals (0, record.getBufferLength ());
      assertEquals ("", record.getText ());
    }

    @Test
    @DisplayName ("um ultimo byte 0xFF nao e confundido com espaco ASCII")
    void highLastByteIsNotAsciiSpace ()
    {
      // o teste do ultimo caractere usa & 0xFF, e nao % 0xFF: 0xFF vale 255, nao 0
      DataRecord record =
          new DataRecord (incoming (0x61, bytes (0xC1, 0xC2, 0xFF)), 0);

      // 0xFF nao e espaco nem dolar, entao o conteudo e lido como EBCDIC
      assertEquals ("AB", record.getText ().substring (0, 2));
    }

    @Test
    @DisplayName ("o dump escolhe a codificacao pela contagem de espacos")
    void hexDumpPicksEncoding ()
    {
      // mais 0x40 que 0x20 -> tratado como EBCDIC
      DataRecord ebcdic =
          new DataRecord (incoming (0x61, bytes (0xC1, 0x40, 0x40, 0xC2)), 0);
      // mais 0x20 que 0x40 -> tratado como ASCII
      DataRecord ascii =
          new DataRecord (incoming (0x61, bytes (0x41, 0x20, 0x20, 0x42)), 0);

      assertTrue (ebcdic.getHexBuffer ().contains ("A  B"), ebcdic.getHexBuffer ());
      assertTrue (ascii.getHexBuffer ().contains ("A  B"), ascii.getHexBuffer ());
    }

    @Test
    @DisplayName ("checkAscii aceita CRLF quando permitido")
    void checkAsciiAllowsCrLf ()
    {
      DataRecord record =
          new DataRecord (incoming (0x61, bytes (0x41, 0x0D, 0x0A, 0x42, 0x1A)), 0);

      // os metodos apenas reportam no console; o teste garante que nao lancam
      record.checkAscii (true);
      record.checkAscii (false);
      record.checkEbcdic ();
    }

    @Test
    @DisplayName ("o cabecalho aparece no toString com o tamanho do conteudo")
    void describesItself ()
    {
      DataRecord record = new DataRecord (incoming (0x61, ascii ("ABCD")), 0);

      assertTrue (record.toString ().startsWith ("header    : C0 80 61 00 09"),
                  record.toString ());
      assertTrue (record.toString ().contains ("5 + 4"), record.toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("FileTransferSF - dispatch dos registros")
  class StructuredFields
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um campo de entrada reconhece numero, erro e dados")
    void inboundDispatch ()
    {
      byte[] buffer = transferField (0x46, 0x05,
                                     bytes (0x63, 0x06, 0x00, 0x00, 0x00, 0x01),
                                     bytes (0x69, 0x04, 0x22, 0x00));

      FileTransferInboundSF sf =
          new FileTransferInboundSF (buffer, 0, buffer.length);

      String report = sf.toString ();

      assertTrue (report.startsWith ("Struct Field : D0 File Transfer Inbound"), report);
      assertTrue (report.contains ("recnum    :"), report);
      assertTrue (report.contains ("error     :"), report);
      assertTrue (report.contains ("type      : 46"), report);
      assertTrue (report.contains ("subtype   : 05"), report);
    }

    @Test
    @DisplayName ("um tipo de registro desconhecido cai no registro genérico")
    void inboundUnknownRecord ()
    {
      byte[] buffer = transferField (0x46, 0x05, bytes (0x77, 0x03, 0x00));

      FileTransferInboundSF sf = new FileTransferInboundSF (buffer, 0, buffer.length);

      assertTrue (sf.toString ().contains ("record    : 77 03 00"), sf.toString ());
    }

    @Test
    @DisplayName ("um OPEN de download e reconhecido pelo tipo 00/12")
    void outboundOpenDownload ()
    {
      ByteArrayOutputStream contents = new ByteArrayOutputStream ();
      contents.write (0x03);
      contents.write (0x09);
      contents.write (ascii ("FT:DATA"), 0, 7);

      byte[] buffer = transferField (0x00, 0x12, contents.toByteArray ());

      FileTransferOutboundSF sf = new FileTransferOutboundSF (buffer, 0, buffer.length);

      assertTrue (sf.toString ().startsWith ("Struct Field : D0 File Transfer Outbound"),
                  sf.toString ());
      assertEquals (TransferType.DOWNLOAD, sf.transferType);
      assertEquals (TransferContents.DATA, sf.transferContents);
    }

    @Test
    @DisplayName ("um registro de tamanho marca a transferencia como upload")
    void outboundRecordSizeMeansUpload ()
    {
      byte[] buffer = transferField (0x00, 0x12,
                                     bytes (0x08, 0x06, 0x00, 0x50, 0x00, 0x3F));

      FileTransferOutboundSF sf = new FileTransferOutboundSF (buffer, 0, buffer.length);

      assertEquals (TransferType.UPLOAD, sf.transferType);
      assertTrue (sf.toString ().contains ("rec size  :"), sf.toString ());
    }

    @Test
    @DisplayName ("os registros de controle 01, 09, 0A e 50 sao lidos como genéricos")
    void outboundControlRecords ()
    {
      byte[] buffer = transferField (0x45, 0x00, bytes (0x01, 0x03, 0x00),
                                     bytes (0x09, 0x03, 0x00), bytes (0x0A, 0x03, 0x00),
                                     bytes (0x50, 0x03, 0x00));

      FileTransferOutboundSF sf = new FileTransferOutboundSF (buffer, 0, buffer.length);

      assertEquals (4, count (sf.toString (), "record    :"), sf.toString ());
    }

    @Test
    @DisplayName ("um campo de saida com dados carrega um DataRecord")
    void outboundDataRecord ()
    {
      ByteArrayOutputStream data = new ByteArrayOutputStream ();
      data.write (0xC0);
      data.write (0x80);
      data.write (0x61);
      data.write (0x00);
      data.write (0x08);
      data.write (ascii ("ABC"), 0, 3);

      byte[] buffer = transferField (0x47, 0x04, data.toByteArray ());

      FileTransferOutboundSF sf = new FileTransferOutboundSF (buffer, 0, buffer.length);

      assertTrue (sf.toString ().contains ("header    : C0 80 61 00 08"),
                  sf.toString ());
    }

    @Test
    @DisplayName ("um tipo diferente de D0 viola a assercao")
    void rejectsWrongType ()
    {
      byte[] buffer = bytes (0xD1, 0x00, 0x12);

      assertThrows (AssertionError.class,
                    () -> new FileTransferOutboundSF (buffer, 0, buffer.length));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("TransferManager.isIndfileCommand")
  class CommandRecognition
  // ---------------------------------------------------------------------------------//
  {
    @ParameterizedTest (name = "[{0}]")
    @ValueSource (strings = { "IND$FILE GET MEU.DATASET",
                              "IND$FILE PUT MEU.DATASET",
                              "TSO IND$FILE GET MEU.DATASET",
                              "IND£FILE GET MEU.DATASET",
                              "TSO   IND$FILE PUT X" })
    @DisplayName ("reconhece as formas validas do comando")
    void recognisesValidCommands (String command)
    {
      assertTrue (TransferManager.isIndfileCommand (command), command);
    }

    @ParameterizedTest (name = "[{0}]")
    @ValueSource (strings = { "IND$FILE", "IND$FILE DEL X", "LISTCAT",
                              "ind$file get x", "GET IND$FILE X" })
    @DisplayName ("recusa o que nao e um IND$FILE GET ou PUT")
    void rejectsOtherCommands (String command)
    {
      assertFalse (TransferManager.isIndfileCommand (command), command);
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
}
