package com.bytezone.dm3270.database;

import com.bytezone.dm3270.datasets.Dataset;
import com.bytezone.dm3270.datasets.Member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.bytezone.dm3270.database.DatabaseRequest.Command;
import com.bytezone.dm3270.database.DatabaseRequest.Result;

// -----------------------------------------------------------------------------------//
@DisplayName ("database - cache SQLite de datasets e membros")
class DatabaseTest
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Dataset")
  class Datasets
  // ---------------------------------------------------------------------------------//
  {
    @Test
    @DisplayName ("um dataset novo tem apenas o nome")
    void startsWithNameOnly ()
    {
      Dataset dataset = new Dataset ("MEU.DATASET");

      assertEquals ("MEU.DATASET", dataset.getName ());
      assertFalse (dataset.isPartitioned ());
      assertTrue (dataset.toString ().contains ("MEU.DATASET"));
    }

    @Test
    @DisplayName ("PO identifica um dataset particionado")
    void recognisesPartitioned ()
    {
      Dataset dataset = new Dataset ("SYS1.PROCLIB");

      dataset.setDisposition ("PO", "FB", 80, 27920);

      assertTrue (dataset.isPartitioned ());
    }

    @ParameterizedTest (name = "dsorg {0}")
    @ValueSource (strings = { "PS", "VS", "DA" })
    @DisplayName ("qualquer outro dsorg e sequencial")
    void otherDsorgIsNotPartitioned (String dsorg)
    {
      Dataset dataset = new Dataset ("MEU.DATASET");

      dataset.setDisposition (dsorg, "FB", 80, 27920);

      assertFalse (dataset.isPartitioned ());
    }

    @Test
    @DisplayName ("localizacao, espaco e disposicao aparecem no relatorio")
    void reportsEverything ()
    {
      Dataset dataset = new Dataset ("MEU.DATASET");

      dataset.setLocation ("FUSR01", "3390", "CATALOG.USER.UCAT");
      dataset.setSpace (15, 1, 2, 75);
      dataset.setDisposition ("PS", "VB", 255, 27998);

      String report = dataset.toString ();

      assertTrue (report.contains ("FUSR01"), report);
      assertTrue (report.contains ("3390"), report);
      assertTrue (report.contains ("CATALOG.USER.UCAT"), report);
      assertTrue (report.contains ("VB"), report);
      assertTrue (report.startsWith ("PS "), report);
    }

    @Test
    @DisplayName ("os setters individuais tambem alimentam a localizacao")
    void individualSetters ()
    {
      Dataset dataset = new Dataset ("MEU.DATASET");

      dataset.setVolume ("FUSR02");
      dataset.setDevice ("3380");
      dataset.setCatalog ("CATALOG.OUTRO");

      String report = dataset.toString ();

      assertTrue (report.contains ("FUSR02"), report);
      assertTrue (report.contains ("3380"), report);
      assertTrue (report.contains ("CATALOG.OUTRO"), report);
    }

    @Test
    @DisplayName ("as datas sao lidas no formato yyyy/MM/dd")
    void parsesDates ()
    {
      Dataset dataset = new Dataset ("MEU.DATASET");

      dataset.setDates ("2024/01/15", "2030/12/31", "2025/06/01");

      String report = dataset.toString ();

      assertTrue (report.contains ("2024/01/15"), report);
      assertTrue (report.contains ("2030/12/31"), report);
      assertTrue (report.contains ("2025/06/01"), report);
    }

    @Test
    @DisplayName ("datas vazias sao simplesmente ignoradas")
    void ignoresEmptyDates ()
    {
      Dataset dataset = new Dataset ("MEU.DATASET");

      dataset.setDates ("", "   ", "");

      assertFalse (dataset.toString ().contains ("/"), dataset.toString ());
    }

    @Test
    @DisplayName ("uma data invalida e reportada e nao interrompe as outras")
    void toleratesInvalidDates ()
    {
      Dataset dataset = new Dataset ("MEU.DATASET");

      dataset.setDates ("nao e data", "2030/12/31", "tambem nao");

      assertTrue (dataset.toString ().contains ("2030/12/31"), dataset.toString ());
    }

    @Test
    @DisplayName ("as datas tambem podem vir do banco em java.sql.Date")
    void acceptsSqlDates ()
    {
      Dataset dataset = new Dataset ("MEU.DATASET");
      java.sql.Date created = java.sql.Date.valueOf ("2024/01/15".replace ('/', '-'));

      dataset.setDates (created, null, null);

      assertTrue (dataset.toString ().contains ("2024/01/15"), dataset.toString ());
    }

    @Test
    @DisplayName ("merge traz do outro apenas os campos preenchidos")
    void mergeKeepsWhatIsAlreadyThere ()
    {
      Dataset current = new Dataset ("MEU.DATASET");
      current.setSpace (15, 1, 2, 75);
      current.setDisposition ("PS", "FB", 80, 27920);

      Dataset update = new Dataset ("MEU.DATASET");
      update.setSpace (20, 0, 0, 0);              // so as trilhas mudaram

      current.merge (update);

      String report = current.toString ();

      assertTrue (report.contains (" 20 "), report);        // trilhas atualizadas
      assertTrue (report.contains ("FB"), report);          // recfm preservado
      assertTrue (report.contains ("PS "), report);         // dsorg preservado
    }

    @Test
    @DisplayName ("merge de datasets com nomes diferentes viola a assercao")
    void mergeChecksTheName ()
    {
      Dataset current = new Dataset ("UM.DATASET");
      Dataset other = new Dataset ("OUTRO.DATASET");

      org.junit.jupiter.api.Assertions.assertThrows (AssertionError.class,
                                                     () -> current.merge (other));
    }

    @Test
    @DisplayName ("differsFrom ignora os campos que o outro nao declara")
    void differsIgnoresUnsetFields ()
    {
      Dataset current = new Dataset ("MEU.DATASET");
      current.setSpace (15, 1, 2, 75);
      current.setDisposition ("PS", "FB", 80, 27920);

      Dataset empty = new Dataset ("MEU.DATASET");

      assertFalse (current.differsFrom (empty));
      assertFalse (current.differsFrom (current));
    }

    @Test
    @DisplayName ("differsFrom aponta cada campo divergente")
    void differsDetectsChanges ()
    {
      Dataset current = new Dataset ("MEU.DATASET");
      current.setSpace (15, 1, 2, 75);
      current.setDisposition ("PS", "FB", 80, 27920);
      current.setLocation ("FUSR01", "3390", "CATALOG.A");
      current.setDates ("2024/01/15", "2030/12/31", "2025/06/01");

      assertTrue (current.differsFrom (withSpace (20, 1, 2, 75)));
      assertTrue (current.differsFrom (withDisposition ("PO", "FB", 80, 27920)));
      assertTrue (current.differsFrom (withDisposition ("PS", "VB", 80, 27920)));
      assertTrue (current.differsFrom (withDisposition ("PS", "FB", 255, 27920)));
      assertTrue (current.differsFrom (withLocation ("FUSR99", null, null)));
      assertTrue (current.differsFrom (withDates ("2020/01/01", "", "")));
    }

    private Dataset withSpace (int tracks, int cylinders, int extents, int percent)
    {
      Dataset dataset = new Dataset ("MEU.DATASET");
      dataset.setSpace (tracks, cylinders, extents, percent);
      return dataset;
    }

    private Dataset withDisposition (String dsorg, String recfm, int lrecl, int blksize)
    {
      Dataset dataset = new Dataset ("MEU.DATASET");
      dataset.setDisposition (dsorg, recfm, lrecl, blksize);
      return dataset;
    }

    private Dataset withLocation (String volume, String device, String catalog)
    {
      Dataset dataset = new Dataset ("MEU.DATASET");
      dataset.setLocation (volume, device, catalog);
      return dataset;
    }

    private Dataset withDates (String created, String expires, String referred)
    {
      Dataset dataset = new Dataset ("MEU.DATASET");
      dataset.setDates (created, expires, referred);
      return dataset;
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("Member")
  class Members
  // ---------------------------------------------------------------------------------//
  {
    private final Dataset dataset = new Dataset ("SYS1.PROCLIB");

    @Test
    @DisplayName ("um membro novo conhece seu dataset")
    void knowsItsDataset ()
    {
      Member member = new Member (dataset, "IEFBR14");

      assertEquals ("IEFBR14", member.getName ());
      assertSame (dataset, member.getDataset ());
      assertTrue (member.toString ().startsWith ("IEFBR14"));
    }

    @Test
    @DisplayName ("o tamanho pode vir sozinho ou com as estatisticas do ISPF")
    void setsSize ()
    {
      Member simple = new Member (dataset, "MEMBRO1");
      simple.setSize (120);

      Member detailed = new Member (dataset, "MEMBRO2");
      detailed.setSize (120, 100, 20, 1, 5);

      assertTrue (simple.toString ().contains ("120"), simple.toString ());
      assertTrue (detailed.toString ().contains ("100"), detailed.toString ());
      assertTrue (detailed.toString ().contains (" 5"), detailed.toString ());
    }

    @Test
    @DisplayName ("o autor da ultima alteracao aparece no relatorio")
    void setsId ()
    {
      Member member = new Member (dataset, "MEMBRO");

      member.setID ("USER01");

      assertTrue (member.toString ().contains ("USER01"), member.toString ());
    }

    @Test
    @DisplayName ("a data de criacao usa yyyy/MM/dd e a de alteracao inclui a hora")
    void parsesDates ()
    {
      Member member = new Member (dataset, "MEMBRO");

      member.setDates ("2024/01/15", "2025/06/01 14:30:00");

      String report = member.toString ();

      assertTrue (report.contains ("2024/01/15"), report);
      assertTrue (report.contains ("2025/06/01 14:30:00"), report);
    }

    @Test
    @DisplayName ("datas invalidas sao reportadas sem interromper")
    void toleratesInvalidDates ()
    {
      Member member = new Member (dataset, "MEMBRO");

      member.setDates ("15/01/2024", "ontem");

      assertFalse (member.toString ().contains ("2024"), member.toString ());
    }

    @Test
    @DisplayName ("as datas tambem podem vir do banco")
    void acceptsSqlDates ()
    {
      Member member = new Member (dataset, "MEMBRO");

      member.setDates (java.sql.Date.valueOf ("2024-01-15"), null);

      assertTrue (member.toString ().contains ("2024/01/15"), member.toString ());
    }

    @Test
    @DisplayName ("merge traz do outro apenas o que esta preenchido")
    void mergeKeepsWhatIsAlreadyThere ()
    {
      Member current = new Member (dataset, "MEMBRO");
      current.setSize (120, 100, 20, 1, 5);
      current.setID ("USER01");

      Member update = new Member (dataset, "MEMBRO");
      update.setSize (150);

      current.merge (update);

      assertTrue (current.toString ().contains ("150"), current.toString ());
      assertTrue (current.toString ().contains ("USER01"), current.toString ());
      assertTrue (current.toString ().contains ("100"), current.toString ());
    }

    @Test
    @DisplayName ("merge exige que os dois membros sejam do mesmo dataset")
    void mergeChecksTheDataset ()
    {
      Member current = new Member (dataset, "MEMBRO");
      Member other = new Member (new Dataset ("OUTRO.PDS"), "MEMBRO");

      org.junit.jupiter.api.Assertions.assertThrows (AssertionError.class,
                                                     () -> current.merge (other));
    }

    @Test
    @DisplayName ("differsFrom ignora os campos zerados do outro")
    void differsIgnoresUnsetFields ()
    {
      Member current = new Member (dataset, "MEMBRO");
      current.setSize (120, 100, 20, 1, 5);
      current.setID ("USER01");

      assertFalse (current.differsFrom (new Member (dataset, "MEMBRO")));
      assertFalse (current.differsFrom (current));
    }

    @Test
    @DisplayName ("differsFrom aponta cada campo divergente")
    void differsDetectsChanges ()
    {
      Member current = new Member (dataset, "MEMBRO");
      current.setSize (120, 100, 20, 1, 5);
      current.setID ("USER01");
      current.setDates ("2024/01/15", "2025/06/01 14:30:00");

      assertTrue (current.differsFrom (withSize (150, 100, 20, 1, 5)));
      assertTrue (current.differsFrom (withSize (120, 999, 20, 1, 5)));
      assertTrue (current.differsFrom (withSize (120, 100, 99, 1, 5)));
      assertTrue (current.differsFrom (withSize (120, 100, 20, 9, 5)));
      assertTrue (current.differsFrom (withSize (120, 100, 20, 1, 9)));
      assertTrue (current.differsFrom (withId ("USER99")));
      assertTrue (current.differsFrom (withDates ("2020/01/01", "")));
    }

    private Member withSize (int size, int init, int mod, int vv, int mm)
    {
      Member member = new Member (dataset, "MEMBRO");
      member.setSize (size, init, mod, vv, mm);
      return member;
    }

    private Member withId (String id)
    {
      Member member = new Member (dataset, "MEMBRO");
      member.setID (id);
      return member;
    }

    private Member withDates (String created, String changed)
    {
      Member member = new Member (dataset, "MEMBRO");
      member.setDates (created, changed);
      return member;
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("CacheEntry")
  class Cache
  // ---------------------------------------------------------------------------------//
  {
    private final Dataset dataset = new Dataset ("SYS1.PROCLIB");

    @Test
    @DisplayName ("o primeiro membro cria o mapa")
    void firstMemberCreatesTheMap ()
    {
      CacheEntry entry = new CacheEntry (dataset);
      Member member = new Member (dataset, "MEMBRO1");

      assertSame (member, entry.addMember (member));
      assertEquals (1, entry.members.size ());
    }

    @Test
    @DisplayName ("um membro novo e apenas adicionado")
    void addsNewMember ()
    {
      CacheEntry entry = new CacheEntry (dataset);
      entry.addMember (new Member (dataset, "MEMBRO1"));

      Member second = new Member (dataset, "MEMBRO2");

      assertSame (second, entry.addMember (second));
      assertEquals (2, entry.members.size ());
    }

    @Test
    @DisplayName ("um membro repetido e fundido no que ja estava no cache")
    void mergesExistingMember ()
    {
      CacheEntry entry = new CacheEntry (dataset);
      Member original = new Member (dataset, "MEMBRO");
      original.setID ("USER01");
      entry.addMember (original);

      Member update = new Member (dataset, "MEMBRO");
      update.setSize (150);

      assertSame (original, entry.addMember (update));
      assertEquals (1, entry.members.size ());
      assertTrue (original.toString ().contains ("150"), original.toString ());
      assertTrue (original.toString ().contains ("USER01"), original.toString ());
    }

    @Test
    @DisplayName ("putMember substitui sem fundir")
    void putReplacesMember ()
    {
      CacheEntry entry = new CacheEntry (dataset);
      Member original = new Member (dataset, "MEMBRO");
      original.setID ("USER01");
      entry.addMember (original);

      Member replacement = new Member (dataset, "MEMBRO");
      entry.putMember (replacement);

      assertSame (replacement, entry.members.get ("MEMBRO"));
    }

    @Test
    @DisplayName ("putMember tambem cria o mapa quando e o primeiro")
    void putCreatesTheMap ()
    {
      CacheEntry entry = new CacheEntry (dataset);

      entry.putMember (new Member (dataset, "MEMBRO"));

      assertEquals (1, entry.members.size ());
    }

    @Test
    @DisplayName ("replace troca o dataset por outro de mesmo nome")
    void replaceKeepsTheSameName ()
    {
      CacheEntry entry = new CacheEntry (dataset);
      Dataset atualizado = new Dataset (dataset.getName ());

      entry.replace (atualizado);

      assertSame (atualizado, entry.dataset);
    }

    @Test
    @DisplayName ("replace recusa um dataset de outro nome")
    void replaceChecksTheName ()
    {
      // a assercao compara o nome guardado com o do parametro
      CacheEntry entry = new CacheEntry (dataset);
      Dataset outro = new Dataset ("OUTRO.DATASET");

      assertThrows (AssertionError.class, () -> entry.replace (outro));
      assertSame (dataset, entry.dataset);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("requisicoes")
  class Requests
  // ---------------------------------------------------------------------------------//
  {
    private final Initiator initiator = request ->
    {
    };

    @Test
    @DisplayName ("uma requisicao de banco descreve comando e resultado")
    void describesDatabaseRequest ()
    {
      DatabaseRequest request = new DatabaseRequest (initiator, Command.OPEN);
      request.databaseName = "teste.db";
      request.result = Result.SUCCESS;

      String report = request.toString ();

      assertTrue (report.contains ("Database ...... teste.db"), report);
      assertTrue (report.contains ("Command ....... OPEN"), report);
      assertTrue (report.contains ("Result ........ SUCCESS"), report);
      assertTrue (report.contains ("Updated ....... false"), report);
      assertSame (initiator, request.initiator);
    }

    @Test
    @DisplayName ("uma requisicao de dataset por nome")
    void datasetRequestByName ()
    {
      DatasetRequest request =
          new DatasetRequest (initiator, Command.FIND, "MEU.DATASET");

      assertEquals ("MEU.DATASET", request.datasetName);
      assertNull (request.dataset);
      assertTrue (request.toString ().contains ("Dataset ....... MEU.DATASET"));
    }

    @Test
    @DisplayName ("uma requisicao de dataset por objeto tira o nome dele")
    void datasetRequestByObject ()
    {
      Dataset dataset = new Dataset ("MEU.DATASET");

      DatasetRequest request = new DatasetRequest (initiator, Command.ADD, dataset);

      assertEquals ("MEU.DATASET", request.datasetName);
      assertSame (dataset, request.dataset);
    }

    @Test
    @DisplayName ("uma requisicao de membro por objeto tira dataset e nome dele")
    void memberRequestByObject ()
    {
      Dataset dataset = new Dataset ("SYS1.PROCLIB");
      Member member = new Member (dataset, "IEFBR14");

      MemberRequest request = new MemberRequest (initiator, Command.ADD, member);

      assertEquals ("SYS1.PROCLIB", request.datasetName);
      assertEquals ("IEFBR14", request.memberName);
      assertSame (member, request.member);

      String report = request.toString ();

      assertTrue (report.contains ("Dataset ....... SYS1.PROCLIB"), report);
      assertTrue (report.contains ("Member ........ IEFBR14"), report);
    }

    @Test
    @DisplayName ("uma requisicao de membro por nome guarda o dataset separado")
    void memberRequestByName ()
    {
      Dataset dataset = new Dataset ("SYS1.PROCLIB");

      MemberRequest request =
          new MemberRequest (initiator, Command.FIND, dataset, "IEFBR14");

      assertSame (dataset, request.dataset);
      assertNull (request.member);
      assertEquals ("IEFBR14", request.memberName);
    }

    @Test
    @DisplayName ("os dez comandos e os dois resultados estao declarados")
    void enumsAreComplete ()
    {
      assertEquals (10, Command.values ().length);
      assertEquals (2, Result.values ().length);
    }
  }

  // ---------------------------------------------------------------------------------//
  @Nested
  @DisplayName ("DatabaseThread - integracao com o SQLite")
  class Persistence
  // ---------------------------------------------------------------------------------//
  {
    private String originalHome;
    private BlockingQueue<DatabaseRequest> queue;
    private DatabaseThread thread;

    @BeforeEach
    void setUp (@TempDir File directory)
    {
      // o banco e criado em ~/dm3270/databases: o teste redireciona o HOME
      originalHome = System.getProperty ("user.home");
      System.setProperty ("user.home", directory.getAbsolutePath ());

      queue = new LinkedBlockingQueue<> ();
      thread = new DatabaseThread ("teste.db", queue);
      thread.setDaemon (true);
      thread.start ();
    }

    @AfterEach
    void tearDown () throws InterruptedException
    {
      if (thread.isAlive ())
      {
        thread.interrupt ();
        thread.join (2000);
      }
      System.setProperty ("user.home", originalHome);
    }

    // Envia a requisicao e espera o Initiator receber o resultado.
    private DatabaseRequest execute (DatabaseRequest request) throws InterruptedException
    {
      CountDownLatch done = new CountDownLatch (1);
      Waiter waiter = new Waiter (done);
      DatabaseRequest wired = wire (request, waiter);

      queue.put (wired);

      assertTrue (done.await (10, TimeUnit.SECONDS), "o banco nao respondeu");

      return wired;
    }

    // As requisicoes guardam o Initiator num campo final, entao o teste as recria.
    private DatabaseRequest wire (DatabaseRequest request, Initiator initiator)
    {
      if (request instanceof DatasetRequest datasetRequest)
        return datasetRequest.dataset != null
            ? new DatasetRequest (initiator, request.command, datasetRequest.dataset)
            : new DatasetRequest (initiator, request.command,
                                  datasetRequest.datasetName);

      if (request instanceof MemberRequest memberRequest)
        return memberRequest.member != null
            ? new MemberRequest (initiator, request.command, memberRequest.member)
            : new MemberRequest (initiator, request.command, memberRequest.dataset,
                                 memberRequest.memberName);

      return new DatabaseRequest (initiator, request.command);
    }

    private DatabaseRequest open () throws InterruptedException
    {
      return execute (new DatabaseRequest (null, Command.OPEN));
    }

    private Dataset sampleDataset (String name)
    {
      Dataset dataset = new Dataset (name);
      dataset.setLocation ("FUSR01", "3390", "CATALOG.USER.UCAT");
      dataset.setSpace (15, 1, 2, 75);
      dataset.setDisposition ("PO", "FB", 80, 27920);
      dataset.setDates ("2024/01/15", "2030/12/31", "2025/06/01");

      return dataset;
    }

    @Test
    @DisplayName ("OPEN cria as tabelas e informa o nome do banco")
    @Timeout (30)
    void openCreatesTables () throws InterruptedException
    {
      DatabaseRequest request = open ();

      assertEquals (Result.SUCCESS, request.result);
      assertEquals ("teste.db", request.databaseName);
    }

    @Test
    @DisplayName ("OPEN duas vezes nao quebra: as tabelas ja existem")
    @Timeout (30)
    void openIsIdempotent () throws InterruptedException
    {
      open ();

      assertEquals (Result.SUCCESS, open ().result);
    }

    @Test
    @DisplayName ("CREATE derruba e recria as tabelas")
    @Timeout (30)
    void createDropsAndRecreates () throws InterruptedException
    {
      open ();
      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("MEU.DATASET")));

      assertEquals (Result.SUCCESS,
                    execute (new DatabaseRequest (null, Command.CREATE)).result);

      // depois do CREATE o dataset ja nao esta la
      DatasetRequest find =
          (DatasetRequest) execute (new DatasetRequest (null, Command.FIND,
                                                       "MEU.DATASET"));

      assertEquals (Result.FAILURE, find.result);
    }

    @Test
    @DisplayName ("DROP remove as tabelas")
    @Timeout (30)
    void dropRemovesTables () throws InterruptedException
    {
      open ();

      assertEquals (Result.SUCCESS,
                    execute (new DatabaseRequest (null, Command.DROP)).result);
    }

    @Test
    @DisplayName ("ADD grava um dataset novo e recusa o repetido")
    @Timeout (30)
    void addsDatasetOnce () throws InterruptedException
    {
      open ();

      assertEquals (Result.SUCCESS,
                    execute (new DatasetRequest (null, Command.ADD,
                                                 sampleDataset ("MEU.DATASET"))).result);
      assertEquals (Result.FAILURE,
                    execute (new DatasetRequest (null, Command.ADD,
                                                 sampleDataset ("MEU.DATASET"))).result);
    }

    @Test
    @DisplayName ("FIND devolve o dataset gravado com todos os campos")
    @Timeout (30)
    void findsDataset () throws InterruptedException
    {
      open ();
      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("MEU.DATASET")));

      DatasetRequest found =
          (DatasetRequest) execute (new DatasetRequest (null, Command.FIND,
                                                       "MEU.DATASET"));

      assertEquals (Result.SUCCESS, found.result);
      assertNotNull (found.dataset);
      assertEquals ("MEU.DATASET", found.dataset.getName ());
      assertTrue (found.dataset.isPartitioned ());
      assertTrue (found.dataset.toString ().contains ("FUSR01"),
                  found.dataset.toString ());
    }

    @Test
    @DisplayName ("FIND de um dataset inexistente falha")
    @Timeout (30)
    void findMissingDataset () throws InterruptedException
    {
      open ();

      assertEquals (Result.FAILURE,
                    execute (new DatasetRequest (null, Command.FIND, "NAO.EXISTE"))
                        .result);
    }

    @Test
    @DisplayName ("UPDATE grava o dataset que ainda nao existe")
    @Timeout (30)
    void updateInsertsWhenMissing () throws InterruptedException
    {
      open ();

      assertEquals (Result.SUCCESS,
                    execute (new DatasetRequest (null, Command.UPDATE,
                                                 sampleDataset ("NOVO.DATASET"))).result);
      assertEquals (Result.SUCCESS,
                    execute (new DatasetRequest (null, Command.FIND, "NOVO.DATASET"))
                        .result);
    }

    @Test
    @DisplayName ("MODIFY exige que o dataset exista")
    @Timeout (30)
    void modifyNeedsAnExistingDataset () throws InterruptedException
    {
      open ();

      assertEquals (Result.FAILURE,
                    execute (new DatasetRequest (null, Command.MODIFY,
                                                 sampleDataset ("NAO.EXISTE"))).result);

      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("MEU.DATASET")));
      Dataset changed = sampleDataset ("MEU.DATASET");
      changed.setSpace (99, 9, 9, 99);

      assertEquals (Result.SUCCESS,
                    execute (new DatasetRequest (null, Command.MODIFY, changed)).result);
    }

    @Test
    @DisplayName ("DELETE remove o dataset e depois falha")
    @Timeout (30)
    void deletesDataset () throws InterruptedException
    {
      open ();
      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("MEU.DATASET")));

      assertEquals (Result.SUCCESS,
                    execute (new DatasetRequest (null, Command.DELETE, "MEU.DATASET"))
                        .result);
      assertEquals (Result.FAILURE,
                    execute (new DatasetRequest (null, Command.DELETE, "MEU.DATASET"))
                        .result);
    }

    @Test
    @DisplayName ("LIST com curinga na primeira posicao devolve tudo")
    @Timeout (30)
    void listsAllDatasets () throws InterruptedException
    {
      open ();
      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("UM.DATASET")));
      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("DOIS.DATASET")));

      DatasetRequest list =
          (DatasetRequest) execute (new DatasetRequest (null, Command.LIST, "*"));

      assertEquals (Result.SUCCESS, list.result);
      assertNotNull (list.datasets);
      assertEquals (2, list.datasets.size ());
    }

    @Test
    @DisplayName ("LIST com prefixo antes do curinga filtra pela faixa de nomes")
    @Timeout (30)
    void listsByPrefix () throws InterruptedException
    {
      open ();
      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("USER01.DADOS")));
      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("USER01.FONTES")));
      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("SYS1.PARMLIB")));

      DatasetRequest list =
          (DatasetRequest) execute (new DatasetRequest (null, Command.LIST, "USER01*"));

      assertEquals (2, list.datasets.size ());
    }

    @Test
    @DisplayName ("um filtro vazio lista tudo, um nome sem curinga procura o exato")
    @Timeout (30)
    void emptyFilterListsEverything () throws InterruptedException
    {
      // convencao do filtro: vazio ou "*" lista tudo, "PREFIXO*" lista a faixa e um
      // nome sem curinga procura aquele dataset
      open ();
      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("UM.DATASET")));
      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("DOIS.DATASET")));

      DatasetRequest empty =
          (DatasetRequest) execute (new DatasetRequest (null, Command.LIST, ""));
      DatasetRequest exact =
          (DatasetRequest) execute (new DatasetRequest (null, Command.LIST,
                                                       "UM.DATASET"));
      DatasetRequest missing =
          (DatasetRequest) execute (new DatasetRequest (null, Command.LIST,
                                                       "NAO.EXISTE"));

      assertEquals (2, empty.datasets.size ());
      assertEquals (1, exact.datasets.size ());
      assertEquals (0, missing.datasets.size ());
    }

    @Test
    @DisplayName ("ADD grava um membro de um dataset existente")
    @Timeout (30)
    void addsMember () throws InterruptedException
    {
      open ();
      Dataset dataset = sampleDataset ("SYS1.PROCLIB");
      execute (new DatasetRequest (null, Command.ADD, dataset));

      Member member = new Member (dataset, "IEFBR14");
      member.setSize (120, 100, 20, 1, 5);
      member.setID ("USER01");
      member.setDates ("2024/01/15", "2025/06/01 14:30:00");

      assertEquals (Result.SUCCESS,
                    execute (new MemberRequest (null, Command.ADD, member)).result);
      assertEquals (Result.FAILURE,
                    execute (new MemberRequest (null, Command.ADD, member)).result);
    }

    @Test
    @DisplayName ("FIND devolve o membro gravado")
    @Timeout (30)
    void findsMember () throws InterruptedException
    {
      open ();
      Dataset dataset = sampleDataset ("SYS1.PROCLIB");
      execute (new DatasetRequest (null, Command.ADD, dataset));

      Member member = new Member (dataset, "IEFBR14");
      member.setSize (120, 100, 20, 1, 5);
      member.setID ("USER01");
      execute (new MemberRequest (null, Command.ADD, member));

      MemberRequest found =
          (MemberRequest) execute (new MemberRequest (null, Command.FIND, member));

      assertEquals (Result.SUCCESS, found.result);
      assertNotNull (found.member);
      assertEquals ("IEFBR14", found.member.getName ());
    }

    @Test
    @DisplayName ("UPDATE de membro insere quando ainda nao existe")
    @Timeout (30)
    void updateInsertsMember () throws InterruptedException
    {
      open ();
      Dataset dataset = sampleDataset ("SYS1.PROCLIB");
      execute (new DatasetRequest (null, Command.ADD, dataset));

      Member member = new Member (dataset, "NOVO");
      member.setSize (10);

      assertEquals (Result.SUCCESS,
                    execute (new MemberRequest (null, Command.UPDATE, member)).result);

      member.setSize (20);

      assertEquals (Result.SUCCESS,
                    execute (new MemberRequest (null, Command.UPDATE, member)).result);
    }

    @Test
    @DisplayName ("MODIFY de membro exige que ele exista")
    @Timeout (30)
    void modifyNeedsAnExistingMember () throws InterruptedException
    {
      open ();
      Dataset dataset = sampleDataset ("SYS1.PROCLIB");
      execute (new DatasetRequest (null, Command.ADD, dataset));

      Member member = new Member (dataset, "NAO.EXISTE");

      assertEquals (Result.FAILURE,
                    execute (new MemberRequest (null, Command.MODIFY, member)).result);
    }

    @Test
    @DisplayName ("DELETE remove o membro e depois falha")
    @Timeout (30)
    void deletesMember () throws InterruptedException
    {
      open ();
      Dataset dataset = sampleDataset ("SYS1.PROCLIB");
      execute (new DatasetRequest (null, Command.ADD, dataset));

      Member member = new Member (dataset, "IEFBR14");
      member.setSize (10);
      execute (new MemberRequest (null, Command.ADD, member));

      assertEquals (Result.SUCCESS,
                    execute (new MemberRequest (null, Command.DELETE, member)).result);

      // o membro saiu do banco, entao um segundo DELETE nao encontra nada
      assertEquals (Result.FAILURE,
                    execute (new MemberRequest (null, Command.DELETE, member)).result);
    }

    @Test
    @DisplayName ("um membro apagado nao e mais encontrado")
    @Timeout (30)
    void deletedMemberIsGone () throws InterruptedException
    {
      open ();
      Dataset dataset = sampleDataset ("SYS1.PROCLIB");
      execute (new DatasetRequest (null, Command.ADD, dataset));

      Member member = new Member (dataset, "IEFBR14");
      member.setSize (10);
      execute (new MemberRequest (null, Command.ADD, member));
      execute (new MemberRequest (null, Command.DELETE, member));

      MemberRequest list =
          (MemberRequest) execute (new MemberRequest (null, Command.LIST, dataset, ""));

      assertEquals (0, list.members.size ());
    }

    @Test
    @DisplayName ("LIST devolve os membros de um dataset")
    @Timeout (30)
    void listsMembers () throws InterruptedException
    {
      open ();
      Dataset dataset = sampleDataset ("SYS1.PROCLIB");
      execute (new DatasetRequest (null, Command.ADD, dataset));

      for (String name : new String[] { "MEMBRO1", "MEMBRO2", "MEMBRO3" })
      {
        Member member = new Member (dataset, name);
        member.setSize (10);
        execute (new MemberRequest (null, Command.ADD, member));
      }

      MemberRequest list =
          (MemberRequest) execute (new MemberRequest (null, Command.LIST, dataset, ""));

      assertEquals (Result.SUCCESS, list.result);
      assertNotNull (list.members);
      assertEquals (3, list.members.size ());
    }

    @Test
    @DisplayName ("um FIND de membro nao monta a lista do dataset")
    @Timeout (30)
    void findMemberDoesNotList () throws InterruptedException
    {
      // o case FIND de process (MemberRequest) tem break: nao cai no case LIST
      open ();
      Dataset dataset = sampleDataset ("SYS1.PROCLIB");
      execute (new DatasetRequest (null, Command.ADD, dataset));

      Member member = new Member (dataset, "IEFBR14");
      member.setSize (10);
      execute (new MemberRequest (null, Command.ADD, member));

      MemberRequest found =
          (MemberRequest) execute (new MemberRequest (null, Command.FIND, member));

      assertEquals (Result.SUCCESS, found.result);
      assertNotNull (found.member);
      assertNull (found.members, "o FIND nao deveria montar a lista");
    }

    @Test
    @DisplayName ("CLOSE encerra a thread e fecha a conexao")
    @Timeout (30)
    void closeEndsTheThread () throws InterruptedException
    {
      open ();

      assertEquals (Result.SUCCESS,
                    execute (new DatabaseRequest (null, Command.CLOSE)).result);

      thread.join (5000);

      assertFalse (thread.isAlive ());
    }

    @Test
    @DisplayName ("um comando sem tratamento devolve falha")
    @Timeout (30)
    void unknownCommandFails () throws InterruptedException
    {
      open ();

      assertEquals (Result.FAILURE,
                    execute (new DatabaseRequest (null, Command.FIND)).result);
    }

    // -------------------------------------------------------------------------------//
    // O mapeamento coluna a coluna
    // -------------------------------------------------------------------------------//

    /*
     * Comparar so o objeto que volta de um FIND nao prova o mapeamento. Se duas colunas do
     * mesmo tipo trocassem de lugar na gravacao E na leitura, as duas trocas se
     * compensariam, o round-trip fecharia certinho e o banco estaria errado. Os dois testes
     * abaixo abrem o arquivo SQLite e leem as colunas PELO NOME, o que fixa a ordem dos
     * quinze parametros de INSERT_DATASET e dos dez de INSERT_MEMBER.
     *
     * Existem porque a onda 3 tira o DatabaseThread de cima dos campos package-private de
     * Dataset e Member e move o binding para um mapper. Um indice trocado nessa mudanca
     * passaria despercebido pela suite que havia antes: findsDataset conferia tres campos
     * dos quinze, e findsMember conferia so o nome.
     */
    // -------------------------------------------------------------------------------//
    private Connection openDatabaseFile () throws SQLException
    // -------------------------------------------------------------------------------//
    {
      String path = Paths
          .get (System.getProperty ("user.home"), "dm3270", "databases", "teste.db")
          .toString ();

      return DriverManager.getConnection ("jdbc:sqlite:" + path);
    }

    @Test
    @DisplayName ("cada uma das quinze colunas de DATASETS recebe o seu proprio valor")
    @Timeout (30)
    void datasetColumnsAreBoundInOrder () throws InterruptedException, SQLException
    {
      open ();
      execute (new DatasetRequest (null, Command.ADD, sampleDataset ("MEU.DATASET")));

      try (Connection connection = openDatabaseFile ();
          Statement statement = connection.createStatement ();
          ResultSet rs = statement
              .executeQuery ("select * from DATASETS where NAME='MEU.DATASET'"))
      {
        assertTrue (rs.next (), "o dataset nao chegou ao arquivo do banco");

        assertEquals ("MEU.DATASET", rs.getString ("NAME"));
        assertEquals ("FUSR01", rs.getString ("VOLUME"));
        assertEquals ("3390", rs.getString ("DEVICE"));
        assertEquals ("CATALOG.USER.UCAT", rs.getString ("CATALOG"));

        // os quatro inteiros de espaco sao distintos de proposito: qualquer troca entre
        // eles muda o valor lido, e nenhuma passa despercebida
        assertEquals (15, rs.getInt ("TRACKS"));
        assertEquals (1, rs.getInt ("CYLINDERS"));
        assertEquals (75, rs.getInt ("PERCENT"));
        assertEquals (2, rs.getInt ("EXTENTS"));

        assertEquals ("PO", rs.getString ("DSORG"));
        assertEquals ("FB", rs.getString ("RECFM"));
        assertEquals (80, rs.getInt ("LRECL"));
        assertEquals (27920, rs.getInt ("BLKSIZE"));

        // 2024/01/15 (created) < 2025/06/01 (referred) < 2030/12/31 (expires). A ordem
        // separa as tres colunas de data sem depender de como o driver converte a data
        // para o fuso local, que e o unico detalhe que muda de maquina para maquina
        assertNotNull (rs.getDate ("CREATED"));
        assertTrue (rs.getDate ("CREATED").before (rs.getDate ("REFERRED")),
                    "CREATED deveria ser a mais antiga das tres");
        assertTrue (rs.getDate ("REFERRED").before (rs.getDate ("EXPIRES")),
                    "EXPIRES deveria ser a mais recente das tres");
      }
    }

    @Test
    @DisplayName ("cada uma das dez colunas de MEMBERS recebe o seu proprio valor")
    @Timeout (30)
    void memberColumnsAreBoundInOrder () throws InterruptedException, SQLException
    {
      open ();
      Dataset dataset = sampleDataset ("SYS1.PROCLIB");
      execute (new DatasetRequest (null, Command.ADD, dataset));

      Member member = new Member (dataset, "IEFBR14");
      member.setSize (120, 100, 20, 1, 5);
      member.setID ("USER01");
      member.setDates ("2024/01/15", "2025/06/01 14:30:00");
      execute (new MemberRequest (null, Command.ADD, member));

      try (Connection connection = openDatabaseFile ();
          Statement statement = connection.createStatement ();
          ResultSet rs = statement.executeQuery (
              "select * from MEMBERS where DATASET='SYS1.PROCLIB' and NAME='IEFBR14'"))
      {
        assertTrue (rs.next (), "o membro nao chegou ao arquivo do banco");

        assertEquals ("SYS1.PROCLIB", rs.getString ("DATASET"));
        assertEquals ("IEFBR14", rs.getString ("NAME"));
        assertEquals ("USER01", rs.getString ("ID"));

        // 120, 100, 20, 1 e 5 sao distintos de proposito, pelo mesmo motivo
        assertEquals (120, rs.getInt ("SIZE"));
        assertEquals (100, rs.getInt ("INIT"));
        assertEquals (20, rs.getInt ("MOD"));
        assertEquals (1, rs.getInt ("VV"));
        assertEquals (5, rs.getInt ("MM"));

        assertNotNull (rs.getDate ("CREATED"));
        assertTrue (rs.getDate ("CREATED").before (rs.getDate ("CHANGED")),
                    "CREATED deveria ser anterior a CHANGED");
      }
    }

    @Test
    @DisplayName ("o dataset volta do FIND com as quinze colunas intactas")
    @Timeout (30)
    void datasetRoundTripsEveryColumn () throws InterruptedException
    {
      open ();
      Dataset original = sampleDataset ("MEU.DATASET");
      execute (new DatasetRequest (null, Command.ADD, original));

      DatasetRequest found = (DatasetRequest) execute (
          new DatasetRequest (null, Command.FIND, "MEU.DATASET"));

      // toString imprime as quinze colunas de uma vez, entao comparar as duas
      // representacoes cobre a ida e a volta inteiras
      assertEquals (original.toString (), found.dataset.toString ());
    }

    @Test
    @DisplayName ("um dataset sem datas volta sem datas, e nao com o dia de hoje")
    @Timeout (30)
    void datasetWithoutDatesRoundTrips () throws InterruptedException
    {
      open ();
      Dataset original = new Dataset ("SEM.DATAS");
      original.setLocation ("FUSR02", "3380", "CATALOG.OUTRO");
      original.setSpace (3, 0, 1, 10);
      original.setDisposition ("PS", "VB", 133, 6233);
      execute (new DatasetRequest (null, Command.ADD, original));

      DatasetRequest found = (DatasetRequest) execute (
          new DatasetRequest (null, Command.FIND, "SEM.DATAS"));

      assertEquals (original.toString (), found.dataset.toString ());
    }

    @Test
    @DisplayName ("gravar um membro marca como particionado o dataset que ja existia")
    @Timeout (30)
    void insertingMemberMarksExistingDatasetPartitioned ()
        throws InterruptedException, SQLException
    {
      open ();
      Dataset dataset = new Dataset ("SEM.DSORG");
      dataset.setSpace (1, 0, 1, 5);
      execute (new DatasetRequest (null, Command.ADD, dataset));

      execute (new MemberRequest (null, Command.ADD, new Member (dataset, "UM")));

      try (Connection connection = openDatabaseFile ();
          Statement statement = connection.createStatement ();
          ResultSet rs = statement
              .executeQuery ("select DSORG from DATASETS where NAME='SEM.DSORG'"))
      {
        assertTrue (rs.next ());
        assertEquals ("PO", rs.getString ("DSORG"));
      }
    }

    @Test
    @DisplayName ("gravar um membro cria como particionado o dataset que faltava")
    @Timeout (30)
    void insertingMemberCreatesMissingDatasetPartitioned ()
        throws InterruptedException, SQLException
    {
      open ();

      Member member = new Member (new Dataset ("NUNCA.VISTO"), "UM");
      assertEquals (Result.SUCCESS,
                    execute (new MemberRequest (null, Command.ADD, member)).result);

      try (Connection connection = openDatabaseFile ();
          Statement statement = connection.createStatement ();
          ResultSet rs = statement
              .executeQuery ("select DSORG from DATASETS where NAME='NUNCA.VISTO'"))
      {
        assertTrue (rs.next (), "o dataset deveria ter sido criado junto com o membro");
        assertEquals ("PO", rs.getString ("DSORG"));
      }
    }
  }

  // ---------------------------------------------------------------------------------//
  private static class Waiter implements Initiator
  // ---------------------------------------------------------------------------------//
  {
    private final CountDownLatch done;

    Waiter (CountDownLatch done)
    {
      this.done = done;
    }

    @Override
    public void processResult (DatabaseRequest request)
    {
      done.countDown ();
    }
  }
}
