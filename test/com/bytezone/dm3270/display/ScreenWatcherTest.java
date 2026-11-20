package com.bytezone.dm3270.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.bytezone.dm3270.watch.ScreenWatcher;
import com.bytezone.dm3270.screen.ScreenDimensions;
import com.bytezone.dm3270.utilities.Dm3270Utility;

// Telas sinteticas minimas (nao capturas reais) para fixar layouts e offsets do ScreenWatcher.
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

  // hasPromptField exige texto exato seguido de campo de entrada com 66 ou 48 posicoes.
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

  // Atributo em (row, col); dados comecam em col+1 ate o proximo SF (define a largura).
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

  // Linha 1 cria entrada de 66 posicoes ate (2,0); linhas 3-4 ajudam a passar de 21 campos.
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

  // Linha 5 decide o screenType; +1 na coluna reserva o byte de atributo de cada campo.
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

  // Linha unica encerra o laco de leitura; demais completam >= 21 campos de checkDatasetList.
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
  private Dataset onlyStoredDataset ()
  // ---------------------------------------------------------------------------------//
  {
    assertEquals (1, store.datasets.size (),
        "esperava um dataset gravado, veio " + store.datasets.size ());
    return store.datasets.get (0);
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

    @Test
    @DisplayName ("o banco recebe espaco e dispositivo, e nenhuma disposicao")
    void storesSpaceAndDeviceOnly ()
    {
      Dataset stored = onlyStoredDataset ();

      assertEquals ("MY.DATA.SET", stored.getName ());
      assertEquals (150, stored.getTracks ());
      assertEquals (75, stored.getPercent ());
      assertEquals (3, stored.getExtents ());
      assertEquals ("3390", stored.getDevice ());

      // O ScreenWatcher passa getCylinders () adiante, e nada nesta tela o preenche.
      assertEquals (0, stored.getCylinders ());

      assertNull (stored.getDsorg (), "o ramo do screenType 1 nao chama setDisposition");
      assertNull (stored.getRecfm ());
      assertEquals (0, stored.getLrecl ());
      assertEquals (0, stored.getBlksize ());
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

    @Test
    @DisplayName ("o banco recebe a disposicao, e nenhum espaco")
    void storesDispositionOnly ()
    {
      Dataset stored = onlyStoredDataset ();

      assertEquals ("MY.LOAD.LIB", stored.getName ());
      assertEquals ("PO", stored.getDsorg ());
      assertEquals ("FB", stored.getRecfm ());
      assertEquals (80, stored.getLrecl ());
      assertEquals (27920, stored.getBlksize ());

      assertEquals (0, stored.getTracks (), "o ramo do screenType 2 nao chama setSpace");
      assertEquals (0, stored.getPercent ());
      assertEquals (0, stored.getExtents ());
      assertNull (stored.getDevice (), "nem setDevice");
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

      // Usa espacos: FieldManager.getFieldsInRange descarta campos vazios
      // e reduziria a linha de tres para dois campos.
      datasetRow (7, "MY.DATA.SET", "      ", "PRD001");
      endOfList (8);
      send ();

      assertEquals ("PRD001", onlyDataset ().getVolume (),
          "o volume vem do terceiro campo da linha, ja sem espacos");
    }

    @Test
    @DisplayName ("o banco recebe so o volume")
    void storesTheVolumeOnly ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Message", "Volume", "");
      datasetRow (7, "MY.DATA.SET", "      ", "PRD001");
      endOfList (8);
      send ();

      Dataset stored = onlyStoredDataset ();

      assertEquals ("MY.DATA.SET", stored.getName ());
      assertEquals ("PRD001", stored.getVolume ());
      assertEquals (0, stored.getTracks (), "o ramo do screenType 3 so chama setVolume");
      assertNull (stored.getDsorg ());
      assertNull (stored.getDevice ());
    }
  }

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

      // Campo unico na linha 12 faz o laco parar na linha 13.
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

    @Test
    @DisplayName ("o banco recebe volume, espaco, disposicao e catalogo")
    void storesEverythingTheScreenShowed ()
    {
      Dataset stored = onlyStoredDataset ();

      assertEquals ("MY.BIG.DATASET", stored.getName ());
      assertEquals ("PRD001", stored.getVolume ());
      assertEquals ("CATALOG.MASTER", stored.getCatalog ());

      assertEquals (150, stored.getTracks ());
      assertEquals (75, stored.getPercent ());
      assertEquals (3, stored.getExtents ());
      assertEquals ("3390", stored.getDevice ());

      assertEquals ("PO", stored.getDsorg ());
      assertEquals ("FB", stored.getRecfm ());
      assertEquals (80, stored.getLrecl ());
      assertEquals (27920, stored.getBlksize ());
    }

    // DatasetSummary guarda texto bruto; Dataset converte para SQL e deixa null se falhar.
    @Test
    @DisplayName ("uma data que nao converte fica nula no banco, e visivel na tela")
    void anUnparseableDateIsNullInTheDatabaseOnly ()
    {
      Dataset stored = onlyStoredDataset ();

      assertNotNull (stored.getCreatedSQL ());
      assertNotNull (stored.getReferredSQL ());
      assertNull (stored.getExpiresSQL (), "***None*** nao casa com yyyy/MM/dd");

      assertEquals ("***None***", onlyDataset ().getExpires (),
          "o texto observado sobrevive inteiro no DatasetSummary");
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

      field (8, 0, UNPROTECTED, "         MY.OTHER.DATASET");
      field (8, 45, PROTECTED, "      ");
      field (8, 55, PROTECTED, "PRD002");
      // Colunas espacadas para que cada campo comporte o texto sem sobrescrever o anterior.
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

    @Test
    @DisplayName ("o banco recebe espaco e disposicao, mas nem volume nem catalogo")
    void storesSpaceAndDispositionButNotTheVolume ()
    {
      Dataset stored = onlyStoredDataset ();

      assertEquals ("MY.OTHER.DATASET", stored.getName ());

      assertNull (stored.getVolume (), "o ramo do screenType 5 nao chama ds.setVolume");
      assertEquals ("PRD002", onlyDataset ().getVolume (),
          "mas a tela mostra o volume, e a tabela do assistant tambem");

      assertEquals (300, stored.getTracks ());
      assertEquals (50, stored.getPercent ());
      assertEquals (1, stored.getExtents ());

      assertEquals ("PS", stored.getDsorg ());
      assertEquals ("VB", stored.getRecfm ());
      assertEquals (255, stored.getLrecl ());
      assertEquals (27998, stored.getBlksize ());

      assertNull (stored.getCatalog ());

      assertNotNull (stored.getCreatedSQL ());
      assertNull (stored.getDevice (), "o ramo do screenType 5 nao chama setDevice");
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

  // datasetStore.update(ds) fica fora do switch e grava o nome mesmo se a guarda falhar.
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("as guardas de cada ramo, e o que o banco recebe quando elas falham")
  class PartiallyReadRows
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um campo a mais na linha nao le nada, e o banco recebe so o nome")
    void aRowThatMissesTheGuardStillStoresTheName ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Tracks %Used XT Device", "");
      datasetRow (7, "MY.DATA.SET", "   150   75   3 3390", "sobra");
      endOfList (8);
      send ();

      assertEquals ("MY.DATA.SET", onlyDataset ().getDatasetName (),
          "o dataset entra na lista da tela mesmo sem detalhe nenhum");
      assertEquals (0, onlyDataset ().getTracks (), "a guarda exige dois campos");

      Dataset stored = onlyStoredDataset ();
      assertEquals ("MY.DATA.SET", stored.getName ());
      assertEquals (0, stored.getTracks ());
      assertNull (stored.getDevice ());
    }

    @Test
    @DisplayName ("no layout de tracos, tres campos preenchem o volume e mais nada")
    void threeFieldsFillOnlyTheVolume ()
    {
      header ("DSLIST - Data Sets on volume PRD001");
      headingRow ("Command - Enter \"/\" to select action", "Message", "Volume", "a", "b",
                  "c");
      field (7, 0, PROTECTED, "-------------------------------");

      field (8, 0, UNPROTECTED, "         MY.THIN.DATASET");
      field (8, 45, PROTECTED, "      ");
      field (8, 55, PROTECTED, "PRD003");

      field (10, 0, PROTECTED, "-------------------------------");
      field (11, 0, PROTECTED, "");

      // Completa >= 21 campos exigidos por checkDatasetList (sem a linha 9 seriam 18).
      endOfList (12);
      send ();

      DatasetSummary summary = onlyDataset ();
      assertEquals ("MY.THIN.DATASET", summary.getDatasetName ());
      assertEquals ("PRD003", summary.getVolume ());
      assertEquals (0, summary.getTracks (), "espaco so entra com seis campos");
      assertNull (summary.getDsorg ());

      Dataset stored = onlyStoredDataset ();
      assertEquals ("MY.THIN.DATASET", stored.getName ());
      assertNull (stored.getVolume ());
      assertEquals (0, stored.getTracks ());
      assertNull (stored.getDsorg ());
    }
  }

  // hasPromptField exige entrada de 66 posicoes (proximo SF na linha seguinte, col 0).
  // ---------------------------------------------------------------------------------//
  private void commandLine (int row)
  // ---------------------------------------------------------------------------------//
  {
    field (row, 0, PROTECTED, COMMAND_PROMPT);
    field (row, 13, UNPROTECTED, "");
  }

  // Campo vazio no fim entra no indice de screenFields sem ser contado como menu.
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

  // Preenche uma linha com campos vazios para alinhar indices fixos de screenFields.
  // ---------------------------------------------------------------------------------//
  private void fillRow (int row, int count)
  // ---------------------------------------------------------------------------------//
  {
    int pitch = COLUMNS / count;
    for (int i = 0; i < count; i++)
      field (row, i * pitch, PROTECTED, "");
  }

  // Linha 4: 7/10/13 campos escolhem o formato; o sexto define a largura ate o proximo SF.
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

  // Quatro campos por membro; nome tem 8 posicoes por causa de equals("**End** ").
  // ---------------------------------------------------------------------------------//
  private void memberRow (int row, String name, String details)
  // ---------------------------------------------------------------------------------//
  {
    field (row, 0, UNPROTECTED, "");
    field (row, 9, PROTECTED, name);
    field (row, 18, PROTECTED, "");
    field (row, 25, PROTECTED, details);
  }

  // checkMemberList1 exige mode/dataset nos campos 8 (pos 161) e 9 (pos 179).
  // ---------------------------------------------------------------------------------//
  private void pdsHeader (String mode, String datasetName)
  // ---------------------------------------------------------------------------------//
  {
    menuBar ("Menu", "Functions", "Confirm", "Utilities", "Help");
    commandLine (1);
    field (2, 0, PROTECTED, mode);
    field (2, 18, PROTECTED, datasetName);
    field (3, 0, PROTECTED, "");
  }

  // checkMemberList2 (4 menus) desloca mode/dataset para os campos 7 (161) e 8 (170).
  // ---------------------------------------------------------------------------------//
  private void memberHeader (String mode, String datasetName)
  // ---------------------------------------------------------------------------------//
  {
    menuBar ("Menu", "Functions", "Utilities", "Help");
    commandLine (1);
    field (2, 0, PROTECTED, mode);
    field (2, 9, PROTECTED, datasetName);
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

  // Concatena blocos ate preencher as 54 posicoes do campo de detalhes do membro.
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
    // checkPrefixScreen exige >= 74 campos e posicoes exatas: titulo[10],
    // User ID[23@457, 24@470] e TSO prefix[72@1017, 73@1030]; fillRow acerta os indices.
    private void primaryOptionMenu (String heading, String userid, String prefix)
    {
      fillRow (0, 10);
      field (1, 0, PROTECTED, heading);
      field (1, 25, PROTECTED, "");
      commandLine (2);
      fillRow (3, 4);
      fillRow (4, 4);
      field (5, 0, PROTECTED, "");
      field (5, 56, PROTECTED, " User ID . :");
      field (5, 69, PROTECTED, userid);
      fillRow (6, 8);
      fillRow (7, 8);
      fillRow (8, 8);
      fillRow (9, 8);
      fillRow (10, 7);
      fillRow (11, 7);
      field (12, 0, PROTECTED, "");
      field (12, 56, PROTECTED, " TSO prefix:");
      field (12, 69, PROTECTED, prefix);
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
    // checkSingleDataset exige mode (9-10 posicoes) e third sem trim dois campos a frente.
    private void editScreen (String mode, String name, String third)
    {
      field (0, 0, PROTECTED, mode);
      field (0, 11, PROTECTED, name);
      field (0, 41, PROTECTED, third);
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

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("tela de comando TSO (Opcao 6 RFE/RPF)")
  class TsoCommandScreen
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("reconhece a tela RFE TSO (ENTER TSO COMMAND OR CLIST BELOW)")
    void recognisesRfeTsoScreen ()
    {
      field (0, 0, PROTECTED, "RFE TSO");
      field (1, 0, PROTECTED, "ENTER TSO COMMAND OR CLIST BELOW:");
      field (2, 0, PROTECTED, "===>");
      field (2, 5, UNPROTECTED, "");
      field (3, 0, PROTECTED, "");
      send ();

      assertTrue (watcher.isTSOCommandScreen ());
      assertNotNull (watcher.getTSOCommandField ());
    }

    @Test
    @DisplayName ("reconhece a tela RPF TSO Command Processor")
    void recognisesRpfTsoScreen ()
    {
      field (0, 0, PROTECTED, "RPF TSO Command Processor");
      field (1, 0, PROTECTED, "Enter command or END below");
      field (2, 0, UNPROTECTED, "");
      field (3, 0, PROTECTED, "");
      send ();

      assertTrue (watcher.isTSOCommandScreen ());
      assertNotNull (watcher.getTSOCommandField ());
    }
  }

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
