package com.bytezone.dm3270.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bytezone.dm3270.assistant.TableDataset;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.orders.BufferAddress;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.utilities.Dm3270Utility;

/*
 * Caracterizacao dos layouts de lista de dataset do ScreenWatcher.
 *
 * ScreenWatcher tem 1.002 linhas e nao tinha teste nenhum. checkDatasetList classifica a tela
 * num screenType de 1 a 5 com um switch sobre a QUANTIDADE de campos de uma linha, e addDataset
 * tem um segundo switch que repete a numeracao e le os dados por deslocamento de coluna
 * cravado no codigo - 6, 11, 15 num ramo, 5, 11, 18 noutro, 6, 10, 14 noutro. Suportar um
 * layout novo obriga a editar os dois lugares.
 *
 * A onda 3 vai trocar isso por uma Strategy, uma classe por layout. Estes testes existem para
 * que a troca seja verificavel: cada um monta uma tela do ISPF como fluxo 3270 de verdade -
 * SBA, Start Field, atributo e texto em EBCDIC - deixa o parser processa-la, e confere o que o
 * ScreenWatcher extraiu. Se um layout deixar de ser reconhecido, ou um deslocamento de coluna
 * mudar, o teste correspondente quebra.
 *
 * As telas nao sao capturas reais: sao o minimo que satisfaz cada caminho de reconhecimento,
 * montado a partir da leitura do codigo. O que elas congelam e a decisao - qual screenType a
 * tela recebe - e o mapeamento de colunas para campos do dataset.
 */
// -----------------------------------------------------------------------------------//
@DisplayName ("ScreenWatcher - layouts de lista de dataset")
class ScreenWatcherTest
// -----------------------------------------------------------------------------------//
{
  private static final byte ERASE_WRITE = 0x05;
  private static final byte SBA = 0x11;
  private static final byte SF = 0x1D;
  private static final byte WCC_RESET_KEYBOARD = (byte) 0xC2;

  private static final int PROTECTED = 0x20;
  private static final int UNPROTECTED = 0x00;

  private static final int ROWS = 24;
  private static final int COLUMNS = 80;

  // A linha de comando do ISPF. O texto tem de casar exatamente, e o campo de entrada
  // seguinte tem de ter 66 ou 48 posicoes - e o que hasPromptField exige.
  private static final String COMMAND_PROMPT = "Command ===>";

  private HeadlessScreenTarget screen;
  private ScreenWatcher watcher;
  private final List<Integer> stream = new ArrayList<> ();

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void setUp ()
  // ---------------------------------------------------------------------------------//
  {
    screen = new HeadlessScreenTarget (new ScreenDimensions (ROWS, COLUMNS),
        new ScreenDimensions (43, COLUMNS));
    watcher = screen.getScreenWatcher ();
    stream.clear ();
    stream.add ((int) ERASE_WRITE);
    stream.add ((int) WCC_RESET_KEYBOARD);
  }

  /*
   * Um campo: o atributo ocupa (row, col) e os dados comecam na coluna seguinte. O campo se
   * estende ate o proximo atributo, entao o comprimento de cada um e decidido por onde o
   * proximo comeca - e por isso que os testes posicionam os campos por coluna explicita.
   */
  // ---------------------------------------------------------------------------------//
  private void field (int row, int col, int attribute, String text)
  // ---------------------------------------------------------------------------------//
  {
    int position = row * COLUMNS + col;
    stream.add ((int) SBA);
    stream.add (BufferAddress.address[(position >> 6) & 0x3F] & 0xFF);
    stream.add (BufferAddress.address[position & 0x3F] & 0xFF);
    stream.add ((int) SF);
    stream.add (attribute);
    for (char ch : text.toCharArray ())
      stream.add (Dm3270Utility.asc2ebc[ch]);
  }

  // ---------------------------------------------------------------------------------//
  private void send ()
  // ---------------------------------------------------------------------------------//
  {
    byte[] buffer = new byte[stream.size ()];
    for (int i = 0; i < buffer.length; i++)
      buffer[i] = (byte) (int) stream.get (i);
    Command.getCommand (buffer, 0, buffer.length).process (screen);
  }

  /*
   * O cabecalho comum a todas as telas DSLIST:
   *
   *   linha 0   a barra de menus, so para haver campo
   *   linha 1   "Command ===>" com exatamente 12 posicoes, seguido do campo de entrada, que
   *             fica com 66 porque o proximo atributo esta na linha 2 coluna 0
   *   linha 2   o titulo, de onde sai o volume ou o padrao de nomes
   *   linhas 3-4 preenchimento, para o total de campos passar de 21
   */
  // ---------------------------------------------------------------------------------//
  private void header (String title)
  // ---------------------------------------------------------------------------------//
  {
    field (0, 0, PROTECTED, "  Menu  Options  View  Utilities  Compilers  Help");
    field (1, 0, PROTECTED, COMMAND_PROMPT);
    field (1, 13, UNPROTECTED, "");
    field (2, 0, PROTECTED, title);
    field (3, 0, PROTECTED, "");
    field (4, 0, PROTECTED, "");
  }

  /*
   * A linha 5 e a que decide o screenType, pela quantidade de campos e pelos titulos. Os
   * campos entram lado a lado a partir da coluna zero, cada um consumindo o texto mais a
   * coluna do proprio atributo.
   */
  // ---------------------------------------------------------------------------------//
  private void headingRow (String... texts)
  // ---------------------------------------------------------------------------------//
  {
    int col = 0;
    for (String text : texts)
    {
      field (5, col, PROTECTED, text);
      col += text.length () + 1;
    }
  }

  /*
   * Uma linha de dataset. O primeiro campo carrega nove posicoes de area de comando e depois
   * o nome; o resto sao os campos de detalhe, um por coluna informada.
   */
  // ---------------------------------------------------------------------------------//
  private void datasetRow (int row, String name, String... details)
  // ---------------------------------------------------------------------------------//
  {
    field (row, 0, UNPROTECTED, "         " + name);
    int col = 45;
    for (String detail : details)
    {
      field (row, col, PROTECTED, detail);
      col += detail.length () + 1;
    }
  }

  /*
   * Fecha a lista e completa a contagem de campos.
   *
   * Uma linha com um unico campo faz o laco de leitura parar - ele desiste quando a linha tem
   * um campo ou menos. E as linhas seguintes existem por outro motivo: checkDatasetList
   * desiste de saida se a tela tiver menos de 21 campos, e o cabecalho mais uma linha de
   * dataset dao doze. Cada linha restante contribui com um.
   */
  // ---------------------------------------------------------------------------------//
  private void endOfList (int row)
  // ---------------------------------------------------------------------------------//
  {
    for (int r = row; r < ROWS - 1; r++)
      field (r, 0, PROTECTED, "");
  }

  // ---------------------------------------------------------------------------------//
  private TableDataset onlyDataset ()
  // ---------------------------------------------------------------------------------//
  {
    List<TableDataset> datasets = watcher.getDatasets ();
    assertEquals (1, datasets.size (), "esperava um dataset, veio " + datasets.size ());
    return datasets.get (0);
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("reconhecimento da tela")
  class Recognition
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("uma tela DSLIST por volume e reconhecida como lista de dataset")
    void volumeListIsRecognised ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Tracks %Used XT Device", "");
      datasetRow (7, "MY.DATA.SET", "   150   75   3 3390");
      endOfList (8);
      send ();

      assertTrue (watcher.getDatasets ().size () == 1, "nao reconheceu a lista");
    }

    @Test
    @DisplayName ("uma tela DSLIST por padrao de nome tambem e reconhecida")
    void matchingListIsRecognised ()
    {
      header ("DSLIST - Data Sets Matching SYS1.*");
      headingRow ("Command - Enter \"/\" to select action", "Tracks %Used XT Device", "");
      datasetRow (7, "SYS1.MACLIB", "   900   99   1 3390");
      endOfList (8);
      send ();

      assertEquals ("SYS1.MACLIB", onlyDataset ().getDatasetName ());
    }

    @Test
    @DisplayName ("um titulo que nao e nem volume nem padrao nao produz dataset")
    void unknownTitleIsRejected ()
    {
      header ("DSLIST - Data Sets Matched in list REFLIST");
      headingRow ("Command - Enter \"/\" to select action", "Tracks %Used XT Device", "");
      datasetRow (7, "MY.DATA.SET", "   150   75   3 3390");
      endOfList (8);
      send ();

      assertTrue (watcher.getDatasets ().isEmpty (),
          "locationText nao casa com nenhum dos dois prefixos esperados");
    }

    @Test
    @DisplayName ("sem a linha de comando do ISPF, a tela nem chega a ser examinada")
    void withoutThePromptFieldNothingIsParsed ()
    {
      field (0, 0, PROTECTED, "  Menu  Options");
      field (1, 0, PROTECTED, "Nao e o prompt");
      field (2, 0, PROTECTED, "DSLIST - Data Sets on volume PRD001");
      field (3, 0, PROTECTED, "");
      field (4, 0, PROTECTED, "");
      headingRow ("Command - Enter \"/\" to select action", "Tracks %Used XT Device", "");
      datasetRow (7, "MY.DATA.SET", "   150   75   3 3390");
      endOfList (8);
      send ();

      assertTrue (watcher.getDatasets ().isEmpty ());
    }

    @Test
    @DisplayName ("um titulo de coluna desconhecido deixa a tela sem screenType")
    void unknownHeadingLeavesNoScreenType ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Coluna Inventada", "");
      datasetRow (7, "MY.DATA.SET", "   150   75   3 3390");
      endOfList (8);
      send ();

      assertTrue (watcher.getDatasets ().isEmpty (),
          "tres campos na linha 5, mas o titulo nao e Tracks nem Dsorg");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("screenType 1 - Tracks, deslocamentos 6/11/15")
  class TracksLayout
  // ---------------------------------------------------------------------------------//
  {
    @BeforeEach
    void tracksScreen ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Tracks %Used XT Device", "");
      //            0     6    11  15
      datasetRow (7, "MY.DATA.SET", "   150   75   3 3390");
      endOfList (8);
      send ();
    }

    @Test
    @DisplayName ("o nome sai da linha depois das nove posicoes de area de comando")
    void nameComesAfterTheCommandArea ()
    {
      assertEquals ("MY.DATA.SET", onlyDataset ().getDatasetName ());
    }

    @Test
    @DisplayName ("as colunas 0 a 6 sao as trilhas")
    void tracksComeFromTheFirstSixColumns ()
    {
      assertEquals (150, onlyDataset ().getTracks ());
    }

    @Test
    @DisplayName ("as colunas 6 a 11 sao o percentual usado")
    void percentUsedComesFromColumnsSixToEleven ()
    {
      assertEquals (75, onlyDataset ().getPercentUsed ());
    }

    @Test
    @DisplayName ("as colunas 11 a 15 sao os extents")
    void extentsComeFromColumnsElevenToFifteen ()
    {
      assertEquals (3, onlyDataset ().getExtents ());
    }

    @Test
    @DisplayName ("o que sobra depois da coluna 15 e o dispositivo")
    void deviceIsWhateverFollowsColumnFifteen ()
    {
      assertEquals ("3390", onlyDataset ().getDevice ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("screenType 2 - Dsorg, deslocamentos 5/11/18")
  class DsorgLayout
  // ---------------------------------------------------------------------------------//
  {
    @BeforeEach
    void dsorgScreen ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Dsorg Recfm Lrecl Blksz", "");
      //            0    5     11     18
      datasetRow (7, "MY.LOAD.LIB", "PO   FB         80  27920");
      endOfList (8);
      send ();
    }

    @Test
    @DisplayName ("as colunas 0 a 5 sao o dsorg")
    void dsorgComesFromTheFirstFiveColumns ()
    {
      assertEquals ("PO", onlyDataset ().getDsorg ());
    }

    @Test
    @DisplayName ("as colunas 5 a 11 sao o recfm")
    void recfmComesFromColumnsFiveToEleven ()
    {
      assertEquals ("FB", onlyDataset ().getRecfm ());
    }

    @Test
    @DisplayName ("as colunas 11 a 18 sao o lrecl")
    void lreclComesFromColumnsElevenToEighteen ()
    {
      assertEquals (80, onlyDataset ().getLrecl ());
    }

    @Test
    @DisplayName ("o que sobra depois da coluna 18 e o blksize")
    void blksizeIsWhateverFollowsColumnEighteen ()
    {
      assertEquals (27920, onlyDataset ().getBlksize ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("screenType 3 - Message e Volume")
  class VolumeLayout
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("quatro campos na linha 5 dao o layout de volume")
    void fourHeadingFieldsGiveTheVolumeLayout ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Message", "Volume", "");

      // O campo de mensagem vai com espacos, e nao vazio: um campo de largura zero e
      // descartado por FieldManager.getFieldsInRange, e a linha voltaria com dois campos em
      // vez de tres - o suficiente para criar o dataset e nao preencher o volume.
      datasetRow (7, "MY.DATA.SET", "      ", "PRD001");
      endOfList (8);
      send ();

      assertEquals ("PRD001", onlyDataset ().getVolume (),
          "o volume vem do terceiro campo da linha, ja sem espacos");
    }
  }

  /*
   * Os dois layouts de varias linhas por dataset. Sao os que a onda 3 mais precisa ter
   * congelados, por dois motivos.
   *
   * Primeiro, o reconhecimento nao para na linha 5: com seis campos ali, checkDatasetList vai
   * olhar a linha 7 e decidir entre o screenType 4 e o 5 pelo que encontra - a palavra
   * "Catalog" ou uma linha de tracos. Sao dois niveis de decisao encadeados.
   *
   * Segundo, e principal: os deslocamentos de coluna NAO sao os mesmos dos layouts de uma
   * linha. O espaco e lido em 6/11/15 no screenType 1 e em 6/10/14 aqui; a disposicao em
   * 5/11/18 no screenType 2 e em 5/10/16 aqui. Sao quatro conjuntos de numeros cravados em
   * dois switches paralelos, e trocar por Strategy sem trocar nenhum deles e exatamente o que
   * estes testes verificam.
   */
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("screenType 4 - Catalog, tres linhas por dataset")
  class CatalogLayout
  // ---------------------------------------------------------------------------------//
  {
    @BeforeEach
    void catalogScreen ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Message", "Volume", "a", "b",
                  "c");

      // A linha 7 com um campo unico contendo "Catalog" e o que escolhe o screenType 4.
      field (7, 0, PROTECTED, "                    Catalog");

      // Tres linhas por dataset, sete campos no total, na ordem em que addDataset os le.
      field (9, 0, UNPROTECTED, "         MY.BIG.DATASET");
      field (9, 45, PROTECTED, "      ");
      field (9, 55, PROTECTED, "PRD001");
      //                        0     6   10  14
      field (10, 0, PROTECTED, "   150  75   3 3390");
      //                        0    5    10    16
      field (10, 30, PROTECTED, "PO   FB       80  27920");
      //                        0          11         22
      field (11, 0, PROTECTED, "2024/01/15 ***None*** 2024/06/30");
      field (11, 40, PROTECTED, "CATALOG.MASTER");

      // Uma linha so na 12 faz o laco parar quando ele chega na 13.
      field (12, 0, PROTECTED, "");
      send ();
    }

    @Test
    @DisplayName ("o nome vem da primeira das tres linhas")
    void nameComesFromTheFirstLine ()
    {
      assertEquals ("MY.BIG.DATASET", onlyDataset ().getDatasetName ());
    }

    @Test
    @DisplayName ("o volume vem do terceiro campo")
    void volumeComesFromTheThirdField ()
    {
      assertEquals ("PRD001", onlyDataset ().getVolume ());
    }

    @Test
    @DisplayName ("o espaco e lido em 6/10/14, e nao em 6/11/15 como no screenType 1")
    void spaceUsesTheCatalogOffsets ()
    {
      TableDataset dataset = onlyDataset ();

      assertEquals (150, dataset.getTracks ());
      assertEquals (75, dataset.getPercentUsed ());
      assertEquals (3, dataset.getExtents ());
      assertEquals ("3390", dataset.getDevice ());
    }

    @Test
    @DisplayName ("a disposicao e lida em 5/10/16, e nao em 5/11/18 como no screenType 2")
    void dispositionUsesTheCatalogOffsets ()
    {
      TableDataset dataset = onlyDataset ();

      assertEquals ("PO", dataset.getDsorg ());
      assertEquals ("FB", dataset.getRecfm ());
      assertEquals (80, dataset.getLrecl ());
      assertEquals (27920, dataset.getBlksize ());
    }

    @Test
    @DisplayName ("as datas saem em blocos de 11, 11 e o resto")
    void datesAreSplitInElevenColumnBlocks ()
    {
      TableDataset dataset = onlyDataset ();

      assertEquals ("2024/01/15", dataset.getCreated ());
      assertEquals ("***None***", dataset.getExpires ());
      assertEquals ("2024/06/30", dataset.getReferredDate ());
    }

    @Test
    @DisplayName ("o catalogo entra so se parecer nome de dataset")
    void catalogIsAcceptedOnlyWhenItLooksLikeADatasetName ()
    {
      assertEquals ("CATALOG.MASTER", onlyDataset ().getCatalog ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("screenType 5 - tracos, duas linhas por dataset")
  class UnderscoreLayout
  // ---------------------------------------------------------------------------------//
  {
    @BeforeEach
    void underscoreScreen ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Message", "Volume", "a", "b",
                  "c");

      // Agora a linha 7 traz tracos, e nao "Catalog": e o screenType 5.
      field (7, 0, PROTECTED, "-------------------------------");

      // Duas linhas por dataset, seis campos.
      field (8, 0, UNPROTECTED, "         MY.OTHER.DATASET");
      field (8, 45, PROTECTED, "      ");
      field (8, 55, PROTECTED, "PRD002");
      // As colunas dos atributos dao a cada campo espaco para o texto inteiro: 19 posicoes
      // para o espaco, 24 para a disposicao e 34 para as datas. Apertar qualquer um faz o
      // texto seguinte sobrescrever a cauda do anterior, e o teste passa a medir o proprio
      // erro de montagem em vez do comportamento do ScreenWatcher.
      field (9, 0, PROTECTED, "   300  50   1 3380");
      field (9, 20, PROTECTED, "PS   VB      255  27998");
      field (9, 45, PROTECTED, "2023/02/20 2025/12/31 2024/03/01");

      // A linha 10 e a de tracos que o laco salta; a 11 com um campo so o faz parar.
      field (10, 0, PROTECTED, "-------------------------------");
      field (11, 0, PROTECTED, "");
      send ();
    }

    @Test
    @DisplayName ("o nome e o volume vem da primeira das duas linhas")
    void nameAndVolumeComeFromTheFirstLine ()
    {
      TableDataset dataset = onlyDataset ();

      assertEquals ("MY.OTHER.DATASET", dataset.getDatasetName ());
      assertEquals ("PRD002", dataset.getVolume ());
    }

    @Test
    @DisplayName ("o espaco e a disposicao usam os mesmos deslocamentos do screenType 4")
    void spaceAndDispositionShareTheCatalogOffsets ()
    {
      TableDataset dataset = onlyDataset ();

      assertEquals (300, dataset.getTracks ());
      assertEquals (50, dataset.getPercentUsed ());
      assertEquals (1, dataset.getExtents ());
      assertEquals ("3380", dataset.getDevice ());

      assertEquals ("PS", dataset.getDsorg ());
      assertEquals ("VB", dataset.getRecfm ());
      assertEquals (255, dataset.getLrecl ());
      assertEquals (27998, dataset.getBlksize ());
    }

    @Test
    @DisplayName ("as datas tambem saem em blocos de 11")
    void datesAreSplitTheSameWay ()
    {
      TableDataset dataset = onlyDataset ();

      assertEquals ("2023/02/20", dataset.getCreated ());
      assertEquals ("2025/12/31", dataset.getExpires ());
      assertEquals ("2024/03/01", dataset.getReferredDate ());
    }

    @Test
    @DisplayName ("este layout nao preenche o catalogo")
    void catalogIsNotSetByThisLayout ()
    {
      assertEquals (null, onlyDataset ().getCatalog (),
          "o ramo do screenType 5 nao le campo de catalogo");
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("o dataset acumulado entre telas")
  class Accumulation
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("a mesma lista com dois datasets devolve os dois, na ordem da tela")
    void twoDatasetsComeBackInScreenOrder ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Tracks %Used XT Device", "");
      datasetRow (7, "FIRST.DATA.SET", "   150   75   3 3390");
      datasetRow (8, "SECOND.DATA.SET", "   300   50   1 3390");
      endOfList (9);
      send ();

      List<TableDataset> datasets = watcher.getDatasets ();
      assertEquals (2, datasets.size ());
      assertEquals ("FIRST.DATA.SET", datasets.get (0).getDatasetName ());
      assertEquals ("SECOND.DATA.SET", datasets.get (1).getDatasetName ());
    }

    @Test
    @DisplayName ("um nome invalido e descartado sem interromper a lista")
    void anInvalidNameIsSkipped ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Tracks %Used XT Device", "");
      datasetRow (7, "nome.minusculo", "   150   75   3 3390");
      datasetRow (8, "VALID.DATA.SET", "   300   50   1 3390");
      endOfList (9);
      send ();

      List<TableDataset> datasets = watcher.getDatasets ();
      assertEquals (1, datasets.size (), "veio " + datasets.size ());
      assertEquals ("VALID.DATA.SET", datasets.get (0).getDatasetName ());
    }

    @Test
    @DisplayName ("uma tela que nao e lista de dataset limpa a lista da tela anterior")
    void anUnrelatedScreenClearsThePreviousList ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Tracks %Used XT Device", "");
      datasetRow (7, "MY.DATA.SET", "   150   75   3 3390");
      endOfList (8);
      send ();
      assertFalse (watcher.getDatasets ().isEmpty (), "a primeira tela tinha dataset");

      setUp ();
      field (0, 0, PROTECTED, "  outra tela qualquer");
      field (1, 0, PROTECTED, "sem prompt do ISPF");
      field (2, 0, PROTECTED, "");
      send ();

      assertTrue (watcher.getDatasets ().isEmpty (),
          "check () limpa screenDatasets no inicio de cada tela");
    }
  }
}
