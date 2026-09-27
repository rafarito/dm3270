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

/*
 * O "Not possible" do item 3 do backlog, provado.
 *
 * DefaultReportMaker devolve a literal "Not possible" nas duas sobrecargas de
 * getFormattedRecord. AsaReport e NatloadReport sobrescrevem a de dois argumentos mas nao a de
 * quatro, e o ReportScore.getSubrecord chama a de quatro em dois casos: quando a pagina tem um
 * registro so, e quando a pagina comeca ou termina no meio de um registro. Nos dois, o
 * relatorio mostra a literal no lugar do conteudo.
 *
 * O backlog registrava isso como "devem mostrar", sem caso que o provasse. Estes sao os casos,
 * e existem antes do ciclo C8 tornar os metodos da base abstract: o comportamento tem de
 * continuar o mesmo, so que declarado nas duas subclasses em vez de herdado em silencio.
 *
 * NAO E A EXPECTATIVA CERTA, E O COMPORTAMENTO DE HOJE. Corrigir e o item 3, e exige
 * autorizacao (Regra 1); quando vier, estes casos mudam no mesmo commit do fix.
 */
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

    /*
     * 65 registros de uma linha enchem 65 das 66 linhas da pagina; o seguinte, com '-', pede
     * tres. Com allowSplitRecords a pagina fica com o registro partido no fim (sobra uma
     * linha), e a pagina seguinte comeca por ele.
     */
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
    /*
     * Um registro de linha e depois um cabecalho de modulo (primeiro byte acima de 0x95,
     * sequencia 1 nos bytes 16 e 17): o cabecalho fecha a pagina anterior, que fica so com o
     * registro de linha.
     */
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
