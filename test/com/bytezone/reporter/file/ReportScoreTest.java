package com.bytezone.reporter.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormatSymbols;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.bytezone.dm3270.testing.JavaFxToolkit;
import com.bytezone.reporter.record.CrlfRecordMaker;
import com.bytezone.reporter.record.LfRecordMaker;
import com.bytezone.reporter.record.RecordMaker;
import com.bytezone.reporter.reports.HexReport;
import com.bytezone.reporter.reports.Page;
import com.bytezone.reporter.reports.ReportMaker;
import com.bytezone.reporter.reports.TextReport;
import com.bytezone.reporter.text.AsciiTextMaker;
import com.bytezone.reporter.text.TextMaker;

import javafx.scene.control.TextArea;

/*
 * Caracterizacao do ReportScore, que nao tinha teste nenhum.
 *
 * O ReportScore faz tres coisas ao mesmo tempo, e e por isso que ele e o alvo: guarda a
 * pontuacao de uma combinacao RecordMaker + TextMaker + ReportMaker, guarda as paginas em que
 * o relatorio foi dividido, e ainda possui os widgets JavaFX que mostram essas paginas - uma
 * Pagination, um TextArea e um bloco static que resolve a fonte.
 *
 * Estes testes congelam o comportamento atual antes de separar as tres. O que precisa
 * sobreviver, e que nao e obvio:
 *
 *   - getFormattedPage devolve SEMPRE A MESMA instancia de TextArea, mutada a cada chamada. A
 *     Pagination guarda esse Node como fabrica de pagina, entao devolver uma instancia nova
 *     mudaria o comportamento da janela;
 *   - pagina fora da faixa nao lanca: loga "impossible pageNumber requested" e devolve o
 *     TextArea VAZIO. O nome do logger e saida observavel (§5.13), entao esse aviso tem de
 *     continuar saindo da mesma classe;
 *   - getPagination cria preguicosamente, e a criacao TEM EFEITO: chama
 *     reportMaker.createPages (this), que e quem popula a lista de paginas;
 *   - getSubrecord tem quatro ramos, escolhidos pelos deslocamentos que a pagina carrega, e
 *     so e alcancado quando a pagina comeca ou termina no meio de um registro.
 *
 * O toString e afirmado por inteiro de proposito, incluindo o campo pagination no fim. Ele vai
 * mudar quando os widgets sairem, e o diff deste teste e que vai mostrar exatamente o que
 * mudou.
 */
// -----------------------------------------------------------------------------------//
@ExtendWith (JavaFxToolkit.class)
@DisplayName ("ReportScore - pontuacao, paginas e o widget que as mostra")
class ReportScoreTest
// -----------------------------------------------------------------------------------//
{
  private static final TextMaker ASCII = new AsciiTextMaker ();

  // ---------------------------------------------------------------------------------//
  //  Construcao
  // ---------------------------------------------------------------------------------//

  private static RecordMaker lines (String text)
  {
    LfRecordMaker recordMaker = new LfRecordMaker ();
    recordMaker.setBuffer (text.getBytes (StandardCharsets.ISO_8859_1));
    return recordMaker;
  }

  private static ReportScore score (String text, ReportMaker reportMaker)
  {
    return new ReportScore (lines (text), ASCII, reportMaker, 100.0, 1);
  }

  private static ReportScore text (String text)
  {
    return score (text, new TextReport (false, true));
  }

  private static String shown (ReportScore reportScore, int pageNumber)
  {
    return ((TextArea) reportScore.getFormattedPage (pageNumber)).getText ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("getPagination cria as paginas, e so na primeira chamada")
  class Paginating
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("a criacao dispara createPages, que popula a lista")
    void creationPopulatesThePages ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");

      assertTrue (reportScore.getPages ().isEmpty (), "antes, nao ha pagina nenhuma");

      assertNotNull (reportScore.getPagination ());

      assertEquals (1, reportScore.getPages ().size (), "tres registros cabem numa pagina");
      assertEquals (1, reportScore.getPagination ().getPageCount ());
    }

    @Test
    @DisplayName ("a segunda chamada devolve a mesma Pagination, sem repaginar")
    void secondCallIsCached ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");

      assertSame (reportScore.getPagination (), reportScore.getPagination ());
    }

    @Test
    @DisplayName ("acima de 66 registros o relatorio de texto quebra em mais de uma pagina")
    void splitsBeyondThePageSize ()
    {
      StringBuilder buffer = new StringBuilder ();
      for (int i = 0; i < 70; i++)
        buffer.append (String.format ("linha %02d%n", i).replace (System.lineSeparator (),
            "\n"));

      ReportScore reportScore = text (buffer.toString ());

      assertEquals (2, reportScore.getPagination ().getPageCount ());
      assertEquals (0, reportScore.getPages ().get (0).getFirstRecordIndex ());
      assertEquals (65, reportScore.getPages ().get (0).getLastRecordIndex ());
      assertEquals (66, reportScore.getPages ().get (1).getFirstRecordIndex ());
      assertEquals (69, reportScore.getPages ().get (1).getLastRecordIndex ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o texto que a pagina mostra")
  class PageText
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("os registros da pagina, um por linha, sem quebra sobrando no fim")
    void joinsTheRecords ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");
      reportScore.getPagination ();

      assertEquals ("AAA\nBBB\nCCC", shown (reportScore, 0));
    }

    @Test
    @DisplayName ("com newlineBetweenRecords o relatorio separa os registros por linha vazia")
    void blankLineBetweenRecords ()
    {
      ReportScore reportScore = score ("AAA\nBBB\nCCC\n", new TextReport (true, true));
      reportScore.getPagination ();

      assertEquals ("AAA\n\nBBB\n\nCCC", shown (reportScore, 0));
    }

    @Test
    @DisplayName ("devolve sempre a MESMA instancia de TextArea, mutada")
    void reusesTheSameWidget ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");
      reportScore.getPagination ();

      assertSame (reportScore.getFormattedPage (0), reportScore.getFormattedPage (0));
    }
  }

  /*
   * getFormattedText e a metade de dominio do antigo getFormattedPage. Enquanto o ReportScore
   * ainda constroi um TextArea no proprio construtor, estes testes precisam do toolkit como
   * todos os outros; quando os widgets sairem da classe, eles passam a rodar headless.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o texto da pagina, sem passar pelo widget")
  class PlainText
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("e exatamente o texto que o TextArea recebe")
    void matchesWhatTheWidgetShows ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");
      reportScore.getPagination ();

      assertEquals ("AAA\nBBB\nCCC", reportScore.getFormattedText (0));
      assertEquals (shown (reportScore, 0), reportScore.getFormattedText (0));
    }

    @Test
    @DisplayName ("pagina fora da faixa devolve string vazia, e nao lanca")
    void outOfRangeIsEmpty ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");
      reportScore.getPagination ();

      assertEquals ("", reportScore.getFormattedText (1));
      assertEquals ("", reportScore.getFormattedText (-1));
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("pagina fora da faixa")
  class OutOfRange
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("nao lanca: devolve o TextArea vazio")
    void returnsAnEmptyWidget ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");
      reportScore.getPagination ();

      assertEquals ("AAA\nBBB\nCCC", shown (reportScore, 0), "primeiro com conteudo");

      assertEquals ("", shown (reportScore, 1), "uma pagina alem do fim");
      assertEquals ("", shown (reportScore, -1), "e antes do inicio");
    }

    @Test
    @DisplayName ("o widget limpo e o mesmo de sempre, e nao um novo")
    void clearsTheSharedWidget ()
    {
      ReportScore reportScore = text ("AAA\nBBB\nCCC\n");
      reportScore.getPagination ();

      assertSame (reportScore.getFormattedPage (0), reportScore.getFormattedPage (99));
    }
  }

  /*
   * getSubrecord so e alcancado quando a pagina inteira cabe num registro so - firstRecord ==
   * lastRecord -, e ai os dois deslocamentos da pagina escolhem o ramo. Os quatro ramos abaixo
   * sao os quatro do metodo, na ordem em que ele os testa.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("um registro cortado pelos deslocamentos da pagina")
  class Subrecord
  // ---------------------------------------------------------------------------------//
  {
    private ReportScore single ()
    {
      ReportScore reportScore = text ("ABCDE\n");
      reportScore.getPagination ();
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
      ReportScore reportScore = single ();

      assertEquals ("ABCDE", shown (reportScore, 0));
    }

    @Test
    @DisplayName ("so o inicial: do deslocamento ate o fim do registro")
    void fromTheOffsetToTheEnd ()
    {
      ReportScore reportScore = single ();
      onlyPage (reportScore).setFirstRecordOffset (2);

      assertEquals ("CDE", shown (reportScore, 0));
    }

    @Test
    @DisplayName ("so o final: do inicio ate o deslocamento")
    void fromTheStartToTheOffset ()
    {
      ReportScore reportScore = single ();
      onlyPage (reportScore).setLastRecordOffset (3);

      assertEquals ("ABC", shown (reportScore, 0));
    }

    @Test
    @DisplayName ("os dois: o trecho entre eles")
    void betweenBothOffsets ()
    {
      ReportScore reportScore = single ();
      onlyPage (reportScore).setFirstRecordOffset (1);
      onlyPage (reportScore).setLastRecordOffset (4);

      assertEquals ("BCD", shown (reportScore, 0));
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
      return new ReportScore (recordMaker, ASCII, new TextReport (false, true), score, 10);
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
      ReportScore reportScore =
          new ReportScore (recordMaker, ASCII, reportMaker, 100.0, 1);

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
    /*
     * Afirmado por inteiro, com o campo pagination no fim. Enquanto ninguem chamar
     * getPagination (), esse campo e nulo - e a string diz "null". Quando os widgets sairem
     * do ReportScore, e este teste que vai mostrar o que mudou.
     *
     * O SEPARADOR DECIMAL VEM DO LOCALE, e foi este teste que descobriu: o toString usa
     * String.format com %6.2f e sem Locale explicito, entao imprime "95,50" numa maquina
     * pt-BR e "95.50" numa en-US. Nao e corrigido aqui, porque a Regra 1 preserva
     * comportamento - mas a expectativa e montada com o separador da plataforma, para que o
     * teste valha nas duas.
     */
    @Test
    @DisplayName ("os tres makers, a pontuacao, o tamanho da amostra, o peso e a paginacao")
    void formatsEveryField ()
    {
      ReportScore reportScore =
          new ReportScore (lines ("A\n"), ASCII, new TextReport (false, true), 95.5, 20);

      char dot = new DecimalFormatSymbols ().getDecimalSeparator ();

      assertEquals ("LF         ASCII      Text        95" + dot + "50  20  0" + dot
          + "90  null", reportScore.toString ());
    }
  }
}
