package com.bytezone.reporter.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bytezone.reporter.record.FbRecordMaker;
import com.bytezone.reporter.record.LfRecordMaker;
import com.bytezone.reporter.record.Record;
import com.bytezone.reporter.record.SingleRecordMaker;
import com.bytezone.reporter.reports.HexReport;
import com.bytezone.reporter.reports.TextReport;
import com.bytezone.reporter.text.AsciiTextMaker;
import com.bytezone.reporter.text.EbcdicTextMaker;
import com.bytezone.reporter.text.TextMaker;

// -----------------------------------------------------------------------------------//
@DisplayName ("reporter.file - pontuacao dos formatos candidatos")
class ReportTesterTest
// -----------------------------------------------------------------------------------//
{
  private static final TextMaker ASCII = new AsciiTextMaker ();
  private static final TextMaker EBCDIC = new EbcdicTextMaker ();

  private static byte[] ascii (String text)
  {
    return text.getBytes (java.nio.charset.StandardCharsets.ISO_8859_1);
  }

  private static byte[] ebcdic (String text)
  {
    byte[] buffer = new byte[text.length ()];
    for (int i = 0; i < text.length (); i++)
      buffer[i] = (byte) EbcdicTextMaker.asc2ebc[text.charAt (i)];

    return buffer;
  }

  private static List<Record> records (byte[] buffer, int recordLength)
  {
    List<Record> records = new ArrayList<> ();
    for (int i = 0; i + recordLength <= buffer.length; i += recordLength)
      records.add (new Record (buffer, i, recordLength, records.size ()));

    return records;
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("TextTester - qual codificacao rende mais letras")
  class TextTesting
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("texto ASCII maiusculo pontua 100% no leitor ASCII")
    void asciiTextScoresFull ()
    {
      TextTester tester = new TextTester (ASCII);

      tester.testRecords (records (ascii ("ABCDEFGH"), 4));

      assertEquals (100.0, tester.getAlphanumericRatio ());
    }

    @Test
    @DisplayName ("o mesmo texto pontua zero no leitor EBCDIC")
    void asciiTextScoresZeroInEbcdic ()
    {
      TextTester tester = new TextTester (EBCDIC);

      tester.testRecords (records (ascii ("ABCDEFGH"), 4));

      assertEquals (0.0, tester.getAlphanumericRatio ());
    }

    @Test
    @DisplayName ("digitos nao contam como alfanumericos para o ASCII")
    void digitsDoNotCountInAscii ()
    {
      // countAlphanumericBytes do ASCII aceita apenas espaco e A-Z
      TextTester tester = new TextTester (ASCII);

      tester.testRecords (records (ascii ("AB12"), 4));

      assertEquals (50.0, tester.getAlphanumericRatio ());
    }

    @Test
    @DisplayName ("espacos contam como alfanumericos nas duas codificacoes")
    void spacesCount ()
    {
      TextTester ascii = new TextTester (ASCII);
      ascii.testRecords (records (ascii ("A   "), 4));

      TextTester ebcdic = new TextTester (EBCDIC);
      ebcdic.testRecords (records (ebcdic ("A   "), 4));

      assertEquals (100.0, ascii.getAlphanumericRatio ());
      assertEquals (100.0, ebcdic.getAlphanumericRatio ());
    }

    @Test
    @DisplayName ("varias chamadas acumulam no mesmo testador")
    void accumulatesAcrossCalls ()
    {
      TextTester tester = new TextTester (ASCII);

      tester.testRecords (records (ascii ("ABCD"), 4));      // 4 de 4
      tester.testRecords (records (ascii ("\1\2\3\4"), 4));  // 0 de 4

      assertEquals (50.0, tester.getAlphanumericRatio ());
    }

    @Test
    @DisplayName ("sem registros a razao e NaN, nao zero")
    void noRecordsGivesNaN ()
    {
      // 0/0 em ponto flutuante: quem consome precisa saber disso
      TextTester tester = new TextTester (ASCII);

      tester.testRecords (new ArrayList<> ());

      assertTrue (Double.isNaN (tester.getAlphanumericRatio ()));
    }

    @Test
    @DisplayName ("guarda e expoe o leitor recebido")
    void keepsTextMaker ()
    {
      TextTester tester = new TextTester (EBCDIC);

      assertSame (EBCDIC, tester.getTextMaker ());
    }

    @Test
    @DisplayName ("toString junta codificacao e percentual")
    void describesItself ()
    {
      TextTester tester = new TextTester (ASCII);
      tester.testRecords (records (ascii ("ABCD"), 4));

      assertEquals (String.format ("%-6.6s %6.2f", "ASCII", 100.0),
                    tester.toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ReportTester - quantos registros o formato aceita")
  class ReportTesting
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o hex aceita todos os registros")
    void hexAcceptsEverything ()
    {
      ReportTester tester = new ReportTester (new HexReport (true, true), ASCII);

      tester.testRecords (records (new byte[16], 4));

      assertEquals (100.0, tester.getRatio ());
    }

    @Test
    @DisplayName ("o texto recusa registros binarios")
    void textRejectsBinary ()
    {
      ReportTester tester = new ReportTester (new TextReport (false, false), ASCII);

      tester.testRecords (records (new byte[16], 4));       // bytes nulos

      assertEquals (0.0, tester.getRatio ());
    }

    @Test
    @DisplayName ("a razao mistura registros aceitos e recusados")
    void partialRatio ()
    {
      byte[] buffer = new byte[8];
      System.arraycopy (ascii ("ABCD"), 0, buffer, 0, 4);   // primeiro registro legivel

      ReportTester tester = new ReportTester (new TextReport (false, false), ASCII);
      tester.testRecords (records (buffer, 4));

      assertEquals (50.0, tester.getRatio ());
    }

    @Test
    @DisplayName ("sem registros a razao e NaN")
    void noRecordsGivesNaN ()
    {
      ReportTester tester = new ReportTester (new HexReport (true, true), ASCII);

      tester.testRecords (new ArrayList<> ());

      assertTrue (Double.isNaN (tester.getRatio ()));
    }

    @Test
    @DisplayName ("toString junta formato e percentual")
    void describesItself ()
    {
      ReportTester tester = new ReportTester (new HexReport (true, true), ASCII);
      tester.testRecords (records (new byte[4], 4));

      assertEquals (String.format ("%-6.6s %6.2f", "HEX", 100.0),
                    tester.toString ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("RecordTester - escolhe a codificacao de um formato de registro")
  class RecordTesting
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("conta os registros da amostra")
    void countsSampleRecords ()
    {
      FbRecordMaker recordMaker = new FbRecordMaker (4);
      byte[] buffer = new byte[40];
      java.util.Arrays.fill (buffer, (byte) 0x40);    // ver allNullRecordBreaksSplit

      recordMaker.setBuffer (buffer);

      assertEquals (10, new RecordTester (recordMaker, 40).countSampleRecords ());
      assertEquals (5, new RecordTester (recordMaker, 20).countSampleRecords ());
    }

    @Test
    @DisplayName ("um dataset EBCDIC faz o testador preferir o leitor EBCDIC")
    void prefersEbcdicForEbcdicData ()
    {
      FbRecordMaker recordMaker = new FbRecordMaker (8);
      recordMaker.setBuffer (ebcdic ("MAINFRAMEDATASETX"));

      RecordTester tester = new RecordTester (recordMaker, 16);
      tester.testTextMaker (ASCII);
      tester.testTextMaker (EBCDIC);

      assertSame (EBCDIC, tester.getPreferredTextMaker ());
    }

    @Test
    @DisplayName ("um dataset ASCII faz o testador preferir o leitor ASCII")
    void prefersAsciiForAsciiData ()
    {
      LfRecordMaker recordMaker = new LfRecordMaker ();
      recordMaker.setBuffer (ascii ("PRIMEIRA LINHA\nSEGUNDA LINHA\n"));

      RecordTester tester = new RecordTester (recordMaker, 28);
      tester.testTextMaker (ASCII);
      tester.testTextMaker (EBCDIC);

      assertSame (ASCII, tester.getPreferredTextMaker ());
    }

    @Test
    @DisplayName ("no empate o primeiro leitor testado ganha")
    void firstWinsOnTie ()
    {
      // dados binarios: nenhuma das codificacoes acha alfanumericos
      FbRecordMaker recordMaker = new FbRecordMaker (4);
      recordMaker.setBuffer (new byte[] { 0x01, 0x02, 0x03, 0x04 });

      RecordTester tester = new RecordTester (recordMaker, 4);
      tester.testTextMaker (EBCDIC);
      tester.testTextMaker (ASCII);

      assertSame (EBCDIC, tester.getPreferredTextMaker ());
    }

    @Test
    @DisplayName ("sem nenhum leitor testado a escolha e recusada com mensagem clara")
    void needsAtLeastOneTextMaker ()
    {
      FbRecordMaker recordMaker = new FbRecordMaker (4);
      recordMaker.setBuffer (new byte[] { 0x40, 0x40, 0x40, 0x40 });

      RecordTester tester = new RecordTester (recordMaker, 4);

      IllegalStateException e =
          assertThrows (IllegalStateException.class, tester::getPreferredTextMaker);

      assertTrue (e.getMessage ().contains ("testTextMaker"), e.getMessage ());
    }

    @Test
    @DisplayName ("uma amostra vazia deixa a razao NaN e o primeiro leitor prevalece")
    void emptySampleFallsBackToTheFirst ()
    {
      // sem registros a razao e NaN em todos os candidatos: nao ha o que comparar, e o
      // metodo devolve o primeiro leitor testado em vez de percorrer a lista em vao
      FbRecordMaker recordMaker = new FbRecordMaker (8);
      recordMaker.setBuffer (new byte[4]);            // menor que um registro

      RecordTester tester = new RecordTester (recordMaker, 4);
      tester.testTextMaker (ASCII);
      tester.testTextMaker (EBCDIC);

      assertEquals (0, tester.countSampleRecords ());
      assertSame (ASCII, tester.getPreferredTextMaker ());
    }

    @Test
    @DisplayName ("um candidato com amostra vazia nao rouba a escolha de um valido")
    void naNDoesNotBeatARealRatio ()
    {
      // o leitor de amostra vazia e ignorado na comparacao, e nao tratado como zero
      FbRecordMaker recordMaker = new FbRecordMaker (8);
      recordMaker.setBuffer (ebcdic ("MAINFRAMEDATASETX"));

      RecordTester tester = new RecordTester (recordMaker, 16);
      tester.testTextMaker (ASCII);       // 0% de alfanumericos
      tester.testTextMaker (EBCDIC);      // 100%

      assertSame (EBCDIC, tester.getPreferredTextMaker ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("ReportData - catalogo de formatos candidatos")
  class Catalogue
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("o construtor monta doze formatos de registro")
    void listsRecordMakers ()
    {
      assertEquals (12, new ReportData ().getRecordMakers ().size ());
    }

    @Test
    @DisplayName ("as duas codificacoes e os quatro relatorios estao disponiveis")
    void listsTextAndReportMakers ()
    {
      ReportData reportData = new ReportData ();

      assertEquals (2, reportData.getTextMakers ().size ());
      assertEquals (4, reportData.getReportMakers ().size ());
    }

    @Test
    @DisplayName ("os quatro tamanhos de registro fixo estao cobertos")
    void coversFixedBlockSizes ()
    {
      List<Integer> lengths = new ArrayList<> ();
      for (var recordMaker : new ReportData ().getRecordMakers ())
        if (recordMaker instanceof FbRecordMaker fb)
          lengths.add (fb.getRecordLength ());

      assertEquals (Arrays.asList (63, 80, 132, 252), lengths);
    }

    @Test
    @DisplayName ("um catalogo recem-criado nao tem dados nem pontuacoes")
    void startsEmpty ()
    {
      ReportData reportData = new ReportData ();

      assertFalse (reportData.hasData ());
      assertFalse (reportData.hasScores ());
      assertNull (reportData.getBuffer ());
    }

    @Test
    @DisplayName ("o construtor de transferencia ja recebe o conteudo")
    void acceptsBufferUpFront ()
    {
      byte[] buffer = ascii ("conteudo baixado");

      ReportData reportData = new ReportData (buffer);

      assertTrue (reportData.hasData ());
      assertSame (buffer, reportData.getBuffer ());
    }

    @Test
    @DisplayName ("fillBuffer le o arquivo do disco")
    void readsFileFromDisk (@TempDir File directory) throws IOException
    {
      File file = new File (directory, "dataset.txt");
      Files.write (file.toPath (), ascii ("PRIMEIRA LINHA\n"));

      ReportData reportData = new ReportData ();
      reportData.fillBuffer (file);

      assertTrue (reportData.hasData ());
      assertEquals (15, reportData.getBuffer ().length);
    }

    @Test
    @DisplayName ("um arquivo inexistente resulta em buffer vazio, nao em excecao")
    void missingFileGivesEmptyBuffer (@TempDir File directory)
    {
      ReportData reportData = new ReportData ();

      reportData.fillBuffer (new File (directory, "nao-existe.txt"));

      assertTrue (reportData.hasData ());
      assertEquals (0, reportData.getBuffer ().length);
    }

    @Test
    @DisplayName ("fillBuffer duas vezes viola a assercao do metodo")
    void refusesToOverwriteBuffer (@TempDir File directory) throws IOException
    {
      File file = new File (directory, "dataset.txt");
      Files.write (file.toPath (), ascii ("LINHA\n"));

      ReportData reportData = new ReportData ();
      reportData.fillBuffer (file);

      assertThrows (AssertionError.class, () -> reportData.fillBuffer (file));
    }

    @Test
    @DisplayName ("sem pontuacoes a lista de formatos perfeitos e vazia")
    void noPerfectScores ()
    {
      assertTrue (new ReportData ().getPerfectScores ().isEmpty ());
    }

    @Test
    @DisplayName ("sem selecao a codificacao nao e considerada ASCII")
    void isAsciiNeedsASelection ()
    {
      assertFalse (new ReportData ().isAscii ());
    }

    @Test
    @DisplayName ("setReportScore num catalogo vazio nao encontra nada")
    void setReportScoreWithoutScores ()
    {
      ReportData reportData = new ReportData ();

      assertNull (reportData.setReportScore (new SingleRecordMaker (), ASCII,
                                             new HexReport (true, true)));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("MainframeFile")
  class Files_
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("descreve nome e tamanho de um conteudo em memoria")
    void describesBuffer ()
    {
      MainframeFile file = new MainframeFile ("DATASET.TXT", new byte[1234]);

      String report = file.toString ();

      assertTrue (report.contains ("File name ....... DATASET.TXT"), report);
      assertTrue (report.contains (String.format ("Size ............ %,d", 1234)),
                  report);
    }

    @Test
    @DisplayName ("um arquivo em disco tem o nome e o tamanho lidos do sistema")
    void describesFileOnDisk (@TempDir File directory) throws IOException
    {
      File onDisk = new File (directory, "MEMBRO.SRC");
      java.nio.file.Files.write (onDisk.toPath (), new byte[42]);

      String report = new MainframeFile (onDisk).toString ();

      assertTrue (report.contains ("File name ....... MEMBRO.SRC"), report);
      assertTrue (report.contains ("Size ............ 42"), report);
    }

    @Test
    @DisplayName ("um buffer vazio tem tamanho zero")
    void emptyBuffer ()
    {
      assertTrue (new MainframeFile ("VAZIO", new byte[0]).toString ()
          .contains ("Size ............ 0"));
    }
  }
}
