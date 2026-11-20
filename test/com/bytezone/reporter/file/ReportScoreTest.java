package com.bytezone.reporter.file;

import static com.bytezone.reporter.file.ReportScores.ASCII;
import static com.bytezone.reporter.file.ReportScores.lines;
import static com.bytezone.reporter.file.ReportScores.of;
import static com.bytezone.reporter.file.ReportScores.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.DecimalFormatSymbols;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bytezone.reporter.record.CrlfRecordMaker;
import com.bytezone.reporter.record.RecordMaker;
import com.bytezone.reporter.reports.HexReport;
import com.bytezone.reporter.reports.Page;
import com.bytezone.reporter.reports.ReportMaker;
import com.bytezone.reporter.reports.TextReport;
import com.bytezone.reporter.text.AsciiTextMaker;

// -----------------------------------------------------------------------------------//
@DisplayName ("ReportScore - a pontuacao e as paginas, sem widget nenhum")
class ReportScoreTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("createPages divide os registros")
  class Paginating
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("antes de chamar, nao ha pagina nenhuma")
    void nothingUntilAsked ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");

      assertTrue (reportScore.getPages ().isEmpty ());

      reportScore.createPages ();

      assertEquals (1, reportScore.getPages ().size (), "tres registros cabem numa pagina");
    }

    @Test
    @DisplayName ("acima de 66 registros o relatorio de texto quebra em mais de uma pagina")
    void splitsBeyondThePageSize ()
    {
      StringBuilder buffer = new StringBuilder ();
      for (int i = 0; i < 70; i++)
        buffer.append ("linha ").append (i).append ('\n');

      ReportScore reportScore = text (buffer.toString ());
      reportScore.createPages ();

      assertEquals (2, reportScore.getPages ().size ());
      assertEquals (0, reportScore.getPages ().get (0).getFirstRecordIndex ());
      assertEquals (65, reportScore.getPages ().get (0).getLastRecordIndex ());
      assertEquals (66, reportScore.getPages ().get (1).getFirstRecordIndex ());
      assertEquals (69, reportScore.getPages ().get (1).getLastRecordIndex ());
    }

    @Test
    @DisplayName ("chamar duas vezes nao duplica: createPages comeca limpando a lista")
    void callingTwiceIsHarmless ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");

      reportScore.createPages ();
      reportScore.createPages ();

      assertEquals (1, reportScore.getPages ().size ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o texto de uma pagina")
  class PageText
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("os registros da pagina, um por linha, sem quebra sobrando no fim")
    void joinsTheRecords ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");
      reportScore.createPages ();

      assertEquals ("AAA\nBBB\nCCC", reportScore.getFormattedText (0));
    }

    @Test
    @DisplayName ("com newlineBetweenRecords o relatorio separa os registros por linha vazia")
    void blankLineBetweenRecords ()
    {
      ReportScore reportScore = of ("AAA\nBBB\nCCC\n", new TextReport (true, true));
      reportScore.createPages ();

      assertEquals ("AAA\n\nBBB\n\nCCC", reportScore.getFormattedText (0));
    }

    @Test
    @DisplayName ("pagina fora da faixa devolve string vazia, e nao lanca")
    void outOfRangeIsEmpty ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");
      reportScore.createPages ();

      assertEquals ("", reportScore.getFormattedText (1), "uma pagina alem do fim");
      assertEquals ("", reportScore.getFormattedText (-1), "e antes do inicio");
    }
  }

  // getSubrecord so e alcancado com firstRecord == lastRecord; cobre os 4 ramos de offset.
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("um registro cortado pelos deslocamentos da pagina")
  class Subrecord
  // ---------------------------------------------------------------------------------//
  {
    private ReportScore single ()
    {
      ReportScore reportScore = text ("ABCDE\n");
      reportScore.createPages ();
      return reportScore;
    }

    private Page onlyPage (ReportScore reportScore)
    {
      return reportScore.getPages ().get (0);
    }

    @Test
    @DisplayName ("sem deslocamento nenhum, o registro inteiro")
    void wholeRecord ()
    {
      assertEquals ("ABCDE", single ().getFormattedText (0));
    }

    @Test
    @DisplayName ("so o inicial: do deslocamento ate o fim do registro")
    void fromTheOffsetToTheEnd ()
    {
      ReportScore reportScore = single ();
      onlyPage (reportScore).setFirstRecordOffset (2);

      assertEquals ("CDE", reportScore.getFormattedText (0));
    }

    @Test
    @DisplayName ("so o final: do inicio ate o deslocamento")
    void fromTheStartToTheOffset ()
    {
      ReportScore reportScore = single ();
      onlyPage (reportScore).setLastRecordOffset (3);

      assertEquals ("ABC", reportScore.getFormattedText (0));
    }

    @Test
    @DisplayName ("os dois: o trecho entre eles")
    void betweenBothOffsets ()
    {
      ReportScore reportScore = single ();
      onlyPage (reportScore).setFirstRecordOffset (1);
      onlyPage (reportScore).setLastRecordOffset (4);

      assertEquals ("BCD", reportScore.getFormattedText (0));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("addPage")
  class AddingPages
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("acrescenta a lista e devolve a pagina criada")
    void appendsAndReturns ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");

      Page page = reportScore.addPage (0, 1);

      assertEquals (1, reportScore.getPages ().size ());
      assertSame (page, reportScore.getPages ().get (0));
      assertEquals (0, page.getFirstRecordIndex ());
      assertEquals (1, page.getLastRecordIndex ());
    }

    @Test
    @DisplayName ("a pagina seguinte comeca onde a anterior terminou")
    void chainsTheOffsets ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");

      Page first = reportScore.addPage (0, 0);
      first.setLastRecordOffset (2);

      Page second = reportScore.addPage (1, 2);

      assertEquals (2, second.getFirstRecordOffset (),
          "o deslocamento inicial vem do final da anterior");
      assertEquals (0, first.getFirstRecordOffset (), "a primeira nao herda de ninguem");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("a pontuacao")
  class Scoring
  // ---------------------------------------------------------------------------------//
  {
    private ReportScore with (double score, RecordMaker recordMaker)
    {
      return of (recordMaker, new TextReport (false, true), score, 10);
    }

    @Test
    @DisplayName ("100 e pontuacao perfeita, e nada abaixo disso e")
    void perfectIsExactlyOneHundred ()
    {
      assertTrue (with (100.0, lines ("A\n")).isPerfectScore ());
      assertFalse (with (99.99, lines ("A\n")).isPerfectScore ());
    }

    @Test
    @DisplayName ("compara pela pontuacao")
    void ordersByScore ()
    {
      ReportScore lower = with (50.0, lines ("A\n"));
      ReportScore higher = with (80.0, lines ("A\n"));

      assertTrue (lower.compareTo (higher) < 0);
      assertTrue (higher.compareTo (lower) > 0);
    }

    @Test
    @DisplayName ("empatada a pontuacao, desempata pelo peso dos makers")
    void tiesAreBrokenByWeight ()
    {
      // LF pesa 0.9 e CRLF pesa 0.95; o TextReport pesa 1.0 nos dois
      ReportScore lf = with (100.0, lines ("A\n"));
      ReportScore crlf = with (100.0, new CrlfRecordMaker ());

      assertTrue (lf.compareTo (crlf) < 0, "o maker mais pesado ganha o desempate");
      assertTrue (crlf.compareTo (lf) > 0);
    }

    @Test
    @DisplayName ("matches exige os tres makers, por identidade")
    void matchesAllThree ()
    {
      RecordMaker recordMaker = lines ("A\n");
      ReportMaker reportMaker = new TextReport (false, true);
      ReportScore reportScore = of (recordMaker, reportMaker, 100.0, 1);

      assertTrue (reportScore.matches (recordMaker, ASCII, reportMaker));
      assertFalse (reportScore.matches (new CrlfRecordMaker (), ASCII, reportMaker));
      assertFalse (reportScore.matches (recordMaker, new AsciiTextMaker (), reportMaker));
      assertFalse (reportScore.matches (recordMaker, ASCII, new HexReport (true, true)));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("toString")
  class Text_
  // ---------------------------------------------------------------------------------//
  {
    // toString() usa String.format sem Locale; a expectativa acompanha o separador do SO.
    @Test
    @DisplayName ("os tres makers, a pontuacao, o tamanho da amostra e o peso")
    void formatsEveryField ()
    {
      ReportScore reportScore = of (lines ("A\n"), new TextReport (false, true), 95.5, 20);

      char dot = new DecimalFormatSymbols ().getDecimalSeparator ();

      assertEquals ("LF         ASCII      Text        95" + dot + "50  20  0" + dot + "90",
          reportScore.toString ());
    }
  }
}
