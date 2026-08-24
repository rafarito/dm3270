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

import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.DatasetStore;
import com.bytezone.dm3270.datasets.Member;
import com.bytezone.dm3270.datasets.StoreListener;
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
  private RecordingStore store;
  private final List<Integer> stream = new ArrayList<> ();

  // ---------------------------------------------------------------------------------//
  @BeforeEach
  void setUp ()
  // ---------------------------------------------------------------------------------//
  {
    store = new RecordingStore ();
    screen = new HeadlessScreenTarget (new ScreenDimensions (ROWS, COLUMNS),
        new ScreenDimensions (43, COLUMNS), store);
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
  private DatasetSummary onlyDataset ()
  // ---------------------------------------------------------------------------------//
  {
    List<DatasetSummary> datasets = watcher.getDatasets ();
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
      DatasetSummary dataset = onlyDataset ();

      assertEquals (150, dataset.getTracks ());
      assertEquals (75, dataset.getPercentUsed ());
      assertEquals (3, dataset.getExtents ());
      assertEquals ("3390", dataset.getDevice ());
    }

    @Test
    @DisplayName ("a disposicao e lida em 5/10/16, e nao em 5/11/18 como no screenType 2")
    void dispositionUsesTheCatalogOffsets ()
    {
      DatasetSummary dataset = onlyDataset ();

      assertEquals ("PO", dataset.getDsorg ());
      assertEquals ("FB", dataset.getRecfm ());
      assertEquals (80, dataset.getLrecl ());
      assertEquals (27920, dataset.getBlksize ());
    }

    @Test
    @DisplayName ("as datas saem em blocos de 11, 11 e o resto")
    void datesAreSplitInElevenColumnBlocks ()
    {
      DatasetSummary dataset = onlyDataset ();

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
      DatasetSummary dataset = onlyDataset ();

      assertEquals ("MY.OTHER.DATASET", dataset.getDatasetName ());
      assertEquals ("PRD002", dataset.getVolume ());
    }

    @Test
    @DisplayName ("o espaco e a disposicao usam os mesmos deslocamentos do screenType 4")
    void spaceAndDispositionShareTheCatalogOffsets ()
    {
      DatasetSummary dataset = onlyDataset ();

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
      DatasetSummary dataset = onlyDataset ();

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

      List<DatasetSummary> datasets = watcher.getDatasets ();
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

      List<DatasetSummary> datasets = watcher.getDatasets ();
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

  // ---------------------------------------------------------------------------------//
  // Telas que nao sao lista de dataset
  // ---------------------------------------------------------------------------------//

  /*
   * A linha de comando do ISPF, na linha pedida. O campo de entrada fica com 66 posicoes
   * porque o proximo atributo esta na linha seguinte, coluna zero - e 66 e um dos dois
   * comprimentos que hasPromptField aceita.
   */
  // ---------------------------------------------------------------------------------//
  private void commandLine (int row)
  // ---------------------------------------------------------------------------------//
  {
    field (row, 0, PROTECTED, COMMAND_PROMPT);
    field (row, 13, UNPROTECTED, "");
  }

  /*
   * A barra de menus da linha 0, que e o que checkMemberList usa para escolher entre os dois
   * caminhos: pdsMenus tem cinco entradas e memberMenus tem quatro.
   *
   * getMenus le so a linha 0, so campos protegidos, visiveis e com mais de uma posicao, e
   * descarta os de texto vazio. O campo em branco do fim entra na contagem de campos da tela
   * - que e o que decide os indices que checkMemberList1 e checkMemberList2 esperam - sem
   * entrar na lista de menus.
   */
  // ---------------------------------------------------------------------------------//
  private void menuBar (String... names)
  // ---------------------------------------------------------------------------------//
  {
    int col = 0;
    for (String name : names)
    {
      field (0, col, PROTECTED, name);
      col += name.length () + 4;
    }
    field (0, col, PROTECTED, "");
  }

  /*
   * Preenche uma linha com a quantidade pedida de campos vazios, distribuidos por igual.
   * Serve para levar a contagem de campos ate o indice exato que o codigo sob teste espera -
   * checkPrefixScreen le screenFields.get (10), get (23), get (24), get (72) e get (73).
   */
  // ---------------------------------------------------------------------------------//
  private void fillRow (int row, int count)
  // ---------------------------------------------------------------------------------//
  {
    int pitch = COLUMNS / count;
    for (int i = 0; i < count; i++)
      field (row, i * pitch, PROTECTED, "");
  }

  /*
   * A linha 4, de titulos. checkMemberList1 escolhe o formato dos detalhes so pela
   * QUANTIDADE de campos aqui (7, 10 ou 13); checkMemberList2 tambem le o texto do sexto.
   *
   * Os campos vazios ficam com uma posicao cada, e o sexto recebe a largura exata do texto -
   * escrever mais do que cabe ate o proximo Start Field sobrescreveria o campo seguinte.
   */
  // ---------------------------------------------------------------------------------//
  private void headingsRow (int count, String sixth)
  // ---------------------------------------------------------------------------------//
  {
    int col = 0;
    for (int i = 0; i < count; i++)
    {
      String text = i == 5 ? sixth : "";
      field (4, col, PROTECTED, text);
      col += text.length () + 2;
    }
  }

  /*
   * Uma linha de membro: quatro campos, que e o que os dois checkMemberList exigem. O nome
   * fica com oito posicoes, exatamente o tamanho de "**End** " - o marcador de fim de lista,
   * que o codigo compara com equals e nao com trim.
   */
  // ---------------------------------------------------------------------------------//
  private void memberRow (int row, String name, String details)
  // ---------------------------------------------------------------------------------//
  {
    field (row, 0, UNPROTECTED, "");
    field (row, 9, PROTECTED, name);
    field (row, 18, PROTECTED, "");
    field (row, 25, PROTECTED, details);
  }

  /*
   * O cabecalho de uma lista de membros do tipo 1 - a que checkMemberList1 reconhece.
   *
   * Os indices sao o contrato: a barra de menus mais o campo em branco dao seis campos, a
   * linha de comando da mais dois, e o proximo campo - o atributo na linha 2, coluna 0 - e o
   * de indice 8, que o codigo exige na posicao 161. O modo fica nele, e o nome do dataset no
   * de indice 9, que tem de comecar em 179.
   */
  // ---------------------------------------------------------------------------------//
  private void pdsHeader (String mode, String datasetName)
  // ---------------------------------------------------------------------------------//
  {
    menuBar ("Menu", "Functions", "Confirm", "Utilities", "Help");
    commandLine (1);
    field (2, 0, PROTECTED, mode);                    // campo 8, posicao 161
    field (2, 18, PROTECTED, datasetName);            // campo 9, posicao 179
    field (3, 0, PROTECTED, "");
  }

  /*
   * O mesmo para o tipo 2. Sao quatro menus em vez de cinco, entao o campo do modo cai para o
   * indice 7 - e o nome do dataset, para o 8, na posicao 170 em vez de 179.
   */
  // ---------------------------------------------------------------------------------//
  private void memberHeader (String mode, String datasetName)
  // ---------------------------------------------------------------------------------//
  {
    menuBar ("Menu", "Functions", "Utilities", "Help");
    commandLine (1);
    field (2, 0, PROTECTED, mode);                    // campo 7, posicao 161
    field (2, 9, PROTECTED, datasetName);             // campo 8, posicao 170
    field (3, 0, PROTECTED, "");
  }

  // ---------------------------------------------------------------------------------//
  private DatasetSummary onlyMember ()
  // ---------------------------------------------------------------------------------//
  {
    List<DatasetSummary> members = watcher.getMembers ();
    assertEquals (1, members.size (), "esperava um membro, veio " + members.size ());
    return members.get (0);
  }

  /*
   * Os detalhes de um membro, montados por bloco para que os deslocamentos fiquem visiveis no
   * proprio teste. Cada bloco e preenchido com espacos ate a largura pedida, e a soma tem de
   * dar as 54 posicoes do campo de detalhes.
   */
  // ---------------------------------------------------------------------------------//
  private String details (String... blocks)
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();
    for (String block : blocks)
      text.append (block);
    return text.toString ();
  }

  // ---------------------------------------------------------------------------------//
  private String pad (String value, int width)
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder (value);
    while (text.length () < width)
      text.append (' ');
    return text.toString ();
  }

  // ---------------------------------------------------------------------------------//
  private String padLeft (String value, int width)
  // ---------------------------------------------------------------------------------//
  {
    StringBuilder text = new StringBuilder ();
    while (text.length () < width - value.length ())
      text.append (' ');
    return text.append (value).toString ();
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("lista de membros - pdsMenus, cinco menus")
  class MemberListOne
  // ---------------------------------------------------------------------------------//
  {
    // 12 + 13 + 13 + 9 + 7 = 54, que e a largura do campo de detalhes
    private String libraryDetails ()
    {
      return details (padLeft ("42", 12), pad ("2024/01/15", 13),
          pad ("2025/06/01", 13), pad ("14:30:00", 9), pad ("USER01", 7));
    }

    @Test
    @DisplayName ("o modo LIBRARY le as datas em 12/25/38 e o id em 47")
    void libraryModeReadsDatesAndId ()
    {
      pdsHeader ("LIBRARY", "SYS1.PROCLIB");
      headingsRow (7, "");
      memberRow (5, "IEFBR14", libraryDetails ());
      endOfList (6);
      send ();

      assertFalse (watcher.getMembers ().isEmpty (), "nao reconheceu a lista de membros");
      assertEquals ("SYS1.PROCLIB(IEFBR14)", onlyMember ().getDatasetName ());
      assertEquals ("2024/01/15", onlyMember ().getCreated ());
      assertEquals ("2025/06/01", onlyMember ().getReferredDate ());
      assertEquals ("14:30:00", onlyMember ().getReferredTime ());
      assertEquals ("USER01", onlyMember ().getCatalog ());
    }

    /*
     * Uma peculiaridade que a decomposicao tem de preservar: screenType1 le o MESMO trecho
     * duas vezes, uma para setExtents e outra para o tamanho do Member. As duas colunas
     * acabam com o mesmo numero.
     */
    @Test
    @DisplayName ("o tamanho e os extents saem do mesmo trecho, e ficam iguais")
    void sizeAndExtentsShareTheSameSlice ()
    {
      pdsHeader ("LIBRARY", "SYS1.PROCLIB");
      headingsRow (7, "");
      memberRow (5, "IEFBR14", libraryDetails ());
      endOfList (6);
      send ();

      assertEquals (42, onlyMember ().getExtents ());
      assertEquals (1, store.members.size ());
      assertEquals (42, store.members.get (0).getSize ());
    }

    @Test
    @DisplayName ("os modos de edicao usam 9/21/33 e o id em 42")
    void editModeUsesTheOtherOffsets ()
    {
      pdsHeader ("EDIT", "SYS1.PROCLIB");
      headingsRow (7, "");
      // 9 + 12 + 12 + 9 + 12 = 54
      memberRow (5, "IEFBR14", details (padLeft ("7", 9), pad ("2024/01/15", 12),
          pad ("2025/06/01", 12), pad ("14:30:00", 9), pad ("USER01", 12)));
      endOfList (6);
      send ();

      assertEquals ("2024/01/15", onlyMember ().getCreated ());
      assertEquals ("2025/06/01", onlyMember ().getReferredDate ());
      assertEquals ("14:30:00", onlyMember ().getReferredTime ());
      assertEquals ("USER01", onlyMember ().getCatalog ());
      assertEquals (7, onlyMember ().getExtents ());
    }

    @Test
    @DisplayName ("treze titulos trocam o formato dos detalhes por VV.MM e id")
    void thirteenHeadingsSwitchToStatistics ()
    {
      pdsHeader ("LIBRARY", "SYS1.PROCLIB");
      headingsRow (13, "");
      // 12 + 9 + 10 + 12 + 11 = 54
      memberRow (5, "IEFBR14", details (padLeft ("10", 12), padLeft ("7", 9),
          padLeft ("3", 10), pad ("01.05", 12), pad ("USER02", 11)));
      endOfList (6);
      send ();

      assertEquals ("USER02", onlyMember ().getCatalog ());
      assertEquals (10, onlyMember ().getExtents ());

      assertEquals (1, store.members.size ());
      assertEquals (10, store.members.get (0).getSize ());
      assertEquals (7, store.members.get (0).getInit ());
      assertEquals (3, store.members.get (0).getMod ());
      assertEquals (1, store.members.get (0).getVv ());
      assertEquals (5, store.members.get (0).getMm ());
      assertEquals ("USER02", store.members.get (0).getId ());
    }

    @Test
    @DisplayName ("um modo desconhecido nao produz lista de membros")
    void unknownModeIsRejected ()
    {
      pdsHeader ("QUALQUER", "SYS1.PROCLIB");
      headingsRow (7, "");
      memberRow (5, "IEFBR14", libraryDetails ());
      endOfList (6);
      send ();

      assertTrue (watcher.getMembers ().isEmpty ());
      assertTrue (watcher.getMembers ().isEmpty ());
    }

    @Test
    @DisplayName ("o marcador de fim interrompe a leitura, e ele proprio nao entra")
    void endMarkerStopsTheList ()
    {
      pdsHeader ("LIBRARY", "SYS1.PROCLIB");
      headingsRow (7, "");
      memberRow (5, "IEFBR14", libraryDetails ());
      memberRow (6, "**End**", libraryDetails ());
      memberRow (7, "DEPOIS", libraryDetails ());
      endOfList (8);
      send ();

      assertEquals (1, watcher.getMembers ().size ());
      assertEquals ("SYS1.PROCLIB(IEFBR14)", onlyMember ().getDatasetName ());
    }

    @Test
    @DisplayName ("um nome de membro invalido tambem interrompe a leitura")
    void invalidMemberNameStopsTheList ()
    {
      pdsHeader ("LIBRARY", "SYS1.PROCLIB");
      headingsRow (7, "");
      memberRow (5, "IEFBR14", libraryDetails ());
      memberRow (6, "nao-vale", libraryDetails ());
      memberRow (7, "DEPOIS", libraryDetails ());
      endOfList (8);
      send ();

      assertEquals (1, watcher.getMembers ().size ());
    }

    @Test
    @DisplayName ("o dataset corrente passa a ser o PDS da lista")
    void recordsTheCurrentPds ()
    {
      pdsHeader ("LIBRARY", "SYS1.PROCLIB");
      headingsRow (7, "");
      memberRow (5, "IEFBR14", libraryDetails ());
      endOfList (6);
      send ();

      assertEquals ("SYS1.PROCLIB", watcher.getCurrentPDS ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("lista de membros - memberMenus, quatro menus")
  class MemberListTwo
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("dez titulos com Created no sexto dao o formato de datas")
    void createdHeadingSelectsDates ()
    {
      memberHeader ("EDIT", "SYS1.PROCLIB");
      headingsRow (10, "Created");
      memberRow (5, "IEFBR14", details (padLeft ("42", 12), pad ("2024/01/15", 13),
          pad ("2025/06/01", 13), pad ("14:30:00", 9), pad ("USER01", 7)));
      endOfList (6);
      send ();

      assertFalse (watcher.getMembers ().isEmpty (), "nao reconheceu a lista de membros");
      assertEquals ("SYS1.PROCLIB(IEFBR14)", onlyMember ().getDatasetName ());
      assertEquals ("2024/01/15", onlyMember ().getCreated ());
      assertEquals ("USER01", onlyMember ().getCatalog ());
    }

    @Test
    @DisplayName ("treze titulos com Init no sexto dao o formato de estatisticas")
    void initHeadingSelectsStatistics ()
    {
      memberHeader ("BROWSE", "SYS1.PROCLIB");
      headingsRow (13, "Init");
      memberRow (5, "IEFBR14", details (padLeft ("10", 12), padLeft ("7", 9),
          padLeft ("3", 10), pad ("01.05", 12), pad ("USER02", 11)));
      endOfList (6);
      send ();

      assertEquals (1, store.members.size ());
      assertEquals (10, store.members.get (0).getSize ());
      assertEquals (1, store.members.get (0).getVv ());
      assertEquals (5, store.members.get (0).getMm ());
    }

    @Test
    @DisplayName ("um modo fora de EDIT, BROWSE e VIEW nao produz lista")
    void unknownModeIsRejected ()
    {
      memberHeader ("LIBRARY", "SYS1.PROCLIB");
      headingsRow (10, "Created");
      memberRow (5, "IEFBR14", pad ("", 54));
      endOfList (6);
      send ();

      assertTrue (watcher.getMembers ().isEmpty ());
    }

    @Test
    @DisplayName ("um sexto titulo inesperado nao produz lista, mesmo com dez titulos")
    void unexpectedHeadingIsRejected ()
    {
      memberHeader ("EDIT", "SYS1.PROCLIB");
      headingsRow (10, "Outro");
      memberRow (5, "IEFBR14", pad ("", 54));
      endOfList (6);
      send ();

      assertTrue (watcher.getMembers ().isEmpty ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("tela inicial do ISPF - userid e prefixo")
  class PrefixScreen
  // ---------------------------------------------------------------------------------//
  {
    /*
     * checkPrefixScreen le por INDICE e por POSICAO ABSOLUTA: o titulo tem de ser o campo 10,
     * " User ID . :" o campo 23 na posicao 457, o userid o campo 24 na 470, " TSO prefix:" o
     * campo 72 na 1017 e o prefixo o campo 73 na 1030 - e a tela tem de ter 74 campos ou
     * mais. A contagem de campos de cada linha abaixo existe so para acertar esses indices.
     */
    private void primaryOptionMenu (String heading, String userid, String prefix)
    {
      fillRow (0, 10);                                  // campos 0 a 9
      field (1, 0, PROTECTED, heading);                 // campo 10
      field (1, 25, PROTECTED, "");                     // campo 11
      commandLine (2);                                  // campos 12 e 13
      fillRow (3, 4);                                   // campos 14 a 17
      fillRow (4, 4);                                   // campos 18 a 21
      field (5, 0, PROTECTED, "");                      // campo 22
      field (5, 56, PROTECTED, " User ID . :");         // campo 23, posicao 457
      field (5, 69, PROTECTED, userid);                 // campo 24, posicao 470
      fillRow (6, 8);                                   // campos 25 a 32
      fillRow (7, 8);                                   // campos 33 a 40
      fillRow (8, 8);                                   // campos 41 a 48
      fillRow (9, 8);                                   // campos 49 a 56
      fillRow (10, 7);                                  // campos 57 a 63
      fillRow (11, 7);                                  // campos 64 a 70
      field (12, 0, PROTECTED, "");                     // campo 71
      field (12, 56, PROTECTED, " TSO prefix:");        // campo 72, posicao 1017
      field (12, 69, PROTECTED, prefix);                // campo 73, posicao 1030
      endOfList (13);
    }

    @Test
    @DisplayName ("o menu principal do ISPF entrega userid e prefixo")
    void ispfPrimaryOptionMenu ()
    {
      primaryOptionMenu ("ISPF Primary Option Menu", "DMOLONY", "DMOLONYB");
      send ();

      assertEquals ("DMOLONY", watcher.getUserid ());
      assertEquals ("DMOLONYB", watcher.getPrefix ());
    }

    @Test
    @DisplayName ("o menu do z/OS tambem serve")
    void zosPrimaryOptionMenu ()
    {
      primaryOptionMenu ("z/OS Primary Option Menu", "USER01", "USER01A");
      send ();

      assertEquals ("USER01", watcher.getUserid ());
      assertEquals ("USER01A", watcher.getPrefix ());
    }

    @Test
    @DisplayName ("qualquer outro titulo na mesma posicao e ignorado")
    void otherHeadingIsIgnored ()
    {
      primaryOptionMenu ("Qualquer Outra Coisa Ali", "DMOLONY", "DMOLONYB");
      send ();

      assertTrue (watcher.getPrefix ().isEmpty (), watcher.getPrefix ());
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("dataset unico - a tela de EDIT, VIEW ou BROWSE")
  class SingleDataset
  // ---------------------------------------------------------------------------------//
  {
    /*
     * checkSingleDataset procura, nas tres primeiras linhas, um campo na coluna 1 com nove ou
     * dez posicoes cujo texto seja EDIT, VIEW ou BROWSE, e confere o campo DOIS a frente -
     * sem trim - contra "Columns" ou "Line". O nome vem do campo do meio.
     */
    private void editScreen (String mode, String name, String third)
    {
      field (0, 0, PROTECTED, mode);          // dez posicoes: proximo atributo na coluna 11
      field (0, 11, PROTECTED, name);
      field (0, 41, PROTECTED, third);        // largura exata do texto, sem preenchimento
      field (0, 41 + third.length () + 1, PROTECTED, "");
      commandLine (1);
      endOfList (2);
    }

    @Test
    @DisplayName ("a tela de EDIT registra o dataset como recente")
    void editRecordsTheDataset ()
    {
      editScreen ("EDIT", "MY.DATA.SET", "Columns");
      send ();

      assertEquals ("MY.DATA.SET", watcher.getSingleDataset ());
      assertEquals (List.of ("MY.DATA.SET"), watcher.getRecentDatasets ());
    }

    @Test
    @DisplayName ("o membro entre parenteses e preservado no nome")
    void keepsTheMemberName ()
    {
      editScreen ("EDIT", "SYS1.PROCLIB(IEFBR14)", "Columns");
      send ();

      assertEquals ("SYS1.PROCLIB(IEFBR14)", watcher.getSingleDataset ());
    }

    @Test
    @DisplayName ("o nome termina no primeiro espaco - o resto e numero de versao")
    void stopsAtTheFirstSpace ()
    {
      editScreen ("VIEW", "MY.DATA.SET - 01.00", "Columns");
      send ();

      assertEquals ("MY.DATA.SET", watcher.getSingleDataset ());
    }

    @Test
    @DisplayName ("BROWSE com Line no lugar de Columns tambem conta")
    void browseWithLineAlsoCounts ()
    {
      editScreen ("BROWSE", "MY.DATA.SET", "Line");
      send ();

      assertEquals ("MY.DATA.SET", watcher.getSingleDataset ());
    }

    @Test
    @DisplayName ("um terceiro campo diferente nao produz dataset")
    void otherThirdFieldIsIgnored ()
    {
      editScreen ("EDIT", "MY.DATA.SET", "Outro");
      send ();

      assertTrue (watcher.getSingleDataset ().isEmpty (), watcher.getSingleDataset ());
    }

    @Test
    @DisplayName ("um nome que nao parece dataset nao produz dataset")
    void invalidNameIsIgnored ()
    {
      editScreen ("EDIT", "nao.vale.nada", "Columns");
      send ();

      assertTrue (watcher.getSingleDataset ().isEmpty (), watcher.getSingleDataset ());
    }
  }

  /*
   * O DatasetStore que o ScreenWatcher enxerga nestes testes. Ate agora esse caminho terminava
   * no DatasetStore.NONE e era invisivel: dava para conferir o que ia para a tabela do
   * assistant, mas nao o que ia para a persistencia. Sao os dois lados que a decomposicao do
   * ScreenWatcher tem de preservar.
   */
  // ---------------------------------------------------------------------------------//
  private static class RecordingStore implements DatasetStore
  // ---------------------------------------------------------------------------------//
  {
    private final List<Dataset> datasets = new ArrayList<> ();
    private final List<Member> members = new ArrayList<> ();

    @Override
    public void open (StoreListener listener)
    {
    }

    @Override
    public void close (StoreListener listener)
    {
    }

    @Override
    public void update (Dataset dataset)
    {
      datasets.add (dataset);
    }

    @Override
    public void update (Member member)
    {
      members.add (member);
    }
  }
}
