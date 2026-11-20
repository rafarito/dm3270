package com.bytezone.reporter.file;

import static com.bytezone.reporter.file.ReportScores.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bytezone.reporter.reports.AsaReport;
import com.bytezone.reporter.reports.NatloadReport;
import com.bytezone.reporter.reports.Page;

// Caracteriza bug conhecido: AsaReport e NatloadReport herdam "Not possible" da sobrecarga
// de 4 argumentos de getFormattedRecord, chamada em paginas de 1 registro ou partido.
// -----------------------------------------------------------------------------------//
@DisplayName ("registro partido - o \"Not possible\" do ASA e do Natload")
class PartialRecordTest
// -----------------------------------------------------------------------------------//
{
  private static final String NOT_POSSIBLE = "Not possible";

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("AsaReport")
  class Asa
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("pagina de um registro so: a literal no lugar do registro")
    void singleRecordPage ()
    {
      ReportScore reportScore = of ("1HELLO\n", new AsaReport (false, true));
      reportScore.createPages ();

      assertEquals (1, reportScore.getPages ().size ());
      assertEquals (NOT_POSSIBLE, reportScore.getFormattedText (0));
    }

    // 65 linhas enchem 65/66 da pagina; '-' pede 3 linhas e forca a quebra entre paginas.
    @Test
    @DisplayName ("registro partido entre duas paginas: a literal no fim de uma e no inicio da outra")
    void splitRecord ()
    {
      StringBuilder text = new StringBuilder ();
      for (int i = 0; i < 65; i++)
        text.append (" linha ").append (i).append ('\n');
      text.append ("-partido\n");
      text.append (" depois\n");

      ReportScore reportScore = of (text.toString (), new AsaReport (false, true));
      reportScore.createPages ();

      List<Page> pages = reportScore.getPages ();
      assertEquals (2, pages.size ());
      assertEquals (65, pages.get (0).getLastRecordIndex ());
      assertEquals (1, pages.get (0).getLastRecordOffset ());
      assertEquals (65, pages.get (1).getFirstRecordIndex ());
      assertEquals (1, pages.get (1).getFirstRecordOffset ());

      String[] first = reportScore.getFormattedText (0).split ("\n");
      assertEquals ("linha 64", first[first.length - 2]);
      assertEquals (NOT_POSSIBLE, first[first.length - 1]);

      assertEquals (NOT_POSSIBLE + "\ndepois", reportScore.getFormattedText (1));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("NatloadReport")
  class Natload
  // ---------------------------------------------------------------------------------//
  {
    // Cabecalho Natload (1o byte > 0x95, seq 1 em [16..17]) fecha a pagina com 1 registro.
    @Test
    @DisplayName ("pagina de um registro so: a literal no lugar do registro")
    void singleRecordPage ()
    {
      String line = "\u0001\u0002ABC\n";
      String header = " LIBRARY PROGRAM\u0000\u0001010\n";
      String next = "\u0001\u0003XYZ\n";

      ReportScore reportScore = of (line + header + next, new NatloadReport (false, true));
      reportScore.createPages ();

      List<Page> pages = reportScore.getPages ();
      assertEquals (2, pages.size ());
      assertEquals (0, pages.get (0).getFirstRecordIndex ());
      assertEquals (0, pages.get (0).getLastRecordIndex ());

      assertEquals (NOT_POSSIBLE, reportScore.getFormattedText (0));
    }
  }
}
